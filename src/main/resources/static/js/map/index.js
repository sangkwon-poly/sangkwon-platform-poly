// 메인 지도. 구 단위 단계구분도에서 시작해 구 클릭 시 상권 폴리곤으로 내려간다.

// 레이어별 색. 매출은 브랜드 레드, 유동은 데님 블루, 점포는 틸.
// 성장·쇠퇴는 범주형이라 색약에서도 갈리는 레드-틸 축 + 명도 사다리.
const CHANGE_COLORS = { HH: "#9AA0A8", HL: "#A8412C", LH: "#1E7D70", LL: "#9973BD" };
const CHANGE_ORDER = [["HH", "정체"], ["HL", "축소"], ["LH", "확장"], ["LL", "다이나믹"]];
const CHANGE_SCORE = { LL: 3, LH: 2, HL: 1, HH: 0 };

// 임대료는 상권 단위 원천이 없어 레이어 대신 드로어 지표(서울 소규모상가 기준)로 보여준다.
const LAYERS = {
    sales: { name: "추정 매출", unit: "억/분기", value: (d) => d.salesAmt,
        colors: ["#F9DFD3", "#F0BCA6", "#E29679", "#CE7052", "#A8412C", "#7A2A1B"] },
    flpop: { name: "유동인구", unit: "만 명", value: (d) => d.flpop,
        colors: ["#DCE8F4", "#B3CDE8", "#84AED7", "#5A8EC0", "#3D6FA3", "#28517D"] },
    store: { name: "점포 수", unit: "개", value: (d) => d.storeCnt,
        colors: ["#D9ECE8", "#ABD6CE", "#7BBCB1", "#4F9E92", "#2F8175", "#1C5F55"] },
    change: { name: "성장·쇠퇴", unit: "변화지표", value: (d) => d.changeIx, colors: null },
};

const state = {
    map: null,
    districts: [],          // 상권 요약 전체
    byGu: new Map(),        // 구 이름 -> 집계
    guPolys: new Map(),     // 구 이름 -> kakao.maps.Polygon[]
    guBounds: new Map(),    // 구 이름 -> LatLngBounds
    guLabels: [],           // 구 이름 라벨 오버레이
    trdarPolys: new Map(),  // 상권 코드 -> kakao.maps.Polygon[]
    level: "gu",
    currentGu: null,
    selected: null,
    layer: "sales",
    seoulRent: null,
    drill: null,            // 드릴다운 요청 취소용 AbortController
    polyClicked: false,     // 폴리곤 클릭이 지도 클릭으로 전파돼 드로어가 닫히는 것 방지
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
function makeColorScale(values, ramp) {
    const sorted = values.filter((v) => v != null).sort((a, b) => a - b);
    return (v) => {
        if (v == null || !sorted.length) {
            return ramp[0];
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
        return ramp[Math.min(5, Math.floor((lo / sorted.length) * 6))];
    };
}

// 단계별 구 폴리곤 투명도. 드릴다운 중엔 주변은 흐리게, 들어간 구는 더 옅게
function guOpacity(gu) {
    if (state.level === "gu") {
        return 0.72;
    }
    return gu === state.currentGu ? 0.1 : 0.18;
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
    const scale = makeColorScale([...state.byGu.values()].map(guValue), LAYERS[state.layer].colors);
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
            const scale = makeColorScale(mine.map(LAYERS[state.layer].value), LAYERS[state.layer].colors);
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
            if (gu !== state.currentGu) {
                poly.setOptions({ fillOpacity: state.level === "gu" ? 0.9 : 0.3, strokeWeight: 2.5 });
            }
        });
        kakao.maps.event.addListener(poly, "mouseout", () => {
            poly.setOptions({ fillOpacity: guOpacity(gu), strokeWeight: 1.5 });
        });
        // 드릴다운 중이라도 다른 구를 누르면 그 구로 바로 전환
        kakao.maps.event.addListener(poly, "click", () => {
            state.polyClicked = true;
            if (gu !== state.currentGu) {
                drillDown(gu, boundsOf(paths));
            }
        });
        const arr = state.guPolys.get(gu) || [];
        arr.push(poly);
        state.guPolys.set(gu, arr);

        // 구 이름 라벨. 가장 큰 링의 꼭짓점 평균을 중심으로 잡는다.
        const main = ringsOf(f.geometry).reduce((a, b) => (b.length > a.length ? b : a));
        let latSum = 0;
        let lngSum = 0;
        main.forEach(([lng, lat]) => {
            latSum += lat;
            lngSum += lng;
        });
        const el = document.createElement("div");
        el.textContent = gu;
        el.style.cssText = "padding:2px 7px;border-radius:10px;background:rgba(255,255,255,.75);"
            + "font-size:11px;font-weight:600;color:#5e2a1b;pointer-events:none;white-space:nowrap";
        const label = new kakao.maps.CustomOverlay({
            map: state.map,
            position: new kakao.maps.LatLng(latSum / main.length, lngSum / main.length),
            content: el,
            zIndex: 2,
        });
        state.guLabels.push(label);
    });
    paintGu();
}

function setGuLabelsVisible(visible) {
    state.guLabels.forEach((l) => l.setMap(visible ? state.map : null));
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
    // 주변 구는 흐리게 두어 지금 보는 구가 어디인지 드러낸다
    state.guPolys.forEach((polys, name) => {
        polys.forEach((p) => p.setOptions({
            fillOpacity: name === gu ? 0.1 : 0.18,
            strokeWeight: name === gu ? 3 : 1.5,
        }));
    });
    setGuLabelsVisible(false);

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
            state.polyClicked = true;
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
    setGuLabelsVisible(true);
    document.getElementById("map-back").style.display = "none";
    if (state.seoulBounds) {
        state.map.setBounds(state.seoulBounds, 8);
    }
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
    // 카드 칸이 좁아 짧은 이름을 쓴다
    const nm = { LL: "다이나믹", LH: "확장", HL: "축소", HH: "정체" }[guChangeIx(g)];
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
                // 범주형이라 4색을 이름과 함께 보여준다
                scaleBox.innerHTML = CHANGE_ORDER
                    .map(([c]) => '<span style="background:' + CHANGE_COLORS[c] + '"></span>').join("");
                ends.innerHTML = CHANGE_ORDER.map(([, nm]) => "<span>" + nm + "</span>").join("");
            } else {
                scaleBox.innerHTML = LAYERS[key].colors
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

// 선택한 분기·업종 기준으로 요약을 다시 받아 지도·랭킹을 갱신한다
let summaryReq = 0;
async function reloadSummary() {
    const my = ++summaryReq;
    const params = new URLSearchParams();
    const quarter = document.getElementById("quarter-select").value;
    const induty = document.getElementById("induty-select").value;
    if (quarter) {
        params.set("quarter", quarter);
    }
    if (induty) {
        params.set("indutyCd", induty);
    }
    const qs = params.toString();
    const rows = (await apiData("/api/districts/summary" + (qs ? "?" + qs : ""))) || [];
    if (my !== summaryReq) {
        return; // 더 최근 선택이 있으면 버린다
    }
    state.districts = rows;
    aggregateByGu();
    if (state.level === "gu") {
        paintGu();
    } else {
        paintTrdar();
    }
    drawRanking();
    closeDrawer();
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
    document.querySelector(".rank-foot").firstChild.textContent =
        "전체 " + state.districts.length.toLocaleString() + "개 상권";

    // 분기 선택. 목록을 채우고 바꾸면 그 분기 기준으로 다시 칠한다
    const quarterSel = document.getElementById("quarter-select");
    apiData("/api/districts/quarters").then((quarters) => {
        const valid = (quarters || []).filter((q) => q && q.length === 5);
        if (!valid.length) {
            return; // 목록이 없으면 최신 분기 고정
        }
        quarterSel.innerHTML = valid
            .map((q) => '<option value="' + q + '">' + quarterLabel(q) + "</option>").join("");
        if (state.districts.length) {
            quarterSel.value = state.districts[0].quarter;
        }
    }).catch(() => { /* 목록이 없으면 최신 분기 고정 */ });
    quarterSel.addEventListener("change", reloadSummary);

    // 업종 필터. 이름순으로 채우고 바꾸면 매출·점포 집계가 그 업종 기준이 된다
    const indutySel = document.getElementById("induty-select");
    if (typeof INDUTY_NM !== "undefined") {
        indutySel.innerHTML = '<option value="">전체 업종</option>' +
            Object.entries(INDUTY_NM)
                .sort((a, b) => a[1].localeCompare(b[1], "ko"))
                .map(([cd, nm]) => '<option value="' + cd + '">' + nm + "</option>").join("");
    }
    indutySel.addEventListener("change", reloadSummary);

    drawGuPolygons(guGeo.features);
    // 처음부터 서울 전체가 화면에 차게 맞춘다
    state.seoulBounds = new kakao.maps.LatLngBounds();
    state.guBounds.forEach((b) => {
        state.seoulBounds.extend(b.getSouthWest());
        state.seoulBounds.extend(b.getNorthEast());
    });
    state.map.setBounds(state.seoulBounds, 8);
    drawRanking();
    bindLayers();

    document.getElementById("map-back").addEventListener("click", backToSeoul);
    // 랭킹 버튼은 토글, 지도 빈 곳을 누르면 드로어를 닫는다
    document.getElementById("rank-fab").addEventListener("click", () => {
        const panel = document.querySelector(".map-panel");
        if (panel.classList.contains("is-open")) {
            closeDrawer();
        } else {
            document.querySelector(".sel-card").style.display = "none";
            openDrawer();
        }
    });
    kakao.maps.event.addListener(state.map, "click", () => {
        // 폴리곤 클릭이 지도까지 전파된 경우는 무시하고, 진짜 빈 곳 클릭만 닫는다
        if (state.polyClicked) {
            state.polyClicked = false;
            return;
        }
        closeDrawer();
    });
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
