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
    const insightMeta = document.getElementById("insightMeta");
    const updateTime = document.getElementById("updateTime");

    if (!industrySelect) return;

    loadInsight();

    industrySelect.addEventListener("change", loadInsight);

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

            const parsed = parseInsightText(data.insightText);

            insightChip.textContent = `${data.indutyNm ?? indutyNm} · ${data.yearMonth ?? "최신"}`;
            summaryText.textContent = parsed.summary;
            opportunityText.textContent = parsed.opportunity;
            warningText.textContent = parsed.warning;


            updateTime.textContent = `수집 ${data.yearMonth ?? "-"}`;

        } catch (e) {
            console.error("인사이트 조회 실패:", e);

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