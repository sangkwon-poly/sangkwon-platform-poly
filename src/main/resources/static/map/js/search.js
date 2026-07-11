// 상권 검색 결과 화면

const SORTS = [
    { key: "salesAmt", label: "추정 매출 높은순" },
    { key: "flpop", label: "유동인구 높은순" },
    { key: "storeCnt", label: "점포 수 높은순" },
];
const MAX_ROWS = 300;

const view = { all: [], keyword: "", sort: 0 };

function currentRows() {
    const checkedGus = [...document.querySelectorAll("#filter-gu .check.is-on")]
        .map((li) => li.dataset.gu);
    const minSales = +document.getElementById("filter-minsales").value;
    const kw = view.keyword;
    const sortKey = SORTS[view.sort].key;
    return view.all
        .filter((d) => !kw || d.trdarNm.includes(kw) || (d.signguNm || "").includes(kw))
        .filter((d) => !checkedGus.length || checkedGus.includes(d.signguNm))
        .filter((d) => !minSales || (d.salesAmt || 0) >= minSales)
        .sort((a, b) => (b[sortKey] || 0) - (a[sortKey] || 0));
}

function buildRow(d, rank, maxAmt) {
    const width = maxAmt ? Math.round(((d.salesAmt || 0) / maxAmt) * 100) : 0;
    // 매출 null은 집계 없음이라 0과 구분해 표기, 점포 null은 실제 0개
    const amtHtml = d.salesAmt != null
        ? "<b>" + fmtEok(d.salesAmt) + '</b><span class="rt-unit"> 억</span>'
        : "<b>-</b>";
    const ix = d.changeIx || "";
    const tr = document.createElement("tr");
    if (rank === 1) {
        tr.className = "is-top";
    }
    tr.style.cursor = "pointer";
    tr.innerHTML =
        '<td class="rt-rank"><span class="rank-num' + (rank === 1 ? " is-top" : "") + '">' + rank + "</span></td>" +
        '<td class="rt-name"><span class="rt-place">' + d.trdarNm + '</span><span class="rt-desc">' + (d.signguNm || "") + "</span></td>" +
        '<td class="rt-num">' + amtHtml + '<span class="minibar"><span style="width:' + width + '%;background:#c0664e"></span></span></td>' +
        '<td class="rt-num rt-plain">' + (d.flpop != null ? fmtMan(d.flpop) + " 만" : "-") + "</td>" +
        '<td class="rt-num rt-plain">' + (d.storeCnt || 0).toLocaleString() + "</td>" +
        '<td class="rt-num ' + (ix ? (CHANGE_UP.has(ix) ? "rt-up" : "rt-down") : "rt-plain") + '">' + (d.changeIxNm || "-") + "</td>";
    tr.addEventListener("click", () => {
        location.href = "/map/trdar-detail?trdarCd=" + d.trdarCd;
    });
    return tr;
}

function render() {
    const rows = currentRows();
    const count = document.querySelector(".result-count");
    count.innerHTML = "<b>" + rows.length.toLocaleString() + "</b>개 상권" +
        (rows.length > MAX_ROWS ? " · 상위 " + MAX_ROWS + "개 표시" : "");

    const tbody = document.querySelector(".rt tbody");
    tbody.innerHTML = "";
    const maxAmt = rows.reduce((max, d) => Math.max(max, d.salesAmt || 0), 0);
    rows.slice(0, MAX_ROWS).forEach((d, i) => tbody.appendChild(buildRow(d, i + 1, maxAmt)));
}

// 자치구 체크 목록을 데이터에서 생성
function buildGuFilter() {
    const counts = new Map();
    view.all.forEach((d) => {
        if (d.signguNm) {
            counts.set(d.signguNm, (counts.get(d.signguNm) || 0) + 1);
        }
    });
    const ul = document.getElementById("filter-gu");
    ul.innerHTML = "";
    [...counts.keys()].sort((a, b) => a.localeCompare(b, "ko")).forEach((gu) => {
        const li = document.createElement("li");
        li.className = "check";
        li.dataset.gu = gu;
        li.style.cursor = "pointer";
        li.innerHTML = '<span class="check-box" aria-hidden="true"></span>' + gu +
            ' <span style="color:#b0a49e;font-size:12px">' + counts.get(gu) + "</span>";
        li.addEventListener("click", () => {
            li.classList.toggle("is-on");
            li.querySelector(".check-box").textContent = li.classList.contains("is-on") ? "✓" : "";
            render();
        });
        ul.appendChild(li);
    });
}

function bindControls() {
    const sortBtn = document.querySelector(".result-sort");
    sortBtn.textContent = SORTS[view.sort].label + " ▾";
    sortBtn.addEventListener("click", () => {
        view.sort = (view.sort + 1) % SORTS.length;
        sortBtn.textContent = SORTS[view.sort].label + " ▾";
        render();
    });

    document.getElementById("filter-minsales").addEventListener("change", render);

    // 검색어는 입력 즉시 반영하되 300ms 디바운스
    const input = document.querySelector(".app-search input");
    let timer = null;
    input.addEventListener("input", () => {
        clearTimeout(timer);
        timer = setTimeout(() => {
            view.keyword = input.value.trim();
            render();
        }, 300);
    });
    input.closest("form").addEventListener("submit", (e) => {
        e.preventDefault();
        clearTimeout(timer);
        view.keyword = input.value.trim();
        render();
        logSearch(view.keyword);
    });

    bindRecent(input);

    const cmp = document.querySelector(".result-compare");
    cmp.innerHTML = "비교함 <b>" + cmpList().length + "</b>";
    cmp.style.cursor = "pointer";
    cmp.addEventListener("click", () => {
        location.href = "/map/compare";
    });
}

// submit·load 때만 남긴다. 입력 디바운스마다는 남기지 않는다.
function logSearch(keyword) {
    if (!keyword) return;
    fetch("/api/search-logs", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ keyword: keyword }),
    }).catch(() => {});
}

// 비로그인은 조회가 막혀 드롭다운이 자동으로 안 뜬다.
var recentList = [];

function bindRecent(input) {
    const wrap = document.querySelector(".app-search-wrap");
    const box = document.getElementById("recent-box");
    if (!wrap || !box) return;

    input.addEventListener("focus", () => loadRecent(box, input));
    input.addEventListener("input", () => showRecent(box, input));
    document.addEventListener("click", (e) => {
        if (!wrap.contains(e.target)) box.hidden = true;
    });
}

function loadRecent(box, input) {
    apiData("/api/search-logs?limit=5")
        .then((list) => { recentList = list || []; showRecent(box, input); })
        .catch(() => { box.hidden = true; });
}

// 입력 중이면 최신 3개, 빈 창이면 5개
function showRecent(box, input) {
    var n = input.value.trim() ? 3 : 5;
    renderRecent(box, input, recentList.slice(0, n));
}

function renderRecent(box, input, list) {
    box.innerHTML = "";
    if (!list.length) {
        box.hidden = true;
        return;
    }
    list.forEach((r) => {
        const item = document.createElement("div");
        item.className = "recent-item";
        const kw = document.createElement("span");
        kw.textContent = r.keyword; // 검색어는 사용자 입력이라 textContent로만 넣는다(XSS 방지)
        const del = document.createElement("button");
        del.type = "button";
        del.className = "recent-item__del";
        del.textContent = "×";
        del.setAttribute("aria-label", "삭제");
        item.appendChild(kw);
        item.appendChild(del);
        item.addEventListener("click", () => {
            input.value = r.keyword;
            view.keyword = r.keyword;
            box.hidden = true;
            render();
        });
        del.addEventListener("click", (e) => {
            e.stopPropagation();
            fetch("/api/search-logs/" + encodeURIComponent(r.keyword), {
                method: "DELETE",
                credentials: "include",
            }).then(() => loadRecent(box, input)).catch(() => {});
        });
        box.appendChild(item);
    });
    box.hidden = false;
}

async function load() {
    const params = new URLSearchParams(location.search);
    view.keyword = (params.get("q") || params.get("keyword") || "").trim();

    const input = document.querySelector(".app-search input");
    if (input && view.keyword) {
        input.value = view.keyword;
    }
    if (view.keyword) {
        logSearch(view.keyword);
    }

    // 전체를 한 번만 받고 검색어·필터는 클라이언트에서 거른다
    view.all = (await apiData("/api/districts/summary")) || [];

    if (view.all.length) {
        document.querySelector(".app-quarter").textContent = quarterLabel(view.all[0].quarter);
    }
    buildGuFilter();
    bindControls();
    render();
}

load().catch((err) => {
    console.error("검색 로드 실패:", err);
    document.querySelector(".result-count").innerHTML = "결과를 불러오지 못했습니다";
});
