// 상권 비교: URL의 ?ids= 또는 담아둔 목록으로 여러 상권을 표로 비교한다.

async function apiData(path) {
    const res = await fetch(path);
    if (!res.ok) {
        return null;
    }
    return (await res.json()).data;
}

const CHANGE = { HH: "다이나믹", HL: "확장", LH: "축소", LL: "정체" };
const COLORS = ["#c7735b", "#b85942", "#a9432e", "#913423"];

// 비교 대상 상권코드 (URL 우선, 없으면 담아둔 목록)
function targetIds() {
    const q = new URLSearchParams(location.search).get("ids");
    if (q) {
        return q.split(",").filter(Boolean);
    }
    try {
        return JSON.parse(localStorage.getItem("cmpIds") || "[]");
    } catch (e) {
        return [];
    }
}

// 숫자 지표 행: 상권별 값 + 최고값 강조 + 막대
function numberRow(name, unit, values, fmt) {
    const max = Math.max(...values.map((v) => v || 0), 0);
    const cells = values.map((v) => {
        const width = max ? Math.round(((v || 0) / max) * 100) : 0;
        const best = v != null && v === max && max > 0;
        return '<td class="cmp-cell' + (best ? " is-best" : "") + '">' +
            '<span class="cmp-val">' + (v != null ? fmt(v) : "-") + "</span>" +
            (best ? '<span class="cmp-tag">최고</span>' : "") +
            '<span class="cmp-bar"><span style="width:' + width + '%;background:#b85942"></span></span></td>';
    }).join("");
    return '<tr><th class="cmp-metric" scope="row"><span class="cmp-metric-name">' + name +
        '</span><span class="cmp-metric-unit">' + unit + "</span></th>" + cells + "</tr>";
}

// 범주형 지표 행 (변화지표)
function labelRow(name, values) {
    const cells = values.map((v) => '<td class="cmp-cell"><span class="cmp-val">' + (v || "-") + "</span></td>").join("");
    return '<tr><th class="cmp-metric" scope="row"><span class="cmp-metric-name">' + name + "</span></th>" + cells + "</tr>";
}

async function load() {
    const ids = targetIds();
    const table = document.querySelector("table.cmp");
    if (!ids.length || !table) {
        return;
    }

    const all = (await apiData("/api/districts/summary")) || [];
    const byId = new Map(all.map((d) => [d.trdarCd, d]));
    const picked = ids.map((id) => byId.get(id)).filter(Boolean).slice(0, 4);
    if (!picked.length) {
        return;
    }

    const head = '<tr><th class="cmp-metric-h" scope="col">지표</th>' +
        picked.map((d, i) =>
            '<th class="cmp-col" scope="col"><span class="cmp-color" style="background:' + COLORS[i] + '"></span>' +
            '<span class="cmp-place">' + d.trdarNm + '</span><span class="cmp-place-sub">' + (d.signguNm || "") + "</span></th>"
        ).join("") + "</tr>";

    const body =
        numberRow("추정 매출", "억", picked.map((d) => d.salesAmt), (v) => Math.round(v / 1e8).toLocaleString()) +
        numberRow("유동인구", "만", picked.map((d) => d.flpop), (v) => Math.round(v / 1e4).toLocaleString()) +
        numberRow("점포 수", "개", picked.map((d) => d.storeCnt), (v) => v.toLocaleString()) +
        labelRow("변화지표", picked.map((d) => CHANGE[d.changeIx] || "-"));

    table.querySelector("thead").innerHTML = head;
    table.querySelector("tbody").innerHTML = body;
}

load().catch((err) => console.error("비교 로드 실패:", err));
