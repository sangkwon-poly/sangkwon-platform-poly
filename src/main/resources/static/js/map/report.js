// 상권 리포트 화면

const REPORT_MIX_COLORS = ["#8a3120", "#ca7860", "#d58e76", "#dfa48e", "#e7bba9", "#eccaba", "#f0d6c9"];
const report = { totals: [], name: "" };

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

function drawPaperTrend(totals) {
    const svg = document.querySelector(".paper-trend");
    const recent = totals.slice(-12);
    if (!svg || !recent.length) {
        return;
    }
    const pts = trendPoints(recent.map((t) => t.amt));
    const line = pts.map((p, i) => (i ? "L" : "M") + p.x + " " + p.y).join(" ");
    const area = line + " L" + pts[pts.length - 1].x + " 150 L" + pts[0].x + " 150 Z";
    svg.innerHTML = '<path d="' + area + '" fill="#F1DDD5"></path>' +
        '<path d="' + line + '" fill="none" stroke="#A8412C" stroke-width="3" stroke-linejoin="round"></path>';
}

function drawPaperMix(sales) {
    const list = document.querySelector(".paper-mix");
    if (!list || !sales.length) {
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
        return '<li><span class="paper-mix-name">' + nm + "</span>" +
            '<span class="paper-mix-bar"><span style="width:' + width + "%;background:" + REPORT_MIX_COLORS[i] + '"></span></span>' +
            '<span class="paper-mix-pct">' + pct + "%</span></li>";
    }).join("");
}

// 분기 매출 CSV 다운로드. 엑셀에서 지수표기가 되지 않게 억원 단위로 내린다
function downloadCsv() {
    if (!report.totals.length) {
        return;
    }
    const lines = ["분기,매출(억원)"].concat(
        report.totals.map((t) => quarterLabel(t.q) + "," + (t.amt / 1e8).toFixed(1)));
    const blob = new Blob(["﻿" + lines.join("\n")], { type: "text/csv;charset=utf-8" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = (report.name || "상권") + "_분기매출.csv";
    a.click();
    URL.revokeObjectURL(a.href);
}

// AI 분석: 최근 생성분을 보여주고, 버튼으로 새로 생성한다
function showAiReport(r) {
    document.getElementById("ai-report").textContent = r.resultText;
    document.getElementById("ai-report-meta").textContent =
        r.modelName + " · " + quarterLabel(r.stdrYyquCd) + " 기준 · " + (r.createdAt || "").slice(0, 16).replace("T", " ") + " 생성";
}

function bindAiReport(trdarCd) {
    apiData("/api/llm-reports/" + trdarCd + "/latest").then(showAiReport).catch(() => { /* 아직 없음 */ });

    const btn = document.getElementById("ai-generate");
    btn.addEventListener("click", async () => {
        btn.disabled = true;
        btn.textContent = "생성 중...";
        try {
            const res = await fetch("/api/llm-reports/" + trdarCd, { method: "POST" });
            const body = await res.json();
            if (res.ok) {
                showAiReport(body.data);
            } else {
                document.getElementById("ai-report").textContent =
                    body.message || "생성에 실패했습니다. Gemini API 키 설정을 확인해 주세요.";
            }
        } catch (e) {
            document.getElementById("ai-report").textContent = "생성에 실패했습니다. 서버 연결을 확인해 주세요.";
        } finally {
            btn.disabled = false;
            btn.textContent = "AI 분석 생성";
        }
    });
}

async function load() {
    document.querySelector(".export-pdf").addEventListener("click", () => window.print());
    document.querySelector(".export-csv").addEventListener("click", downloadCsv);

    const trdarCd = new URLSearchParams(location.search).get("trdarCd");
    if (!trdarCd) {
        document.querySelector(".paper-title").textContent = "상권을 선택하세요";
        document.querySelector(".paper-meta").textContent = "상세 화면에서 리포트 버튼으로 들어오면 채워집니다";
        return;
    }

    bindAiReport(trdarCd);
    const [d, sales] = await Promise.all([
        apiData("/api/districts/" + trdarCd),
        apiData("/api/sales?trdarCd=" + trdarCd),
    ]);
    report.name = d.trdarNm;
    report.totals = quarterlyTotals(sales);
    const quarter = sales.length ? latestQuarter(sales) : "";

    document.querySelector(".app-page-name").textContent = "리포트 · " + d.trdarNm;
    document.title = "리포트 · " + d.trdarNm + " · 서울공화국";
    document.querySelector(".paper-title").textContent = d.trdarNm + " 상권 분석 리포트";
    document.querySelector(".paper-meta").textContent =
        (d.signguNm || "") + " · " + quarterLabel(quarter) + " · 서울공화국";

    if (report.totals.length) {
        setPaperKpi("추정 매출", fmtEok(report.totals[report.totals.length - 1].amt));
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
        setPaperKpi("유동인구", fmtMan(row.totFlpopCo));
    }

    drawPaperTrend(report.totals);
    drawPaperMix(sales);
}

load().catch((err) => {
    console.error("리포트 로드 실패:", err);
    document.querySelector(".paper-title").textContent = "리포트를 불러오지 못했습니다";
});
