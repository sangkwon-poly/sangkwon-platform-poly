(function () {
    "use strict";

    var CAP = 200; // 표에 한 번에 그리는 최대 행 수 (매출 상위부터)

    function api(path) {
        return fetch(path).then(function (res) {
            return res.json().catch(function () { return null; }).then(function (b) {
                return { ok: res.ok, status: res.status, body: b };
            });
        });
    }
    function esc(s) { var d = document.createElement("div"); d.textContent = (s == null) ? "" : String(s); return d.innerHTML; }
    function num(n) { return (n == null) ? "—" : Number(n).toLocaleString(); }

    function qLabel(q) {
        if (!q || q.length < 5) { return q || "—"; }
        return q.slice(0, 4) + "년 " + q.slice(4) + "분기";
    }
    function salesRow(won) {
        if (won == null) { return "—"; }
        var eok = won / 1e8;
        if (eok >= 1) { return Math.round(eok).toLocaleString() + "억"; }
        return Math.round(won / 1e4).toLocaleString() + "만";
    }
    function salesBig(won) {
        if (won == null) { return "—"; }
        if (won >= 1e12) { return (won / 1e12).toFixed(1) + "조원"; }
        return Math.round(won / 1e8).toLocaleString() + "억원";
    }
    function chgClass(nm) {
        if (!nm) { return "trd-chg-flat"; }
        if (nm.indexOf("확장") >= 0) { return "trd-chg-up"; }
        if (nm.indexOf("다이나믹") >= 0) { return "trd-chg-dyn"; }
        if (nm.indexOf("축소") >= 0) { return "trd-chg-down"; }
        return "trd-chg-flat"; // 정체 등
    }

    var quarterSel = document.getElementById("trd-quarter");
    var guSel = document.getElementById("trd-gu");
    var keywordInput = document.getElementById("trd-keyword");
    var tbody = document.getElementById("trd-tbody");
    var note = document.getElementById("trd-note");
    var meta = document.getElementById("trd-meta");
    var countEl = document.getElementById("trd-count");
    var statCount = document.getElementById("trd-stat-count");
    var statSales = document.getElementById("trd-stat-sales");
    var statStore = document.getElementById("trd-stat-store");

    var state = { all: [], sortKey: "salesAmt", sortDir: "desc" };

    function emptyRow(msg) { return '<tr><td colspan="6" class="trd-empty">' + esc(msg) + "</td></tr>"; }

    function filtered() {
        var gu = guSel.value;
        var kw = keywordInput.value.trim().toLowerCase();
        return state.all.filter(function (r) {
            if (gu && r.signguNm !== gu) { return false; }
            if (kw) {
                var hay = ((r.trdarNm || "") + " " + (r.signguNm || "")).toLowerCase();
                if (hay.indexOf(kw) < 0) { return false; }
            }
            return true;
        });
    }

    function sortRows(rows) {
        var k = state.sortKey, dir = state.sortDir === "asc" ? 1 : -1;
        return rows.slice().sort(function (a, b) {
            return ((a[k] || 0) - (b[k] || 0)) * dir;
        });
    }

    function render() {
        var rows = sortRows(filtered());
        var total = rows.length;

        // 통계
        var sumSales = 0, sumStore = 0;
        rows.forEach(function (r) { sumSales += (r.salesAmt || 0); sumStore += (r.storeCnt || 0); });
        statCount.textContent = total.toLocaleString() + "개";
        statSales.textContent = salesBig(sumSales);
        statStore.textContent = sumStore.toLocaleString() + "개";
        countEl.textContent = "총 " + total.toLocaleString() + "개 상권";

        if (!total) { tbody.innerHTML = emptyRow("조건에 맞는 상권이 없습니다."); note.hidden = true; return; }

        var shown = rows.slice(0, CAP);
        tbody.innerHTML = shown.map(function (r) {
            return "<tr>"
                + '<td><div class="trd-name">' + esc(r.trdarNm) + "</div><div class=\"trd-code\">" + esc(r.trdarCd) + "</div></td>"
                + '<td class="trd-gu">' + esc(r.signguNm) + "</td>"
                + '<td class="col-center"><span class="trd-chg ' + chgClass(r.changeIxNm) + '">' + esc(r.changeIxNm || "—") + "</span></td>"
                + '<td class="col-num trd-num">' + salesRow(r.salesAmt) + "</td>"
                + '<td class="col-num trd-num">' + num(r.flpop) + '<span class="trd-num-sub"> 명</span></td>'
                + '<td class="col-num trd-num">' + num(r.storeCnt) + '<span class="trd-num-sub"> 개</span></td>'
                + "</tr>";
        }).join("");

        if (total > CAP) {
            note.hidden = false;
            note.textContent = "매출 상위 " + CAP + "개만 표시했습니다. 자치구·검색으로 좁혀 보세요. (총 " + total.toLocaleString() + "개)";
        } else {
            note.hidden = true;
        }
    }

    function buildGuOptions() {
        var seen = {}, gus = [];
        state.all.forEach(function (r) { if (r.signguNm && !seen[r.signguNm]) { seen[r.signguNm] = 1; gus.push(r.signguNm); } });
        gus.sort(function (a, b) { return a.localeCompare(b, "ko"); });
        var prev = guSel.value;
        guSel.innerHTML = '<option value="">전체</option>'
            + gus.map(function (g) { return '<option value="' + esc(g) + '">' + esc(g) + "</option>"; }).join("");
        if (prev && seen[prev]) { guSel.value = prev; }
    }

    function loadSummary(quarter) {
        tbody.innerHTML = emptyRow("불러오는 중…");
        api("/api/districts/summary?quarter=" + encodeURIComponent(quarter)).then(function (r) {
            state.all = (r.ok && r.body && r.body.data) ? r.body.data : [];
            meta.textContent = "서울시 상권 · " + qLabel(quarter);
            buildGuOptions();
            render();
        });
    }

    function updateSortHeaders() {
        Array.prototype.forEach.call(document.querySelectorAll(".trd-sort"), function (th) {
            if (th.getAttribute("data-sort") === state.sortKey) {
                th.setAttribute("aria-sort", state.sortDir === "asc" ? "ascending" : "descending");
            } else {
                th.removeAttribute("aria-sort");
            }
        });
    }

    // 이벤트
    quarterSel.addEventListener("change", function () { loadSummary(quarterSel.value); });
    guSel.addEventListener("change", render);
    keywordInput.addEventListener("input", render);
    Array.prototype.forEach.call(document.querySelectorAll(".trd-sort"), function (th) {
        th.addEventListener("click", function () {
            var key = th.getAttribute("data-sort");
            if (state.sortKey === key) { state.sortDir = state.sortDir === "asc" ? "desc" : "asc"; }
            else { state.sortKey = key; state.sortDir = "desc"; }
            updateSortHeaders();
            render();
        });
    });

    // 분기 목록 → 최신 분기로 첫 로딩
    api("/api/districts/quarters").then(function (r) {
        var quarters = (r.ok && r.body && r.body.data) ? r.body.data : [];
        if (!quarters.length) { tbody.innerHTML = emptyRow("조회 가능한 분기가 없습니다."); return; }
        quarterSel.innerHTML = quarters.map(function (q) { return '<option value="' + esc(q) + '">' + esc(qLabel(q)) + "</option>"; }).join("");
        quarterSel.value = quarters[0];
        loadSummary(quarters[0]);
    });
})();
