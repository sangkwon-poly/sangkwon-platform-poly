// 상권 상세: URL의 trdarCd로 해당 상권의 매출·유동·점포·임대료를 채운다.

async function apiData(path) {
    const res = await fetch(path);
    if (!res.ok) {
        return null;
    }
    return (await res.json()).data;
}

// 가장 최근 분기 코드
function latestQuarter(rows) {
    return rows.reduce((max, r) => (r.stdrYyquCd > max ? r.stdrYyquCd : max), "");
}

// 최근 분기 대비 직전 분기 증감률(%). 데이터가 부족하면 null.
function computeDelta(rows, valueOf) {
    const quarters = [...new Set(rows.map((r) => r.stdrYyquCd))].sort();
    if (quarters.length < 2) {
        return null;
    }
    const sumOf = (q) => rows.filter((r) => r.stdrYyquCd === q)
        .reduce((acc, r) => acc + (valueOf(r) || 0), 0);
    const prev = sumOf(quarters[quarters.length - 2]);
    if (!prev) {
        return null;
    }
    return ((sumOf(quarters[quarters.length - 1]) - prev) / prev) * 100;
}

// KPI 카드 값 채우기. 증감이 없으면 가짜 배지를 제거한다.
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

async function load() {
    const trdarCd = new URLSearchParams(location.search).get("trdarCd");
    if (!trdarCd) {
        return;
    }

    // 상권 이름 · 자치구
    const d = await apiData("/api/districts/" + trdarCd);
    if (d) {
        document.getElementById("detail-crumb").textContent =
            "지도 › " + (d.signguNm || "") + " › " + d.trdarNm;
        document.getElementById("detail-title").innerHTML =
            d.trdarNm + ' <span class="detail-title-sub">· ' + (d.signguNm || "") + "</span>";
    }

    // 추정 매출: 최근 분기, 업종 합계 (원 -> 억)
    const sales = (await apiData("/api/sales?trdarCd=" + trdarCd)) || [];
    if (sales.length) {
        const q = latestQuarter(sales);
        const won = sales.filter((s) => s.stdrYyquCd === q)
            .reduce((acc, s) => acc + (s.thsmonSelngAmt || 0), 0);
        setKpi("sales", Math.round(won / 1e8).toLocaleString(), "억", computeDelta(sales, (s) => s.thsmonSelngAmt));
    } else {
        setKpi("sales", "-", "", null);
    }

    // 유동인구: 최근 분기 (명 -> 만)
    const pop = (await apiData("/api/street-pops?trdarCd=" + trdarCd)) || [];
    if (pop.length) {
        const q = latestQuarter(pop);
        const row = pop.find((p) => p.stdrYyquCd === q);
        setKpi("pop", Math.round((row.totFlpopCo || 0) / 1e4).toLocaleString(), "만", computeDelta(pop, (p) => p.totFlpopCo));
    } else {
        setKpi("pop", "-", "", null);
    }

    // 점포 수: 최근 분기, 업종 합계
    const stores = (await apiData("/api/store-stats?trdarCd=" + trdarCd)) || [];
    if (stores.length) {
        const q = latestQuarter(stores);
        const cnt = stores.filter((s) => s.stdrYyquCd === q)
            .reduce((acc, s) => acc + (s.storCo || 0), 0);
        setKpi("store", cnt.toLocaleString(), "개", computeDelta(stores, (s) => s.storCo));
    } else {
        setKpi("store", "-", "", null);
    }

    // 임대료: 상권 단위 값은 원천에 없어 서울 소규모상가 기준으로 표기
    const rentUrl = "/api/rents?metricCd=RENT&regionCd=500002&rlstTyCd=" + encodeURIComponent("소규모상가");
    const rent = (await apiData(rentUrl)) || [];
    if (rent.length) {
        const q = latestQuarter(rent);
        const row = rent.find((r) => r.stdrYyquCd === q);
        const label = document.querySelector('[data-kpi="rent"] .kpi-label');
        if (label) {
            label.textContent = "임대료(서울 소규모)";
        }
        setKpi("rent", Math.round(row.metricValue).toLocaleString(), "천원/㎡", computeDelta(rent, (r) => r.metricValue));
    } else {
        setKpi("rent", "-", "", null);
    }
}

load().catch((err) => console.error("상세 로드 실패:", err));
