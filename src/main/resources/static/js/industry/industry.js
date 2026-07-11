// 업종·상권 동향은 Pro 전용. 비Pro는 안내로 막고 데이터를 부르지 않는다.
async function fetchMe() {
    try {
        const res = await fetch("/api/members/me", { credentials: "include", headers: { Accept: "application/json" } });
        if (!res.ok) {
            return null;
        }
        const body = await res.json();
        return (body && body.data) || null;
    } catch (e) {
        return null;
    }
}

// mode: "login"(비로그인) / "upgrade"(로그인했지만 무료)
function renderTrendGate(mode) {
    const login = mode === "login";
    const href = login
        ? "/member/login?redirect=" + encodeURIComponent(location.pathname + location.search)
        : "/pricing";
    const label = login ? "로그인하고 이용하기" : "요금제 보러가기";
    const desc = login
        ? "로그인 후 Pro로 이용할 수 있어요."
        : "업종·상권 동향은 Pro가 필요해요.";
    const filter = document.querySelector(".trend-filter");
    if (filter) {
        filter.style.display = "none";
    }
    const main = document.querySelector(".trend-grid");
    if (!main) {
        return;
    }
    main.innerHTML =
        '<div style="grid-column:1/-1;margin:80px auto;max-width:420px;text-align:center;color:#8c7f78">' +
        '<p style="font-size:17px;font-weight:600;color:#3f342e;margin-bottom:8px">업종·상권 동향은 Pro 전용이에요</p>' +
        '<p style="font-size:14px;line-height:1.6;margin-bottom:18px">' + desc + "</p>" +
        '<a class="btn btn-primary" href="' + href + '" ' +
        'style="display:inline-flex;height:44px;padding:0 22px;align-items:center">' + label + "</a>" +
        "</div>";
}

document.addEventListener("DOMContentLoaded", () => {
    fetchMe().then((me) => {
        if (me && me.pro) {
            initTrends();
        } else {
            renderTrendGate(me ? "upgrade" : "login");
        }
    });
});

function initTrends() {

    const industrySelect = document.getElementById("industrySelect");

    Object.entries(INDUTY_NM).forEach(([code, name]) => {
        const option = document.createElement("option");
        option.value = code;
        option.textContent = name;
        industrySelect.appendChild(option);
    });

    const insightChip = document.getElementById("insightChip");
    const summaryText = document.getElementById("summaryText");
    const opportunityText = document.getElementById("opportunityText");
    const warningText = document.getElementById("warningText");
    const updateTime = document.getElementById("updateTime");
    const franchiseChip = document.getElementById("franchiseChip");
    const franchiseList = document.getElementById("franchiseList");
    const patentChip = document.getElementById("patentChip");
    const patentList = document.getElementById("patentList");

    if (!industrySelect) return;

    loadInsight();
    loadFranchise();
    loadTrademarks();

    industrySelect.addEventListener("change", () => {
        loadInsight();
        loadFranchise();
        loadTrademarks();
    });

    async function loadInsight() {
        const indutyCd = industrySelect.value;
        const indutyNm = industrySelect.selectedOptions[0].textContent;

        setLoading(indutyNm);

        try {
            const res = await fetch(`/api/industry-news-insights/latest?indutyCd=${encodeURIComponent(indutyCd)}`);

            if (!res.ok) {
                throw new Error(`HTTP ${res.status}`);
            }

            const json = await res.json();
            const data = json.data ?? json;

            // 응답 대기 중 업종이 바뀌었으면 늦게 온 이전 업종 응답은 버린다
            if (industrySelect.value !== indutyCd) {
                return;
            }

            const parsed = parseInsightText(data.insightText);

            insightChip.textContent = `${data.indutyNm ?? indutyNm} · ${data.yearMonth ?? "최신"}`;
            summaryText.textContent = parsed.summary;
            opportunityText.textContent = parsed.opportunity;
            warningText.textContent = parsed.warning;


            updateTime.textContent = `수집 ${data.yearMonth ?? "-"}`;

        } catch (e) {
            console.error("인사이트 조회 실패:", e);

            if (industrySelect.value !== indutyCd) {
                return;
            }
            insightChip.textContent = `${indutyNm} · 조회 실패`;
            summaryText.textContent = "인사이트를 불러오지 못했습니다.";
            opportunityText.textContent = "-";
            warningText.textContent = "-";
            updateTime.textContent = "수집 -";
        }
    }

    function setLoading(indutyNm) {
        insightChip.textContent = `${indutyNm} · 불러오는 중`;
        summaryText.textContent = "업황 요약을 불러오고 있습니다.";
        opportunityText.textContent = "기회 요인을 불러오고 있습니다.";
        warningText.textContent = "주의 요인을 불러오고 있습니다.";
    }

    // 주요 프랜차이즈 카드: 선택 업종의 가맹점수 상위 브랜드 (공정위 브랜드별 가맹점 현황)
    async function loadFranchise() {
        const indutyCd = industrySelect.value;
        const indutyNm = industrySelect.selectedOptions[0].textContent;

        franchiseChip.textContent = `${indutyNm} · 불러오는 중`;
        franchiseList.innerHTML = '<li class="franchise-empty">가맹점 현황을 불러오고 있습니다.</li>';

        try {
            const res = await fetch(`/api/franchise-brand-stats?indutyCd=${encodeURIComponent(indutyCd)}`);

            if (!res.ok) {
                throw new Error(`HTTP ${res.status}`);
            }

            const json = await res.json();
            const rows = json.data ?? [];

            // 응답 대기 중 업종이 바뀌었으면 늦게 온 이전 업종 응답은 버린다
            if (industrySelect.value !== indutyCd) {
                return;
            }

            if (!rows.length) {
                franchiseChip.textContent = `${indutyNm} · 데이터 없음`;
                franchiseList.innerHTML = '<li class="franchise-empty">이 업종은 집계된 프랜차이즈가 없습니다.</li>';
                return;
            }

            franchiseChip.textContent = `${indutyNm} · ${rows[0].baseYear}년 기준 상위 ${rows.length}개`;
            franchiseList.innerHTML = rows.map((r) =>
                "<li><div><strong>" + esc(r.brandNm) + "</strong><p>" + esc(r.corpNm ?? "-") + "</p></div>" +
                '<div class="franchise-nums"><span>가맹점 ' + Number(r.frcsCnt).toLocaleString() + "개</span>" +
                "<small>" + fmtAvgSales(r.avgSalesAmt) + "</small></div></li>").join("");

        } catch (e) {
            console.error("주요 프랜차이즈 조회 실패:", e);

            if (industrySelect.value !== indutyCd) {
                return;
            }
            franchiseChip.textContent = `${indutyNm} · 조회 실패`;
            franchiseList.innerHTML = '<li class="franchise-empty">가맹점 현황을 불러오지 못했습니다.</li>';
        }
    }

    // 특허·상표 카드: 선택 업종의 최신 상표 출원 (KIPRIS)
    async function loadTrademarks() {
        const indutyCd = industrySelect.value;
        const indutyNm = industrySelect.selectedOptions[0].textContent;

        patentChip.textContent = `${indutyNm} · 불러오는 중`;
        patentList.innerHTML = '<li class="patent-empty">상표 출원 동향을 불러오고 있습니다.</li>';

        try {
            const res = await fetch(`/api/industry-trademarks?indutyCd=${encodeURIComponent(indutyCd)}`);

            if (!res.ok) {
                throw new Error(`HTTP ${res.status}`);
            }

            const json = await res.json();
            const rows = json.data ?? [];

            // 응답 대기 중 업종이 바뀌었으면 늦게 온 이전 업종 응답은 버린다
            if (industrySelect.value !== indutyCd) {
                return;
            }

            if (!rows.length) {
                patentChip.textContent = `${indutyNm} · 데이터 없음`;
                patentList.innerHTML = '<li class="patent-empty">이 업종은 집계된 상표 출원이 없습니다.</li>';
                return;
            }

            patentChip.textContent = `${indutyNm} · 상표 출원 동향`;
            patentList.innerHTML = rows.map((r) =>
                "<li><div><strong>" + esc(r.title) + "</strong><p>" + esc(r.applicantNm ?? "-")
                + " · 출원 " + esc(r.applDate ?? "-") + "</p></div>"
                + '<em class="badge ' + badgeClass(r.status) + '">' + esc(r.status ?? "-") + "</em></li>").join("");

        } catch (e) {
            console.error("상표 출원 동향 조회 실패:", e);

            if (industrySelect.value !== indutyCd) {
                return;
            }
            patentChip.textContent = `${indutyNm} · 조회 실패`;
            patentList.innerHTML = '<li class="patent-empty">상표 출원 동향을 불러오지 못했습니다.</li>';
        }
    }

    // KIPRIS 상태 문자열 -> 배지 색 (등록 초록 / 탈락 계열 빨강 / 진행 중 노랑)
    function badgeClass(status) {
        if (!status) {
            return "yellow";
        }
        if (status.includes("등록")) {
            return "green";
        }
        if (["거절", "취하", "포기", "소멸", "무효"].some((s) => status.includes(s))) {
            return "red";
        }
        return "yellow";
    }

    // 평균매출은 천원 단위(정보공개서 기준)로 온다
    function fmtAvgSales(thousandWon) {
        if (thousandWon == null) {
            return "평균매출 -";
        }
        const eok = thousandWon / 100000;
        if (eok >= 10) {
            return "평균매출 " + Math.round(eok).toLocaleString() + "억 원";
        }
        if (eok >= 1) {
            return "평균매출 " + eok.toFixed(1) + "억 원";
        }
        return "평균매출 " + Math.round(thousandWon / 10).toLocaleString() + "만 원";
    }

    function esc(s) {
        const d = document.createElement("div");
        d.textContent = s == null ? "" : String(s);
        return d.innerHTML;
    }

    function parseInsightText(text) {
        if (!text) {
            return {
                summary: "아직 생성된 인사이트가 없습니다.",
                opportunity: "-",
                warning: "-"
            };
        }

        return {
            summary: extractSection(text, "업황 요약") || text,
            opportunity: extractSection(text, "기회 요인") || "-",
            warning: extractSection(text, "주의 요인") || "-"
        };
    }

    function extractSection(text, title) {
        const regex = new RegExp(`\\[${title}\\]\\s*([\\s\\S]*?)(?=\\n\\[|$)`);
        const match = text.match(regex);
        return match ? match[1].trim() : "";
    }
}