// 상권 상세 화면

const MIX_COLORS = ["#8a3120", "#ca7860", "#d58e76", "#dfa48e", "#e7bba9", "#eccaba", "#f0d6c9"];

// rows를 분기 오름차순 합계 [{q, v}]로
function sumByQuarter(rows, valueOf) {
    const byQ = new Map();
    rows.forEach((r) => {
        byQ.set(r.stdrYyquCd, (byQ.get(r.stdrYyquCd) || 0) + (valueOf(r) || 0));
    });
    return [...byQ.entries()].sort((a, b) => (a[0] < b[0] ? -1 : 1))
        .map(([q, v]) => ({ q: q, v: v }));
}

function qoqPct(totals) {
    if (totals.length < 2 || !totals[totals.length - 2].v) {
        return null;
    }
    const cur = totals[totals.length - 1].v;
    const prev = totals[totals.length - 2].v;
    return ((cur - prev) / prev) * 100;
}

function setKpi(key, num, unit, deltaPct) {
    const card = document.querySelector('[data-kpi="' + key + '"]');
    if (!card) {
        return;
    }
    card.querySelector(".kpi-num").textContent = num;
    if (unit != null) {
        card.querySelector(".kpi-unit").textContent = unit;
    }
    const badge = card.querySelector(".kpi-delta");
    if (!badge) {
        return;
    }
    if (deltaPct == null) {
        badge.remove();
    } else {
        badge.className = "kpi-delta " + (deltaPct >= 0 ? "up" : "down");
        badge.textContent = (deltaPct >= 0 ? "+" : "") + deltaPct.toFixed(1) + "%";
    }
}

// 최근 12분기 매출 추이
const EMPTY_NOTE = '<li style="list-style:none;color:#9a8c84;font-size:13px">데이터 없음</li>';

function drawTrend(totals) {
    const recent = totals.slice(-12);
    const svg = document.querySelector(".trend-svg");
    const axis = document.querySelector(".trend-axis");
    if (!svg) {
        return;
    }
    if (!recent.length) {
        axis.innerHTML = '<span style="color:#9a8c84">데이터 없음</span>';
        return;
    }
    const pts = trendPoints(recent.map((t) => t.v));
    const line = pts.map((p, i) => (i ? "L" : "M") + p.x + " " + p.y).join(" ");
    const area = line + " L" + pts[pts.length - 1].x + " 150 L" + pts[0].x + " 150 Z";
    svg.innerHTML =
        '<defs><linearGradient id="tg" x1="0" y1="0" x2="0" y2="1">' +
        '<stop offset="0" stop-color="#A8412C" stop-opacity="0.22"></stop>' +
        '<stop offset="1" stop-color="#A8412C" stop-opacity="0"></stop></linearGradient></defs>' +
        '<path d="' + area + '" fill="url(#tg)"></path>' +
        '<path d="' + line + '" fill="none" stroke="#A8412C" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"></path>' +
        pts.map((p) => '<circle cx="' + p.x + '" cy="' + p.y + '" r="2.6" fill="#fff" stroke="#A8412C" stroke-width="1.6"></circle>').join("");
    axis.innerHTML = recent.map((t) => "<span>" + t.q.slice(2, 4) + "." + t.q.slice(4) + "Q</span>").join("");
}

// 업종 구성: 최근 분기 매출 상위 업종 비중
function drawMix(sales) {
    const list = document.querySelector(".mix-list");
    if (!list) {
        return;
    }
    if (!sales.length) {
        list.innerHTML = EMPTY_NOTE;
        return;
    }
    const q = latestQuarter(sales);
    const byInduty = new Map();
    let total = 0;
    sales.filter((s) => s.stdrYyquCd === q).forEach((s) => {
        byInduty.set(s.indutyCd, (byInduty.get(s.indutyCd) || 0) + (s.thsmonSelngAmt || 0));
        total += s.thsmonSelngAmt || 0;
    });
    const top = [...byInduty.entries()].sort((a, b) => b[1] - a[1]).slice(0, 7);
    const maxAmt = top.length ? top[0][1] : 0;
    list.innerHTML = top.map(([cd, amt], i) => {
        const nm = (typeof INDUTY_NM !== "undefined" && INDUTY_NM[cd]) || cd;
        const pct = total ? Math.round((amt / total) * 100) : 0;
        const width = maxAmt ? Math.round((amt / maxAmt) * 100) : 0;
        return '<li class="mix-row"><span class="mix-name">' + nm + "</span>" +
            '<span class="mix-bar"><span style="width:' + width + "%;background:" + MIX_COLORS[i] + '"></span></span>' +
            '<span class="mix-pct">' + pct + "%</span></li>";
    }).join("");
}

// 요일 x 시간대 히트맵. 원천이 요일합·시간대합뿐이라 두 분포의 곱으로 추정한다.
const HEAT_DAYS = [["월", "monSelngAmt"], ["화", "tuesSelngAmt"], ["수", "wedSelngAmt"], ["목", "thurSelngAmt"],
    ["금", "friSelngAmt"], ["토", "satSelngAmt"], ["일", "sunSelngAmt"]];
const HEAT_TIMES = [["00-06", "tmzon0006SelngAmt"], ["06-11", "tmzon0611SelngAmt"], ["11-14", "tmzon1114SelngAmt"],
    ["14-17", "tmzon1417SelngAmt"], ["17-21", "tmzon1721SelngAmt"], ["21-24", "tmzon2124SelngAmt"]];
const HEAT_COLORS = ["#f7efea", "#edcbbc", "#db9880", "#c0664e", "#a8412c", "#7e2a1b"];

function drawHeat(sales) {
    const box = document.getElementById("sales-heat");
    if (!box) {
        return;
    }
    const q = latestQuarter(sales);
    const rows = sales.filter((s) => s.stdrYyquCd === q);
    const daySum = HEAT_DAYS.map(([, f]) => rows.reduce((a, s) => a + (s[f] || 0), 0));
    const timeSum = HEAT_TIMES.map(([, f]) => rows.reduce((a, s) => a + (s[f] || 0), 0));
    const dayTotal = daySum.reduce((a, v) => a + v, 0);
    const timeTotal = timeSum.reduce((a, v) => a + v, 0);
    if (!dayTotal || !timeTotal) {
        box.innerHTML = '<p style="color:#9a8c84;font-size:13px">데이터 없음</p>';
        return;
    }
    // 셀 추정치 = 전체 x 요일 비중 x 시간대 비중
    const cells = daySum.map((dv) => timeSum.map((tv) => dayTotal * (dv / dayTotal) * (tv / timeTotal)));
    const max = Math.max(...cells.flat());

    let html = '<div style="display:grid;grid-template-columns:34px repeat(6,1fr);gap:4px;align-items:center">';
    html += "<span></span>" + HEAT_TIMES.map(([t]) =>
        '<span style="font-size:11px;color:#8c7f78;text-align:center">' + t + "</span>").join("");
    cells.forEach((row, i) => {
        html += '<span style="font-size:12px;color:#5c5049">' + HEAT_DAYS[i][0] + "</span>";
        row.forEach((v, j) => {
            const color = HEAT_COLORS[Math.min(5, Math.floor((v / max) * 6))];
            const tip = HEAT_DAYS[i][0] + " " + HEAT_TIMES[j][0] + "시 · 약 " + fmtEok(v) + "억 (분포 추정)";
            html += '<span title="' + tip + '" style="height:26px;border-radius:5px;background:' + color + '"></span>';
        });
    });
    html += "</div>";
    box.innerHTML = html;
}

// 상권 환경: 집객시설·상주인구·아파트·개폐업·프랜차이즈를 한 카드에
function drawEnv(att, res, apt, chg, stores) {
    const box = document.getElementById("env-grid");
    if (!box) {
        return;
    }
    const pick = (rows) => {
        if (!rows || !rows.length) {
            return null;
        }
        const q = latestQuarter(rows);
        return rows.find((r) => r.stdrYyquCd === q);
    };
    const a = pick(att);
    const r = pick(res);
    const p = pick(apt);
    const c = pick(chg);

    // 개업·폐업률은 업종 합산 기준
    let opRt = null;
    let clsRt = null;
    let frcShare = null;
    if (stores && stores.length) {
        const q = latestQuarter(stores);
        const mine = stores.filter((s) => s.stdrYyquCd === q);
        const tot = mine.reduce((x, s) => x + (s.storCo || 0), 0);
        if (tot) {
            opRt = mine.reduce((x, s) => x + (s.opbizStorCo || 0), 0) / tot * 100;
            clsRt = mine.reduce((x, s) => x + (s.clsbizStorCo || 0), 0) / tot * 100;
            frcShare = mine.reduce((x, s) => x + (s.frcStorCo || 0), 0) / tot * 100;
        }
    }

    const n = (v) => (v != null ? (+v).toLocaleString() : "-");
    const pct = (v) => (v != null ? v.toFixed(1) + "%" : "-");
    const items = [
        ["지하철역", a ? n(a.subwayStatnCo) + "개" : "-"],
        ["버스정류장", a ? n(a.busStopCo) + "개" : "-"],
        ["학교 · 병원 · 은행", a ? n(a.schoolCo) + " · " + n(a.hospitalCo) + " · " + n(a.bankCo) : "-"],
        ["집객시설 총", a ? n(a.viatrFcltyCo) + "개" : "-"],
        ["상주인구", r ? (r.totRepopCo >= 10000 ? fmtMan(r.totRepopCo) + "만 명" : n(r.totRepopCo) + "명") : "-"],
        ["가구 수", r ? n(r.totHshldCo) + "가구" : "-"],
        ["아파트", p ? n(p.aptComplxCo) + "단지 · " + n(p.aptHshldCo) + "세대" : "-"],
        ["개업·폐업률", pct(opRt) + " · " + pct(clsRt)],
        ["프랜차이즈 비중", pct(frcShare)],
        ["평균 영업기간", c && c.oprSaleMtAvrg != null ? Math.round(c.oprSaleMtAvrg) + "개월" : "-"],
    ];
    box.innerHTML = '<dl style="display:grid;grid-template-columns:1fr 1fr;gap:9px 18px;margin:0">' +
        items.map(([k, v]) =>
            '<div style="display:flex;justify-content:space-between;border-bottom:1px solid #f4ece6;padding-bottom:7px">' +
            '<dt style="font-size:13px;color:#8c7f78">' + k + "</dt>" +
            '<dd style="font-size:13px;font-weight:600;color:#3f342e;margin:0">' + v + "</dd></div>").join("") + "</dl>";
}

// 인접 경쟁 상권: 같은 자치구 매출 상위 4곳 + 직선거리
function drawCompetitors(d, summaries) {
    const list = document.querySelector(".comp-list");
    if (!list) {
        return;
    }
    const near = summaries
        .filter((s) => s.signguNm === d.signguNm && s.trdarCd !== d.trdarCd)
        .sort((a, b) => (b.salesAmt || 0) - (a.salesAmt || 0))
        .slice(0, 4);
    if (!near.length) {
        list.innerHTML = EMPTY_NOTE;
        return;
    }
    const maxAmt = near[0].salesAmt || 0;
    list.innerHTML = near.map((s) => {
        const km = (d.centerLat != null && s.centerLat != null)
            ? distanceKm(+d.centerLat, +d.centerLot, +s.centerLat, +s.centerLot).toFixed(1) + "km" : "-";
        const width = maxAmt ? Math.round(((s.salesAmt || 0) / maxAmt) * 100) : 0;
        return '<li class="comp-row"><div class="comp-main"><span class="comp-name">' + s.trdarNm + "</span>" +
            '<span class="minibar"><span style="width:' + width + '%;background:#c0664e"></span></span></div>' +
            '<span class="comp-dist">' + km + "</span>" +
            '<span class="comp-val">' + fmtEok(s.salesAmt) + "<span> 억</span></span></li>";
    }).join("");
}

async function load() {
    const trdarCd = new URLSearchParams(location.search).get("trdarCd");
    if (!trdarCd) {
        document.getElementById("detail-title").textContent = "상권을 선택하세요";
        document.getElementById("detail-crumb").textContent = "지도에서 상권을 클릭하면 상세가 열립니다";
        return;
    }

    // 비교 담기
    const cmpBtn = document.querySelector('a.detail-btn[href="/map/compare.html"]');
    if (cmpBtn) {
        cmpBtn.addEventListener("click", (e) => {
            e.preventDefault();
            cmpAdd(trdarCd);
            location.href = "/map/compare.html";
        });
    }
    // 리포트로 상권 전달
    const reportBtn = document.querySelector(".detail-btn-primary");
    if (reportBtn) {
        reportBtn.href = "/map/report.html?trdarCd=" + trdarCd;
    }

    // 하나가 실패해도 나머지 지표는 채운다
    const settled = await Promise.allSettled([
        apiData("/api/districts/" + trdarCd),
        apiData("/api/sales?trdarCd=" + trdarCd),
        apiData("/api/store-stats?trdarCd=" + trdarCd),
        apiData("/api/street-pops?trdarCd=" + trdarCd),
    ]);
    let [d, sales, stores, pop] = settled.map((r) => (r.status === "fulfilled" ? r.value : null));
    if (!d) {
        document.getElementById("detail-title").textContent = "상권 정보를 불러오지 못했습니다";
        return;
    }
    sales = sales || [];
    stores = stores || [];
    pop = pop || [];
    // 인접 상권·임대료·환경 지표는 차트를 막지 않게 병렬로 시작만 해둔다
    const summariesP = apiData("/api/districts/summary?signguCd=" + d.signguCd).catch(() => []);
    const rentP = apiData("/api/rents?metricCd=RENT&regionCd=500002&rlstTyCd=" + encodeURIComponent("소규모상가")).catch(() => []);
    const envP = Promise.allSettled([
        apiData("/api/attractions?trdarCd=" + trdarCd),
        apiData("/api/resident-pops?trdarCd=" + trdarCd),
        apiData("/api/apts?trdarCd=" + trdarCd),
        apiData("/api/trdar-changes?trdarCd=" + trdarCd),
    ]);

    document.title = d.trdarNm + " 상권 상세 · 서울공화국";
    document.querySelector(".app-search-text").textContent = (d.signguNm || "") + " " + d.trdarNm;
    document.getElementById("detail-crumb").textContent = "지도 › " + (d.signguNm || "") + " › " + d.trdarNm;
    document.getElementById("detail-title").innerHTML =
        d.trdarNm + ' <span class="detail-title-sub">· ' + (d.signguNm || "") + "</span>";

    const salesTotals = sumByQuarter(sales, (s) => s.thsmonSelngAmt);
    if (salesTotals.length) {
        setKpi("sales", fmtEok(salesTotals[salesTotals.length - 1].v), "억/분기", qoqPct(salesTotals));
        document.querySelector(".app-quarter").textContent =
            quarterLabel(salesTotals[salesTotals.length - 1].q);
    } else {
        setKpi("sales", "-", "", null);
    }

    const popTotals = sumByQuarter(pop, (p) => p.totFlpopCo);
    if (popTotals.length) {
        setKpi("pop", fmtMan(popTotals[popTotals.length - 1].v), "만 명", qoqPct(popTotals));
    } else {
        setKpi("pop", "-", "", null);
    }

    const storeTotals = sumByQuarter(stores, (s) => s.storCo);
    if (storeTotals.length) {
        setKpi("store", storeTotals[storeTotals.length - 1].v.toLocaleString(), "개", qoqPct(storeTotals));
    } else {
        setKpi("store", "-", "", null);
    }

    // 차트는 확보된 매출 데이터로 즉시 그린다
    drawTrend(salesTotals);
    drawMix(sales);
    drawHeat(sales);

    // 임대료: 상권 단위 원천이 없어 서울 소규모상가 기준으로 표기
    const rent = await rentP;
    if (rent.length) {
        const quarters = [...new Set(rent.map((r) => r.stdrYyquCd))].sort();
        const cur = rent.find((r) => r.stdrYyquCd === quarters[quarters.length - 1]);
        // 분기가 둘 이상일 때만 전분기 대비를 계산한다
        const prev = quarters.length >= 2
            ? rent.find((r) => r.stdrYyquCd === quarters[quarters.length - 2]) : null;
        const pct = prev && prev.metricValue
            ? ((cur.metricValue - prev.metricValue) / prev.metricValue) * 100 : null;
        setKpi("rent", Math.round(cur.metricValue).toLocaleString(), "천원/㎡", pct);
    } else {
        setKpi("rent", "-", "", null);
    }

    const env = (await envP).map((r) => (r.status === "fulfilled" ? r.value : null));
    drawEnv(env[0], env[1], env[2], env[3], stores);

    drawCompetitors(d, await summariesP);
}

load().catch((err) => {
    console.error("상세 로드 실패:", err);
    document.getElementById("detail-title").textContent = "상권 정보를 불러오지 못했습니다";
});
