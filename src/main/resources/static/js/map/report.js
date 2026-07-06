// 상권 리포트: URL의 trdarCd로 제목·메타·핵심 지표를 채운다.

async function apiData(path) {
    const res = await fetch(path);
    if (!res.ok) {
        return null;
    }
    return (await res.json()).data;
}

function latestQuarter(rows) {
    return rows.reduce((max, r) => (r.stdrYyquCd > max ? r.stdrYyquCd : max), "");
}

// 20261 -> 2026년 1분기
function quarterLabel(code) {
    return code ? code.slice(0, 4) + "년 " + code.slice(4) + "분기" : "";
}

// 라벨이 일치하는 KPI의 숫자만 바꾸고 단위 span은 유지
function setPaperKpi(label, value) {
    document.querySelectorAll(".paper-kpi").forEach((kpi) => {
        const l = kpi.querySelector(".paper-kpi-label");
        if (l && l.textContent.trim() === label) {
            const val = kpi.querySelector(".paper-kpi-val");
            const unit = val.querySelector("span");
            val.textContent = value;
            if (unit) {
                val.appendChild(unit);
            }
        }
    });
}

async function load() {
    const trdarCd = new URLSearchParams(location.search).get("trdarCd");
    if (!trdarCd) {
        return;
    }

    const d = await apiData("/api/districts/" + trdarCd);
    const sales = (await apiData("/api/sales?trdarCd=" + trdarCd)) || [];
    const quarter = sales.length ? latestQuarter(sales) : "";

    if (d) {
        document.querySelector(".app-page-name").textContent = "리포트 · " + d.trdarNm;
        document.querySelector(".paper-title").textContent = d.trdarNm + " 상권 분석 리포트";
        document.querySelector(".paper-meta").textContent =
            (d.signguNm || "") + " " + d.trdarNm + " · " + quarterLabel(quarter) + " · 서울공화국";
    }

    if (sales.length) {
        const won = sales.filter((s) => s.stdrYyquCd === quarter).reduce((a, s) => a + (s.thsmonSelngAmt || 0), 0);
        setPaperKpi("추정 매출", Math.round(won / 1e8).toLocaleString());
    }

    const stores = (await apiData("/api/store-stats?trdarCd=" + trdarCd)) || [];
    if (stores.length) {
        const q = latestQuarter(stores);
        const cnt = stores.filter((s) => s.stdrYyquCd === q).reduce((a, s) => a + (s.storCo || 0), 0);
        setPaperKpi("점포 수", cnt.toLocaleString());
    }

    const pop = (await apiData("/api/street-pops?trdarCd=" + trdarCd)) || [];
    if (pop.length) {
        const q = latestQuarter(pop);
        const row = pop.find((p) => p.stdrYyquCd === q);
        setPaperKpi("유동인구", Math.round((row.totFlpopCo || 0) / 1e4).toLocaleString());
    }
}

load().catch((err) => console.error("리포트 로드 실패:", err));
