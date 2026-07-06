// 검색 결과: 상권 요약을 조회해 결과 표를 채운다. 검색어는 index 검색폼의 ?q= 로 넘어온다.

async function apiData(path) {
    const res = await fetch(path);
    if (!res.ok) {
        return null;
    }
    return (await res.json()).data;
}

// 상권 변화지표 코드 -> 표시 라벨
const CHANGE = { HH: "다이나믹", HL: "확장", LH: "축소", LL: "정체" };

function eok(won) {
    return won ? Math.round(won / 1e8).toLocaleString() : "-";
}

function buildRow(d, rank, maxAmt) {
    const amt = d.salesAmt || 0;
    const barWidth = maxAmt ? Math.round((amt / maxAmt) * 100) : 0;
    const ix = d.changeIx || "";
    const tr = document.createElement("tr");
    if (rank === 1) {
        tr.className = "is-top";
    }
    tr.innerHTML =
        '<td class="rt-rank"><span class="rank-num' + (rank === 1 ? " is-top" : "") + '">' + rank + "</span></td>" +
        '<td class="rt-name"><span class="rt-place">' + d.trdarNm + '</span><span class="rt-desc">' + (d.signguNm || "") + "</span></td>" +
        '<td class="rt-num"><b>' + eok(amt) + '</b><span class="rt-unit"> 억</span><span class="minibar"><span style="width:' + barWidth + '%;background:#c0664e"></span></span></td>' +
        '<td class="rt-num rt-plain">' + (d.flpop != null ? Math.round(d.flpop / 1e4).toLocaleString() + " 만" : "-") + "</td>" +
        '<td class="rt-num rt-plain">' + (d.storeCnt != null ? d.storeCnt.toLocaleString() : "-") + "</td>" +
        '<td class="rt-num ' + (ix.startsWith("H") ? "rt-up" : "rt-down") + '">' + (CHANGE[ix] || "-") + "</td>";
    return tr;
}

async function load() {
    const params = new URLSearchParams(location.search);
    const keyword = params.get("q") || params.get("keyword") || "";

    const label = document.querySelector(".app-search-text");
    if (label) {
        label.textContent = keyword || "상권 검색";
    }

    const query = keyword ? "?keyword=" + encodeURIComponent(keyword) : "";
    const rows = (await apiData("/api/districts/summary" + query)) || [];

    const count = document.querySelector(".result-count b");
    if (count) {
        count.textContent = rows.length.toLocaleString();
    }

    const tbody = document.querySelector(".rt tbody");
    if (!tbody) {
        return;
    }
    tbody.innerHTML = "";
    const maxAmt = rows.reduce((max, d) => Math.max(max, d.salesAmt || 0), 0);
    // 표가 너무 길지 않게 상위 200개만
    rows.slice(0, 200).forEach((d, i) => tbody.appendChild(buildRow(d, i + 1, maxAmt)));
}

load().catch((err) => console.error("검색 로드 실패:", err));
