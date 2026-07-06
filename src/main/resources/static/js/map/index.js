// 메인 지도. 구 단위 단계구분도에서 시작해 구 클릭 시 상권 폴리곤으로 내려간다.

const LEGEND_COLORS = ["#f7efea", "#edcbbc", "#db9880", "#c0664e", "#a8412c", "#7e2a1b"];
// LL 다이나믹이 진한 쪽, HH 정체가 옅은 쪽
const CHANGE_COLORS = { LL: "#7e2a1b", LH: "#c0664e", HL: "#db9880", HH: "#edcbbc" };
const CHANGE_SCORE = { LL: 3, LH: 2, HL: 1, HH: 0 };

// 임대료는 상권 단위 원천이 없어 레이어 대신 드로어 지표(서울 소규모상가 기준)로 보여준다.
const LAYERS = {
    sales: { name: "추정 매출", unit: "억/분기", value: (d) => d.salesAmt },
    flpop: { name: "유동인구", unit: "만 명", value: (d) => d.flpop },
    store: { name: "점포 수", unit: "개", value: (d) => d.storeCnt },
    change: { name: "성장·쇠퇴", unit: "변화지표", value: (d) => d.changeIx },
};

const state = {
    map: null,
    districts: [],          // 상권 요약 전체
    byGu: new Map(),        // 구 이름 -> 집계
    guPolys: new Map(),     // 구 이름 -> kakao.maps.Polygon[]
    guBounds: new Map(),    // 구 이름 -> LatLngBounds
    trdarPolys: new Map(),  // 상권 코드 -> kakao.maps.Polygon[]
    level: "gu",
    currentGu: null,
    selected: null,
    layer: "sales",
    seoulRent: null,
    drill: null,            // 드릴다운 요청 취소용 AbortController
};

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

function ringsOf(geo) {
    return geo.type === "MultiPolygon" ? geo.coordinates.map((p) => p[0]) : [geo.coordinates[0]];
}

function toPaths(geo) {
    return ringsOf(geo).map((ring) => ring.map(([lng, lat]) => new kakao.maps.LatLng(lat, lng)));
}

function boundsOf(paths) {
    const b = new kakao.maps.LatLngBounds();
    paths.forEach((p) => p.forEach((pt) => b.extend(pt)));
    return b;
}

// ----- 구 단위 집계와 색 -----

function aggregateByGu() {
    state.byGu.clear();
    state.districts.forEach((d) => {
        if (!d.signguNm) {
            return;
        }
        const g = state.byGu.get(d.signguNm) ||
            { gu: d.signguNm, salesAmt: 0, flpop: 0, storeCnt: 0, changeSum: 0, changeN: 0, count: 0 };
        g.salesAmt += d.salesAmt || 0;
        g.flpop += d.flpop || 0;
        g.storeCnt += d.storeCnt || 0;
        if (d.changeIx in CHANGE_SCORE) {
            g.changeSum += CHANGE_SCORE[d.changeIx];
            g.changeN += 1;
        }
        g.count += 1;
        state.byGu.set(d.signguNm, g);
    });
}

// 구의 대표 변화지표 = 소속 상권 점수 평균을 가장 가까운 단계로
function guChangeIx(g) {
    if (!g.changeN) {
        return null;
    }
    const avg = g.changeSum / g.changeN;
    return ["HH", "HL", "LH", "LL"][Math.min(3, Math.round(avg))];
}

function guValue(g) {
    if (state.layer === "change") {
        return guChangeIx(g);
    }
    return LAYERS[state.layer].value(g);
}

function paintGu() {
    if (state.layer === "change") {
        state.byGu.forEach((g, gu) => {
            const c = CHANGE_COLORS[guChangeIx(g)] || "#e7e0da";
            (state.guPolys.get(gu) || []).forEach((p) => p.setOptions({ fillColor: c }));
        });
        return;
    }
    const scale = makeColorScale([...state.byGu.values()].map(guValue));
    state.byGu.forEach((g, gu) => {
        const c = scale(guValue(g));
        (state.guPolys.get(gu) || []).forEach((p) => p.setOptions({ fillColor: c }));
    });
}

function paintTrdar() {
    const mine = state.districts.filter((d) => d.signguNm === state.currentGu);
    const colorOf = state.layer === "change"
        ? (d) => CHANGE_COLORS[d.changeIx] || "#e7e0da"
        : (() => {
            const scale = makeColorScale(mine.map(LAYERS[state.layer].value));
            return (d) => scale(LAYERS[state.layer].value(d));
        })();
    mine.forEach((d) => {
        (state.trdarPolys.get(d.trdarCd) || []).forEach((p) => p.setOptions({ fillColor: colorOf(d) }));
    });
}

// ----- 폴리곤 그리기 -----

function drawGuPolygons(features) {
    features.forEach((f) => {
        const gu = f.properties.name;
        const paths = toPaths(f.geometry);
        state.guBounds.set(gu, boundsOf(paths));
        const poly = new kakao.maps.Polygon({
            map: state.map, path: paths,
            strokeWeight: 1.5, strokeColor: "#7e2a1b", strokeOpacity: 0.55,
            fillColor: "#e7e0da", fillOpacity: 0.72,
        });
        kakao.maps.event.addListener(poly, "mouseover", () => {
            if (state.level === "gu") {
                poly.setOptions({ fillOpacity: 0.9, strokeWeight: 2.5 });
            }
        });
        kakao.maps.event.addListener(poly, "mouseout", () => {
            poly.setOptions({ fillOpacity: state.level === "gu" ? 0.72 : 0.12, strokeWeight: 1.5 });
        });
        kakao.maps.event.addListener(poly, "click", () => {
            if (state.level === "gu") {
                drillDown(gu, boundsOf(paths));
            }
        });
        const arr = state.guPolys.get(gu) || [];
        arr.push(poly);
        state.guPolys.set(gu, arr);
    });
    paintGu();
}

function clearTrdarPolygons() {
    state.trdarPolys.forEach((polys) => polys.forEach((p) => p.setMap(null)));
    state.trdarPolys.clear();
}

// ----- 드릴다운 -----

async function drillDown(gu, bounds) {
    if (state.drill) {
        state.drill.abort();
    }
    state.drill = new AbortController();

    state.level = "trdar";
    state.currentGu = gu;
    state.selected = null;
    // 스타일시트의 display:none을 이기려면 명시 값이 필요하다
    document.getElementById("map-back").style.display = "inline-block";
    if (bounds) {
        state.map.setBounds(bounds, 24);
    }
    // 구 폴리곤은 흐리게 남겨 맥락만 유지
    state.guPolys.forEach((polys) => polys.forEach((p) => p.setOptions({ fillOpacity: 0.12 })));

    const g = state.byGu.get(gu);
    if (g) {
        openDrawerForGu(g);
    }

    clearTrdarPolygons();
    if (state.codesReady) {
        await state.codesReady;
    }
    const code = GU_CODE_CACHE.get(gu);
    if (!code) {
        return;
    }
    let geos;
    try {
        geos = await apiData("/api/districts/geo?signguCd=" + code, state.drill.signal);
    } catch (e) {
        return; // 취소되었거나 실패. 화면은 구 상태 유지
    }
    if (state.currentGu !== gu) {
        return;
    }
    const byId = new Map(state.districts.map((d) => [d.trdarCd, d]));
    geos.forEach((gjson) => {
        if (!gjson.geoJson) {
            return;
        }
        const d = byId.get(gjson.trdarCd);
        const paths = toPaths(JSON.parse(gjson.geoJson));
        const poly = new kakao.maps.Polygon({
            map: state.map, path: paths,
            strokeWeight: 1.5, strokeColor: "#5e1e11", strokeOpacity: 0.75,
            fillColor: "#e7e0da", fillOpacity: 0.78,
        });
        kakao.maps.event.addListener(poly, "mouseover", () => poly.setOptions({ fillOpacity: 0.95, strokeWeight: 2.5 }));
        kakao.maps.event.addListener(poly, "mouseout", () => {
            const sel = state.selected && state.selected.trdarCd === gjson.trdarCd;
            poly.setOptions({ fillOpacity: sel ? 0.95 : 0.78, strokeWeight: sel ? 3 : 1.5 });
        });
        kakao.maps.event.addListener(poly, "click", () => {
            if (d) {
                selectTrdar(d);
            }
        });
        const arr = state.trdarPolys.get(gjson.trdarCd) || [];
        arr.push(poly);
        state.trdarPolys.set(gjson.trdarCd, arr);
    });
    paintTrdar();
}

function backToSeoul() {
    if (state.drill) {
        state.drill.abort();
    }
    state.level = "gu";
    state.currentGu = null;
    state.selected = null;
    clearTrdarPolygons();
    state.guPolys.forEach((polys) => polys.forEach((p) => p.setOptions({ fillOpacity: 0.72, strokeWeight: 1.5 })));
    document.getElementById("map-back").style.display = "none";
    state.map.setCenter(new kakao.maps.LatLng(37.5665, 126.978));
    state.map.setLevel(9);
    closeDrawer();
}

// 구 이름 -> 자치구 코드. 상권 목록에서 한 번만 채운다.
const GU_CODE_CACHE = new Map();

// ----- 드로어 -----

function openDrawer() {
    document.querySelector(".map-panel").classList.add("is-open");
}

function closeDrawer() {
    document.querySelector(".map-panel").classList.remove("is-open");
    document.querySelector(".sel-card").style.display = "none";
    if (state.selected) {
        const polys = state.trdarPolys.get(state.selected.trdarCd) || [];
        polys.forEach((p) => p.setOptions({ fillOpacity: 0.78, strokeWeight: 1.5 }));
        state.selected = null;
    }
}

function fillDrawerMetrics(salesAmt, flpop, storeCnt, changeNm) {
    document.querySelector(".sel-hero-num").textContent = fmtEok(salesAmt);
    document.querySelector(".sel-hero-unit").textContent = "억/분기";
    const dds = document.querySelectorAll(".sel-metrics .sel-metric dd");
    dds[0].innerHTML = fmtMan(flpop) + "<span>만 명</span>";
    dds[1].innerHTML = (storeCnt != null ? storeCnt.toLocaleString() : "-") + "<span>개</span>";
    dds[2].innerHTML = (changeNm || "-") + "<span></span>";
    dds[3].innerHTML = (state.seoulRent != null ? Math.round(state.seoulRent).toLocaleString() : "-") + "<span>천원/㎡</span>";
}

function openDrawerForGu(g) {
    document.querySelector(".sel-card").style.display = "";
    document.querySelector(".sel-eyebrow").textContent = "자치구 · " + g.count + "개 상권";
    document.querySelector(".sel-name").textContent = g.gu;
    document.querySelector(".sel-hero-delta").textContent = "";
    const nm = { LL: "다이나믹", LH: "상권확장", HL: "상권축소", HH: "정체" }[guChangeIx(g)];
    fillDrawerMetrics(g.salesAmt, g.flpop, g.storeCnt, nm ? nm + " 우세" : null);
    document.querySelector(".sel-report").style.display = "none";
    document.querySelector(".sel-add").style.display = "none";
    openDrawer();
}

async function selectTrdar(d) {
    if (state.selected) {
        const prev = state.trdarPolys.get(state.selected.trdarCd) || [];
        prev.forEach((p) => p.setOptions({ fillOpacity: 0.78, strokeWeight: 1.5 }));
    }
    state.selected = d;
    (state.trdarPolys.get(d.trdarCd) || []).forEach((p) => p.setOptions({ fillOpacity: 0.95, strokeWeight: 3 }));

    document.querySelector(".sel-card").style.display = "";
    document.querySelector(".sel-eyebrow").textContent = "선택 상권 · " + (d.signguNm || "");
    document.querySelector(".sel-name").textContent = d.trdarNm;
    fillDrawerMetrics(d.salesAmt, d.flpop, d.storeCnt, d.changeIxNm);
    const report = document.querySelector(".sel-report");
    report.style.display = "";
    report.href = "/map/trdar-detail.html?trdarCd=" + d.trdarCd;
    document.querySelector(".sel-add").style.display = "";
    openDrawer();

    // 전분기 증감. 응답 전에 다른 상권을 고르면 버린다.
    const delta = document.querySelector(".sel-hero-delta");
    delta.textContent = "";
    try {
        const totals = quarterlyTotals(await apiData("/api/sales?trdarCd=" + d.trdarCd));
        if (state.selected !== d) {
            return;
        }
        if (totals.length >= 2 && totals[totals.length - 2].amt) {
            const cur = totals[totals.length - 1].amt;
            const prev = totals[totals.length - 2].amt;
            const pct = ((cur - prev) / prev) * 100;
            delta.textContent = "전분기 " + (pct >= 0 ? "+" : "") + pct.toFixed(1) + "%";
            delta.className = "sel-hero-delta " + (pct >= 0 ? "up" : "down");
        }
    } catch (e) { /* 증감은 보조 정보 */ }
}

// ----- 랭킹 -----

function drawRanking() {
    const top = [...state.byGu.values()].sort((a, b) => b.salesAmt - a.salesAmt).slice(0, 12);
    const maxAmt = top.length ? top[0].salesAmt : 0;
    const list = document.querySelector(".rank-list");
    list.innerHTML = "";
    top.forEach((g, i) => {
        const li = document.createElement("li");
        li.className = "rank-row";
        li.style.cursor = "pointer";
        const width = maxAmt ? Math.round((g.salesAmt / maxAmt) * 100) : 0;
        li.innerHTML =
            '<span class="rank-num' + (i === 0 ? " is-top" : "") + '">' + (i + 1) + "</span>" +
            '<div class="rank-main"><span class="rank-name' + (i === 0 ? " is-lead" : "") + '">' + g.gu + "</span>" +
            '<span class="minibar"><span style="width:' + width + '%;background:#a8412c"></span></span></div>' +
            '<div class="rank-val"><b>' + fmtEok(g.salesAmt) + "</b><span>억/분기</span></div>";
        li.addEventListener("click", () => {
            list.querySelectorAll(".rank-row").forEach((r) => r.classList.remove("is-selected"));
            li.classList.add("is-selected");
            drillDown(g.gu, state.guBounds.get(g.gu) || null);
        });
        list.appendChild(li);
    });
}

// ----- 레이어 -----

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
            const scaleBox = document.querySelector(".legend-scale");
            const ends = document.querySelector(".legend-ends");
            if (key === "change") {
                // 범주형이라 4색 그대로 보여준다
                scaleBox.innerHTML = ["HH", "HL", "LH", "LL"]
                    .map((c) => '<span style="background:' + CHANGE_COLORS[c] + '"></span>').join("");
                ends.innerHTML = "<span>정체</span><span>다이나믹</span>";
            } else {
                scaleBox.innerHTML = LEGEND_COLORS
                    .map((c) => '<span style="background:' + c + '"></span>').join("");
                ends.innerHTML = "<span>낮음</span><span>높음</span>";
            }
            // 현재 보이는 단계만 다시 칠한다
            if (state.level === "gu") {
                paintGu();
            } else {
                paintTrdar();
            }
        });
    });
}

async function init() {
    const cfg = await apiData("/api/map/config");
    await loadKakaoSdk(cfg.kakaoJsKey);

    const mapDiv = document.getElementById("map");
    mapDiv.innerHTML = "";
    state.map = new kakao.maps.Map(mapDiv, {
        center: new kakao.maps.LatLng(37.5665, 126.978),
        level: 9,
    });

    // 초기 로드는 요약 + 구 경계 두 개만
    const [districts, guGeo] = await Promise.all([
        apiData("/api/districts/summary"),
        fetch("/geo/seoul-gu.json").then((r) => r.json()),
    ]);
    state.districts = districts || [];
    aggregateByGu();

    // 자치구 코드·임대료는 첫 렌더를 막지 않게 비차단으로 채운다
    state.codesReady = apiData("/api/districts").then((rows) => {
        rows.forEach((d) => {
            if (d.signguNm && d.signguCd && !GU_CODE_CACHE.has(d.signguNm)) {
                GU_CODE_CACHE.set(d.signguNm, d.signguCd);
            }
        });
    }).catch(() => { /* 실패하면 드릴다운만 비활성 */ });
    apiData("/api/rents?metricCd=RENT&regionCd=500002&rlstTyCd=" + encodeURIComponent("소규모상가"))
        .then((rent) => {
            if (rent.length) {
                const q = latestQuarter(rent);
                state.seoulRent = rent.find((r) => r.stdrYyquCd === q).metricValue;
            }
        }).catch(() => { /* 임대료 없으면 '-' */ });

    const gus = new Set(state.districts.map((d) => d.signguNm).filter(Boolean));
    document.getElementById("sum-gu").textContent = gus.size;
    document.getElementById("sum-trdar").textContent = state.districts.length.toLocaleString();
    if (state.districts.length) {
        document.querySelector(".app-quarter").childNodes[0].textContent = quarterLabel(state.districts[0].quarter);
    }
    document.querySelector(".rank-foot").firstChild.textContent =
        "전체 " + state.districts.length.toLocaleString() + "개 상권";

    drawGuPolygons(guGeo.features);
    drawRanking();
    bindLayers();

    document.getElementById("map-back").addEventListener("click", backToSeoul);
    document.getElementById("rank-fab").addEventListener("click", () => {
        document.querySelector(".sel-card").style.display = "none";
        openDrawer();
    });
    document.querySelector(".panel-close").addEventListener("click", closeDrawer);
    document.querySelector(".sel-close").addEventListener("click", closeDrawer);
    document.querySelector(".sel-add").addEventListener("click", (e) => {
        e.preventDefault();
        if (state.selected) {
            cmpAdd(state.selected.trdarCd);
            location.href = "/map/compare.html";
        }
    });
    document.getElementById("app-status").textContent = "연동 정상";
}

init().catch((err) => {
    console.error("지도 초기화 실패:", err);
    document.getElementById("app-status").textContent = "연동 오류";
    document.getElementById("map").innerHTML =
        '<p style="padding:40px;text-align:center;color:#8c7f78">지도를 불러오지 못했습니다. 서버 연결을 확인해 주세요.</p>';
});
