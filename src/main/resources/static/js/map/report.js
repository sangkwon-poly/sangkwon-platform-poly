// 상권 리포트 화면

const REPORT_MIX_COLORS = ["#8a3120", "#ca7860", "#d58e76", "#dfa48e", "#e7bba9", "#eccaba", "#f0d6c9"];
const report = { totals: [], name: "", induty: "" };

function setPaperKpi(label, value, dropUnit) {
    document.querySelectorAll(".paper-kpi").forEach((kpi) => {
        const l = kpi.querySelector(".paper-kpi-label");
        if (l && l.textContent.trim() === label) {
            const val = kpi.querySelector(".paper-kpi-val");
            const unit = val.querySelector("span");
            val.textContent = value;
            if (unit && !dropUnit) {
                val.appendChild(unit);
            }
        }
    });
}

function drawPaperTrend(totals) {
    const svg = document.querySelector(".paper-trend");
    const recent = totals.slice(-12);
    if (!svg) {
        return;
    }
    if (!recent.length) {
        // 업종 필터 결과가 비면 빈 차트 대신 안내
        svg.style.display = "none";
        svg.insertAdjacentHTML("afterend", '<p style="font-size:13px;color:#9a8c84">데이터 없음</p>');
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
    a.download = (report.name || "상권") + (report.induty ? "_" + report.induty : "") + "_분기매출.csv";
    a.click();
    URL.revokeObjectURL(a.href);
}

// AI 분석: 최근 생성분을 보여주고, 버튼으로 새로 생성한다
function showAiReport(r) {
    document.getElementById("ai-report").textContent = r.resultText;
    document.getElementById("ai-report-meta").textContent =
        r.modelName + " · " + quarterLabel(r.stdrYyquCd) + " 기준 · " + (r.createdAt || "").slice(0, 16).replace("T", " ") + " 생성" +
        (report.induty ? " · " + report.induty + " 업종" : "");
}

function bindAiReport(trdarCd, indutyCd) {
    // 업종 리포트와 상권 전체 리포트는 서버에서 따로 관리한다
    const q = indutyCd ? "?indutyCd=" + indutyCd : "";
    // 생성을 시작하면 늦게 도착한 최초 조회 응답은 버린다
    let staleGet = false;
    apiData("/api/llm-reports/" + trdarCd + "/latest" + q)
        .then((r) => {
            if (!staleGet) {
                showAiReport(r);
            }
        })
        .catch(() => { /* 아직 없음 */ });

    const btn = document.getElementById("ai-generate");
    btn.addEventListener("click", async () => {
        staleGet = true;
        btn.disabled = true;
        btn.textContent = "생성 중...";
        try {
            const res = await fetch("/api/llm-reports/" + trdarCd + q, { method: "POST" });
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

// 포함 섹션 토글. 끄면 미리보기와 인쇄에서 함께 빠진다
function bindSections() {
    document.querySelectorAll(".sec-row").forEach((row) => {
        row.style.cursor = "pointer";
        row.addEventListener("click", () => {
            row.classList.toggle("is-off");
            const sec = document.querySelector('.paper-sec[data-sec="' + row.dataset.sec + '"]');
            if (sec) {
                sec.style.display = row.classList.contains("is-off") ? "none" : "";
            }
        });
    });
}

async function load() {
    document.querySelector(".export-pdf").addEventListener("click", () => window.print());
    document.querySelector(".export-csv").addEventListener("click", downloadCsv);
    bindSections();

    const params = new URLSearchParams(location.search);
    const trdarCd = params.get("trdarCd");
    // 상세 화면에서 업종을 골라 넘어오면 그 업종 기준 보고서로 만든다
    const indutyCd = params.get("indutyCd") || "";
    const indutyNm = indutyCd ? ((typeof INDUTY_NM !== "undefined" && INDUTY_NM[indutyCd]) || indutyCd) : "";
    report.induty = indutyNm;
    if (!trdarCd) {
        document.querySelector(".paper-title").textContent = "상권을 선택하세요";
        document.querySelector(".paper-meta").textContent = "상세 화면에서 리포트 버튼으로 들어오면 채워집니다";
        return;
    }

    bindAiReport(trdarCd, indutyCd);
    const [d, sales] = await Promise.all([
        apiData("/api/districts/" + trdarCd),
        apiData("/api/sales?trdarCd=" + trdarCd),
    ]);
    const mySales = indutyCd ? sales.filter((s) => s.indutyCd === indutyCd) : sales;
    report.name = d.trdarNm;
    report.totals = quarterlyTotals(mySales);
    const quarter = mySales.length ? latestQuarter(mySales) : "";
    // 내려받을 매출이 없으면 CSV 버튼을 잠근다
    document.querySelector(".export-csv").disabled = !report.totals.length;

    document.querySelector(".app-page-name").textContent =
        "리포트 · " + d.trdarNm + (indutyNm ? " · " + indutyNm : "");
    document.title = "리포트 · " + d.trdarNm + (indutyNm ? " · " + indutyNm : "") + " · 서울공화국";
    document.querySelector(".paper-title").textContent =
        d.trdarNm + (indutyNm ? " " + indutyNm : " 상권") + " 분석 리포트";
    document.querySelector(".paper-meta").textContent =
        [d.signguNm, quarterLabel(quarter), indutyNm ? indutyNm + " 업종" : "", "서울공화국"]
            .filter(Boolean).join(" · ");

    if (report.totals.length) {
        setPaperKpi("추정 매출", fmtEok(report.totals[report.totals.length - 1].amt));
    } else {
        // 매출 원천에 행이 없으면 실제 0과 구분해 집계 없음으로. 상세 화면과 같은 표기
        setPaperKpi("추정 매출", "집계 없음", true);
    }
    const stores = (await apiData("/api/store-stats?trdarCd=" + trdarCd)) || [];
    const myStores = indutyCd ? stores.filter((s) => s.indutyCd === indutyCd) : stores;
    if (myStores.length) {
        const q = latestQuarter(myStores);
        const cnt = myStores.filter((s) => s.stdrYyquCd === q).reduce((a, s) => a + (s.storCo || 0), 0);
        setPaperKpi("점포 수", cnt.toLocaleString());
    } else {
        setPaperKpi("점포 수", "0");
    }
    const pop = (await apiData("/api/street-pops?trdarCd=" + trdarCd)) || [];
    if (pop.length) {
        const q = latestQuarter(pop);
        const row = pop.find((p) => p.stdrYyquCd === q);
        setPaperKpi("유동인구", fmtMan(row.totFlpopCo));
    }

    drawPaperTrend(report.totals);
    // 업종 보고서에서도 업종 구성은 상권 전체 기준으로 남긴다
    if (indutyNm) {
        document.querySelector('.paper-sec[data-sec="mix"] .paper-h').textContent = "업종 구성 · 상권 전체";
    }
    drawPaperMix(sales);
}

load().catch((err) => {
    console.error("리포트 로드 실패:", err);
    document.querySelector(".paper-title").textContent = "리포트를 불러오지 못했습니다";
});
