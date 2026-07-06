// 메인 지도 화면

const LEGEND_COLORS = ["#f7efea", "#edcbbc", "#db9880", "#c0664e", "#a8412c", "#7e2a1b"];
// LL 다이나믹이 진한 쪽, HH 정체가 옅은 쪽
const CHANGE_COLORS = { LL: "#7e2a1b", LH: "#c0664e", HL: "#db9880", HH: "#edcbbc" };

// 지도 레이어 정의. 임대료는 상권 단위 원천이 없어 레이어에서 제외.
const LAYERS = {
    sales: { name: "추정 매출", unit: "억/분기", value: (d) => d.salesAmt },
    flpop: { name: "유동인구", unit: "만 명", value: (d) => d.flpop },
    store: { name: "점포 수", unit: "개", value: (d) => d.storeCnt },
    change: { name: "성장·쇠퇴", unit: "변화지표", value: (d) => d.changeIx },
};

const state = { districts: [], dots: new Map(), layer: "sales", selected: null, polygon: null };

function loadKakaoSdk(appKey) {
    return new Promise((resolve, reject) => {
        const s = document.createElement("script");
        s.src = "https://dapi.kakao.com/v2/maps/sdk.js?autoload=false&appkey=" + appKey;
        s.onload = () => kakao.maps.load(resolve);
        s.onerror = () => reject(new Error("카카오 지도 SDK 로드 실패"));
        document.head.appendChild(s);
    });
}

// 편차가 큰 값이라 순위 기반 6단계 색
function makeColorScale(values) {
    const sorted = values.filter((v) => v != null).sort((a, b) => a - b);
    return (v) => {
        if (v == null || !sorted.length) {
            return LEGEND_COLORS[0];
        }
        let lo = 0;
        let hi = sorted.length - 1;
        while (lo < hi) {
            const mid = (lo + hi) >> 1;
            if (sorted[mid] < v) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return LEGEND_COLORS[Math.min(5, Math.floor((lo / sorted.length) * 6))];
    };
}

function dotColorFn() {
    const layer = LAYERS[state.layer];
    if (state.layer === "change") {
        return (d) => CHANGE_COLORS[d.changeIx] || "#d9cfc9";
    }
    const scale = makeColorScale(state.districts.map(layer.value));
    return (d) => scale(layer.value(d));
}

function recolorDots() {
    const colorOf = dotColorFn();
    state.districts.forEach((d) => {
        const el = state.dots.get(d.trdarCd);
        if (el) {
            el.style.background = colorOf(d);
        }
    });
}

function drawDots(map) {
    const colorOf = dotColorFn();
    state.districts.forEach((d) => {
        if (d.centerLat == null || d.centerLot == null) {
            return;
        }
        const el = document.createElement("div");
        el.title = d.trdarNm;
        el.style.cssText = "width:11px;height:11px;border-radius:50%;border:1.5px solid #fff;"
            + "cursor:pointer;background:" + colorOf(d);
        el.addEventListener("click", () => select(d, map));
        new kakao.maps.CustomOverlay({
            map: map,
            position: new kakao.maps.LatLng(d.centerLat, d.centerLot),
            content: el,
            zIndex: 1,
        });
        state.dots.set(d.trdarCd, el);
    });
}

// 선택 상권 강조 + 경계 + 우측 카드
async function select(d, map) {
    if (state.selected) {
        const prev = state.dots.get(state.selected.trdarCd);
        if (prev) {
            prev.style.width = "11px";
            prev.style.height = "11px";
        }
    }
    state.selected = d;
    const el = state.dots.get(d.trdarCd);
    if (el) {
        el.style.width = "17px";
        el.style.height = "17px";
    }

    const card = document.querySelector(".sel-card");
    card.style.display = "";
    document.querySelector(".sel-name").textContent = d.trdarNm;
    document.querySelector(".sel-hero-num").textContent = fmtEok(d.salesAmt);
    document.querySelector(".sel-hero-unit").textContent = "억/분기";
    const dds = document.querySelectorAll(".sel-metrics .sel-metric dd");
    dds[0].innerHTML = fmtEok(d.salesAmt) + "<span>억/분기</span>";
    dds[1].innerHTML = fmtMan(d.flpop) + "<span>만 명</span>";
    dds[2].innerHTML = (d.storeCnt != null ? d.storeCnt.toLocaleString() : "-") + "<span>개</span>";
    dds[3].innerHTML = (d.changeIxNm || "-") + "<span></span>";
    document.querySelector(".sel-report").href = "/map/trdar-detail.html?trdarCd=" + d.trdarCd;

    const delta = document.querySelector(".sel-hero-delta");
    delta.textContent = "";

    // 증감과 경계는 병렬 조회. 응답 전에 다른 점을 고르면 버린다.
    const [salesRes, geoRes] = await Promise.allSettled([
        apiData("/api/sales?trdarCd=" + d.trdarCd),
        apiData("/api/districts/geo?trdarCd=" + d.trdarCd),
    ]);
    if (state.selected !== d) {
        return;
    }

    if (salesRes.status === "fulfilled") {
        const totals = quarterlyTotals(salesRes.value);
        if (totals.length >= 2 && totals[totals.length - 2].amt) {
            const cur = totals[totals.length - 1].amt;
            const prev = totals[totals.length - 2].amt;
            const pct = ((cur - prev) / prev) * 100;
            delta.textContent = "전분기 " + (pct >= 0 ? "+" : "") + pct.toFixed(1) + "%";
        }
    }

    if (geoRes.status === "fulfilled" && geoRes.value.length && geoRes.value[0].geoJson) {
        const geo = JSON.parse(geoRes.value[0].geoJson);
        const rings = geo.type === "MultiPolygon" ? geo.coordinates.map((p) => p[0]) : [geo.coordinates[0]];
        const path = rings.map((ring) => ring.map(([lng, lat]) => new kakao.maps.LatLng(lat, lng)));
        if (state.polygon) {
            state.polygon.setMap(null);
        }
        state.polygon = new kakao.maps.Polygon({
            map: map, path: path,
            strokeWeight: 2.5, strokeColor: "#7e2a1b", strokeOpacity: 0.9,
            fillColor: "#a8412c", fillOpacity: 0.18,
        });
    }
}

function clearSelection() {
    if (state.selected) {
        const el = state.dots.get(state.selected.trdarCd);
        if (el) {
            el.style.width = "11px";
            el.style.height = "11px";
        }
    }
    state.selected = null;
    if (state.polygon) {
        state.polygon.setMap(null);
        state.polygon = null;
    }
    document.querySelector(".sel-card").style.display = "none";
}

// 자치구 랭킹: 최근 분기 매출 합 기준 상위 12
function drawRanking(map) {
    const byGu = new Map();
    state.districts.forEach((d) => {
        if (!d.signguNm) {
            return;
        }
        const g = byGu.get(d.signguNm) || { amt: 0, lat: 0, lot: 0, n: 0 };
        g.amt += d.salesAmt || 0;
        if (d.centerLat != null) {
            g.lat += +d.centerLat;
            g.lot += +d.centerLot;
            g.n += 1;
        }
        byGu.set(d.signguNm, g);
    });
    const top = [...byGu.entries()].sort((a, b) => b[1].amt - a[1].amt).slice(0, 12);
    const maxAmt = top.length ? top[0][1].amt : 0;

    const list = document.querySelector(".rank-list");
    list.innerHTML = "";
    top.forEach(([gu, g], i) => {
        const li = document.createElement("li");
        li.className = "rank-row";
        li.style.cursor = "pointer";
        const width = maxAmt ? Math.round((g.amt / maxAmt) * 100) : 0;
        li.innerHTML =
            '<span class="rank-num' + (i === 0 ? " is-top" : "") + '">' + (i + 1) + "</span>" +
            '<div class="rank-main"><span class="rank-name' + (i === 0 ? " is-lead" : "") + '">' + gu + "</span>" +
            '<span class="minibar"><span style="width:' + width + '%;background:#a8412c"></span></span></div>' +
            '<div class="rank-val"><b>' + fmtEok(g.amt) + "</b><span>억/분기</span></div>";
        li.addEventListener("click", () => {
            if (g.n) {
                map.setCenter(new kakao.maps.LatLng(g.lat / g.n, g.lot / g.n));
                map.setLevel(6);
            }
            list.querySelectorAll(".rank-row").forEach((r) => r.classList.remove("is-selected"));
            li.classList.add("is-selected");
        });
        list.appendChild(li);
    });
}

// 레이어 목록 클릭 -> 점 색·범례 갱신
function bindLayers() {
    document.querySelectorAll(".layer-item").forEach((li) => {
        li.style.cursor = "pointer";
        li.addEventListener("click", () => {
            const key = li.dataset.layer;
            if (!key || !LAYERS[key]) {
                return;
            }
            state.layer = key;
            document.querySelectorAll(".layer-item").forEach((x) => x.classList.toggle("is-active", x === li));
            document.querySelector(".legend-title").textContent = LAYERS[key].name;
            document.querySelector(".legend-unit").textContent = LAYERS[key].unit;
            const ends = document.querySelector(".legend-ends");
            ends.innerHTML = key === "change"
                ? "<span>정체</span><span>다이나믹</span>"
                : "<span>낮음</span><span>높음</span>";
            recolorDots();
        });
    });
}

async function init() {
    const cfg = await apiData("/api/map/config");
    await loadKakaoSdk(cfg.kakaoJsKey);

    const mapDiv = document.getElementById("map");
    mapDiv.innerHTML = "";
    mapDiv.removeAttribute("aria-hidden");
    const map = new kakao.maps.Map(mapDiv, {
        center: new kakao.maps.LatLng(37.5665, 126.978),
        level: 8,
    });

    state.districts = (await apiData("/api/districts/summary")) || [];

    // 상단 배지·분기 표시를 실제 값으로
    const gus = new Set(state.districts.map((d) => d.signguNm).filter(Boolean));
    document.getElementById("sum-gu").textContent = gus.size;
    document.getElementById("sum-trdar").textContent = state.districts.length.toLocaleString();
    if (state.districts.length) {
        document.querySelector(".app-quarter").childNodes[0].textContent = quarterLabel(state.districts[0].quarter);
    }
    document.querySelector(".rank-foot").firstChild.textContent =
        "전체 " + state.districts.length.toLocaleString() + "개 상권";

    drawDots(map);
    drawRanking(map);
    bindLayers();

    document.querySelector(".sel-close").addEventListener("click", clearSelection);
    document.querySelector(".sel-add").addEventListener("click", (e) => {
        e.preventDefault();
        if (state.selected) {
            cmpAdd(state.selected.trdarCd);
            location.href = "/map/compare.html";
        }
    });
    clearSelection(); // 첫 화면은 선택 없음
    document.getElementById("app-status").textContent = "연동 정상";
}

init().catch((err) => {
    console.error("지도 초기화 실패:", err);
    document.getElementById("app-status").textContent = "연동 오류";
    document.getElementById("map").innerHTML =
        '<p style="padding:40px;text-align:center;color:#8c7f78">지도를 불러오지 못했습니다. 서버 연결을 확인해 주세요.</p>';
});
