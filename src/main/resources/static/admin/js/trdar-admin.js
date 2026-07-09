(function () {
    "use strict";

    var PAGE_SIZE = 50; // 페이지당 행 수 (전체는 클라이언트에 이미 로드됨)

    function api(path) {
        return fetch(path).then(function (res) {
            return res.json().catch(function () { return null; }).then(function (b) {
                return { ok: res.ok, status: res.status, body: b };
            });
        });
    }
    function esc(s) { var d = document.createElement("div"); d.textContent = (s == null) ? "" : String(s); return d.innerHTML.replace(/"/g, "&quot;").replace(/'/g, "&#39;"); }
    function num(n) { return (n == null) ? "—" : Number(n).toLocaleString(); }
    function qLabel(q) { if (!q || q.length < 5) { return q || "—"; } return q.slice(0, 4) + "년 " + q.slice(4) + "분기"; }
    function qShort(q) { if (!q || q.length < 5) { return q || ""; } return q.slice(2, 4) + "." + q.slice(4) + "Q"; }
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
        return "trd-chg-flat";
    }

    var quarterSel = document.getElementById("trd-quarter");
    var guSel = document.getElementById("trd-gu");
    var keywordInput = document.getElementById("trd-keyword");
    var tbody = document.getElementById("trd-tbody");
    var pager = document.getElementById("trd-pager");
    var meta = document.getElementById("trd-meta");
    var countEl = document.getElementById("trd-count");
    var statCount = document.getElementById("trd-stat-count");
    var statSales = document.getElementById("trd-stat-sales");
    var statStore = document.getElementById("trd-stat-store");
    var healthQ = document.getElementById("trd-health-q");
    var healthFacts = document.getElementById("trd-health-facts");
    var healthFlags = document.getElementById("trd-health-flags");
    var modal = document.getElementById("trd-modal");
    var modalTitle = document.getElementById("trd-modal-title");
    var modalSub = document.getElementById("trd-modal-sub");
    var metricsEl = document.getElementById("trd-metrics");
    var indutyEl = document.getElementById("trd-induty");
    var trendEl = document.getElementById("trd-trend");

    var state = { all: [], sortKey: "salesAmt", sortDir: "desc", quarter: null, page: 0 };

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
        return rows.slice().sort(function (a, b) { return ((a[k] || 0) - (b[k] || 0)) * dir; });
    }

    function rowHtml(r) {
        return '<tr class="trd-row" data-cd="' + esc(r.trdarCd) + '">'
            + '<td><div class="trd-name">' + esc(r.trdarNm) + "</div><div class=\"trd-code\">" + esc(r.trdarCd) + "</div></td>"
            + '<td class="trd-gu">' + esc(r.signguNm) + "</td>"
            + '<td class="col-center"><span class="trd-chg ' + chgClass(r.changeIxNm) + '">' + esc(r.changeIxNm || "—") + "</span></td>"
            + '<td class="col-num trd-num">' + salesRow(r.salesAmt) + "</td>"
            + '<td class="col-num trd-num">' + num(r.flpop) + '<span class="trd-num-sub"> 명</span></td>'
            + '<td class="col-num trd-num">' + num(r.storeCnt) + '<span class="trd-num-sub"> 개</span></td>'
            + "</tr>";
    }

    function pageWindow(cur, pages) {
        var out = [], from = Math.max(0, cur - 2), to = Math.min(pages - 1, cur + 2);
        if (from > 0) { out.push(0); if (from > 1) { out.push("…"); } }
        for (var i = from; i <= to; i++) { out.push(i); }
        if (to < pages - 1) { if (to < pages - 2) { out.push("…"); } out.push(pages - 1); }
        return out;
    }

    function renderPager(pages) {
        if (pages <= 1) { pager.hidden = true; pager.innerHTML = ""; return; }
        pager.hidden = false;
        var cur = state.page;
        var html = '<button type="button" class="trd-pg" data-pg="' + (cur - 1) + '"' + (cur <= 0 ? " disabled" : "") + ">이전</button>";
        pageWindow(cur, pages).forEach(function (p) {
            html += (p === "…")
                ? '<span class="trd-pg-gap">…</span>'
                : '<button type="button" class="trd-pg' + (p === cur ? " is-active" : "") + '" data-pg="' + p + '">' + (p + 1) + "</button>";
        });
        html += '<button type="button" class="trd-pg" data-pg="' + (cur + 1) + '"' + (cur >= pages - 1 ? " disabled" : "") + ">다음</button>";
        pager.innerHTML = html;
    }

    function render() {
        var rows = sortRows(filtered());
        var total = rows.length;

        var sumSales = 0, sumStore = 0;
        rows.forEach(function (r) { sumSales += (r.salesAmt || 0); sumStore += (r.storeCnt || 0); });
        statCount.textContent = total.toLocaleString() + "개";
        statSales.textContent = salesBig(sumSales);
        statStore.textContent = sumStore.toLocaleString() + "개";
        countEl.textContent = "총 " + total.toLocaleString() + "개 상권";

        if (!total) { tbody.innerHTML = emptyRow("조건에 맞는 상권이 없습니다."); renderPager(0); return; }

        var pages = Math.ceil(total / PAGE_SIZE);
        if (state.page >= pages) { state.page = pages - 1; }
        if (state.page < 0) { state.page = 0; }
        var start = state.page * PAGE_SIZE;
        tbody.innerHTML = rows.slice(start, start + PAGE_SIZE).map(rowHtml).join("");
        renderPager(pages);
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
        state.quarter = quarter;
        state.page = 0;
        tbody.innerHTML = emptyRow("불러오는 중…");
        api("/api/districts/summary?quarter=" + encodeURIComponent(quarter)).then(function (r) {
            state.all = (r.ok && r.body && r.body.data) ? r.body.data : [];
            meta.textContent = "서울시 상권 · " + qLabel(quarter);
            buildGuOptions();
            render();
        });
        loadHealth(quarter);
    }

    // ── 데이터 품질 점검 ──────────────────────────────────
    function covClass(pct) { return pct >= 99 ? "ok" : (pct >= 90 ? "warn" : "danger"); }
    function healthFact(f, total) {
        var pct = f.coverage != null ? f.coverage : 0;
        var cls = covClass(pct);
        return '<div class="trd-fact">'
            + '<div class="trd-fact-top"><span class="trd-fact-label">' + esc(f.label) + "</span>"
            + '<span class="trd-cov trd-cov-' + cls + '">' + Number(f.districts).toLocaleString() + " / " + Number(total).toLocaleString() + " (" + pct + "%)</span></div>"
            + '<div class="trd-bar"><div class="trd-bar-fill trd-bg-' + cls + '" style="width:' + Math.min(pct, 100) + '%"></div></div>'
            + '<div class="trd-fact-rows">' + Number(f.rows).toLocaleString() + "행</div></div>";
    }
    function flagChip(label, n, danger) {
        var cls = n > 0 ? (danger ? "danger" : "warn") : "ok";
        return '<span class="trd-flag trd-flag-' + cls + '">' + esc(label) + " <b>" + Number(n).toLocaleString() + "</b></span>";
    }
    function renderHealth(h) {
        healthQ.textContent = qLabel(h.quarter);
        healthFacts.innerHTML = h.facts.map(function (f) { return healthFact(f, h.totalDistricts); }).join("");
        var fl = h.flags;
        healthFlags.innerHTML = flagChip("고아 상권", fl.orphanDistricts, true)
            + flagChip("매출 0", fl.zeroSales, false)
            + flagChip("점포 0", fl.zeroStore, false)
            + flagChip("유동 0", fl.zeroFlpop, false);
    }
    function loadHealth(quarter) {
        healthFacts.innerHTML = '<span class="trd-muted">불러오는 중…</span>';
        healthFlags.innerHTML = "";
        api("/api/admin/trdar/health?quarter=" + encodeURIComponent(quarter)).then(function (r) {
            if (r.status === 401) { return; }
            if (r.ok && r.body && r.body.data) { renderHealth(r.body.data); }
            else { healthFacts.innerHTML = '<span class="trd-muted">품질 정보를 불러오지 못했습니다.</span>'; }
        }, function () { healthFacts.innerHTML = '<span class="trd-muted">품질 정보를 불러오지 못했습니다.</span>'; });
    }

    // ── 상권 상세 모달 ────────────────────────────────────
    function metricTile(label, val, unit) {
        return '<div class="trd-metric"><div class="trd-metric-label">' + esc(label) + "</div>"
            + '<div class="trd-metric-val">' + (val == null ? "—" : val)
            + (unit ? '<span class="trd-metric-unit"> ' + esc(unit) + "</span>" : "") + "</div></div>";
    }
    function renderMetrics(m) {
        metricsEl.innerHTML =
            metricTile("추정매출", m.salesAmt == null ? null : salesBig(m.salesAmt), "")
            + metricTile("점포 수", num(m.storeCnt), "개")
            + metricTile("개업", num(m.openCnt), "개")
            + metricTile("폐업", num(m.closeCnt), "개")
            + metricTile("프랜차이즈", num(m.frcCnt), "개")
            + metricTile("유동인구", num(m.flpop), "명")
            + metricTile("상주인구", num(m.residentPop), "명")
            + metricTile("상권 변화", esc(m.changeIxNm || "—"), "");
    }
    function renderInduty(list) {
        if (!list || !list.length) { indutyEl.innerHTML = '<span class="trd-muted">업종 매출 데이터가 없습니다.</span>'; return; }
        var max = Math.max.apply(null, list.map(function (x) { return x.amt || 0; })) || 1;
        indutyEl.innerHTML = list.map(function (x) {
            var w = Math.max(2, Math.round((x.amt || 0) / max * 100));
            return '<div class="trd-ind-row"><span class="trd-ind-nm">' + esc(x.indutyNm || x.indutyCd) + "</span>"
                + '<span class="trd-ind-bar"><span class="trd-ind-fill" style="width:' + w + '%"></span></span>'
                + '<span class="trd-ind-amt">' + salesRow(x.amt) + "</span></div>";
        }).join("");
    }
    function renderTrend(list) {
        if (!list || !list.length) { trendEl.innerHTML = '<span class="trd-muted">추이 데이터가 없습니다.</span>'; return; }
        var max = Math.max.apply(null, list.map(function (x) { return x.salesAmt || 0; })) || 1;
        trendEl.innerHTML = list.map(function (x) {
            var h = Math.max(3, Math.round((x.salesAmt || 0) / max * 100));
            var title = qLabel(x.quarter) + " · 매출 " + salesRow(x.salesAmt) + " · 점포 " + num(x.storeCnt) + " · 유동 " + num(x.flpop);
            return '<div class="trd-trend-col" title="' + esc(title) + '">'
                + '<div class="trd-trend-track"><div class="trd-trend-bar" style="height:' + h + '%"></div></div>'
                + '<div class="trd-trend-x">' + esc(qShort(x.quarter)) + "</div></div>";
        }).join("");
    }
    function openDetail(cd) {
        if (!cd) { return; }
        modal.hidden = false;
        modalTitle.textContent = "불러오는 중…";
        modalSub.textContent = "";
        metricsEl.innerHTML = "";
        indutyEl.innerHTML = "";
        trendEl.innerHTML = "";
        api("/api/admin/trdar/" + encodeURIComponent(cd) + "?quarter=" + encodeURIComponent(state.quarter || "")).then(function (r) {
            if (!(r.ok && r.body && r.body.data)) { modalTitle.textContent = "상세를 불러오지 못했습니다."; return; }
            var d = r.body.data;
            modalTitle.textContent = d.trdarNm;
            modalSub.textContent = (d.signguNm || "") + " · " + d.trdarCd + " · " + qLabel(d.quarter);
            renderMetrics(d.metrics);
            renderInduty(d.topInduty);
            renderTrend(d.trend);
        });
    }
    function closeModal() { modal.hidden = true; }

    // ── 이벤트 ────────────────────────────────────────────
    quarterSel.addEventListener("change", function () { loadSummary(quarterSel.value); });
    guSel.addEventListener("change", function () { state.page = 0; render(); });
    keywordInput.addEventListener("input", function () { state.page = 0; render(); });
    Array.prototype.forEach.call(document.querySelectorAll(".trd-sort"), function (th) {
        th.addEventListener("click", function () {
            var key = th.getAttribute("data-sort");
            if (state.sortKey === key) { state.sortDir = state.sortDir === "asc" ? "desc" : "asc"; }
            else { state.sortKey = key; state.sortDir = "desc"; }
            Array.prototype.forEach.call(document.querySelectorAll(".trd-sort"), function (h) {
                if (h.getAttribute("data-sort") === state.sortKey) { h.setAttribute("aria-sort", state.sortDir === "asc" ? "ascending" : "descending"); }
                else { h.removeAttribute("aria-sort"); }
            });
            state.page = 0;
            render();
        });
    });
    if (pager) {
        pager.addEventListener("click", function (e) {
            var b = e.target.closest("[data-pg]");
            if (!b || b.disabled) { return; }
            var p = parseInt(b.getAttribute("data-pg"), 10);
            if (!isNaN(p)) { state.page = p; render(); }
        });
    }
    if (tbody) {
        tbody.addEventListener("click", function (e) {
            var row = e.target.closest(".trd-row");
            if (row) { openDetail(row.getAttribute("data-cd")); }
        });
    }
    if (modal) {
        modal.addEventListener("click", function (e) { if (e.target.closest("[data-close]")) { closeModal(); } });
    }
    document.addEventListener("keydown", function (e) { if (e.key === "Escape" && modal && !modal.hidden) { closeModal(); } });

    api("/api/districts/quarters").then(function (r) {
        var quarters = (r.ok && r.body && r.body.data) ? r.body.data : [];
        if (!quarters.length) { tbody.innerHTML = emptyRow("조회 가능한 분기가 없습니다."); return; }
        quarterSel.innerHTML = quarters.map(function (q) { return '<option value="' + esc(q) + '">' + esc(qLabel(q)) + "</option>"; }).join("");
        quarterSel.value = quarters[0];
        loadSummary(quarters[0]);
    });
})();
