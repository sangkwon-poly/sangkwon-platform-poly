(function () {
    "use strict";

    var SOURCE_LABEL = { BIZINFO: "기업마당", KSTARTUP: "K-Startup" };
    var STATUS = {
        RECRUITING: { label: "모집중", badge: "badge-ok", dot: "dot-ok" },
        CLOSING: { label: "마감 임박", badge: "badge-danger", dot: "dot-danger" },
        UPCOMING: { label: "예정", badge: "badge-warn", dot: "dot-warn" },
        CLOSED: { label: "마감", badge: "badge-muted", dot: "dot-muted" },
        ALWAYS: { label: "상시", badge: "badge-muted", dot: "dot-muted" }
    };

    function api(path, opts) {
        return fetch(path, opts).then(function (res) {
            return res.json().catch(function () { return null; }).then(function (b) {
                return { ok: res.ok, status: res.status, body: b };
            });
        });
    }
    function jsonOpts(method, body) {
        return { method: method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) };
    }
    function esc(s) { var d = document.createElement("div"); d.textContent = (s == null) ? "" : String(s); return d.innerHTML.replace(/"/g, "&quot;").replace(/'/g, "&#39;"); }
    function msgOf(r, fb) { return (r.body && r.body.message) ? r.body.message : fb; }
    function num(n) { return Number(n || 0).toLocaleString(); }

    function statusMeta(v) { return STATUS[v] || { label: v, badge: "badge-muted", dot: "dot-muted" }; }
    function ddayText(d) { return d === 0 ? "D-DAY" : "D-" + d; }
    function periodText(c) {
        if (c.status === "ALWAYS") { return c.applyPeriodRaw || "상시"; }
        if (c.applyBgngDe && c.applyEndDe) { return c.applyBgngDe + " ~ " + c.applyEndDe.slice(5); }
        if (c.applyEndDe) { return "~ " + c.applyEndDe; }
        return c.applyPeriodRaw || "-";
    }

    var tbody = document.getElementById("sa-tbody");
    var chipsEl = document.getElementById("sa-chips");
    var sourceEl = document.getElementById("sa-source");
    var typeEl = document.getElementById("sa-type");
    var searchEl = document.getElementById("sa-search");
    var pagerEl = document.getElementById("sa-pager");
    var prevBtn = document.getElementById("sa-prev");
    var nextBtn = document.getElementById("sa-next");
    var pageInfo = document.getElementById("sa-page-info");
    var metaEl = document.getElementById("sa-meta");
    var flashEl = document.getElementById("sa-flash");

    var state = { vis: "ALL", source: "", type: "", keyword: "", page: 0, size: 20, resp: null };
    var flashTimer = null, searchTimer = null;

    function flash(msg, isError) {
        flashEl.textContent = msg;
        flashEl.classList.toggle("is-error", !!isError);
        flashEl.hidden = false;
        if (flashTimer) { clearTimeout(flashTimer); }
        flashTimer = setTimeout(function () { flashEl.hidden = true; }, 2600);
    }
    function emptyRow(msg) { return '<tr><td colspan="6" class="sa-empty">' + esc(msg) + "</td></tr>"; }
    function findRow(src, id) {
        var list = (state.resp && state.resp.content) || [];
        for (var i = 0; i < list.length; i++) { if (list[i].sourceCd === src && list[i].programId === id) { return list[i]; } }
        return null;
    }

    function loadCounts() {
        api("/api/admin/support-programs/counts").then(function (r) {
            if (!r.ok || !r.body || !r.body.data) { return; }
            var c = r.body.data;
            document.getElementById("sa-c-total").textContent = num(c.total);
            document.getElementById("sa-c-visible").textContent = num(c.visible);
            document.getElementById("sa-c-hidden").textContent = num(c.hidden);
            document.getElementById("sa-c-bizinfo").textContent = num(c.bizinfo);
            document.getElementById("sa-c-kstartup").textContent = num(c.kstartup);
        });
    }

    function forbidden() {
        tbody.innerHTML = emptyRow("최고관리자(SUPER_ADMIN)만 접근할 수 있습니다.");
        document.querySelector(".sa-toolbar").style.display = "none";
        document.querySelector(".sa-summary").style.display = "none";
        pagerEl.hidden = true;
        metaEl.textContent = "접근 권한이 없습니다.";
    }

    function load() {
        var q = "/api/admin/support-programs?page=" + state.page + "&size=" + state.size;
        if (state.vis !== "ALL") { q += "&visibility=" + state.vis; }
        if (state.source) { q += "&source=" + state.source; }
        if (state.type) { q += "&type=" + state.type; }
        if (state.keyword) { q += "&keyword=" + encodeURIComponent(state.keyword); }
        tbody.innerHTML = emptyRow("불러오는 중…");
        api(q).then(function (r) {
            if (r.status === 401) { return; }
            if (r.status === 403) { forbidden(); return; }
            if (!r.ok || !r.body || !r.body.data) { tbody.innerHTML = emptyRow("목록을 불러오지 못했습니다."); pagerEl.hidden = true; return; }
            state.resp = r.body.data;
            renderTable();
        }, function () { tbody.innerHTML = emptyRow("목록을 불러오지 못했습니다."); pagerEl.hidden = true; });
    }

    function rowHtml(c) {
        var s = statusMeta(c.status);
        var badge = '<span class="badge ' + s.badge + '"><span class="dot ' + s.dot + '" aria-hidden="true"></span>'
            + esc(s.label) + (c.dday != null && (c.status === "CLOSING" || c.status === "RECRUITING") ? " " + ddayText(c.dday) : "") + "</span>";
        var srcBadge = '<span class="sa-src sa-src-' + esc(c.sourceCd) + '">' + esc(SOURCE_LABEL[c.sourceCd] || c.sourceCd) + "</span>";
        var toggle = '<button type="button" class="sa-toggle ' + (c.visible ? "is-on" : "is-off") + '"'
            + ' data-src="' + esc(c.sourceCd) + '" data-id="' + esc(c.programId) + '" data-visible="' + (c.visible ? "1" : "0") + '">'
            + (c.visible ? "노출" : "숨김") + "</button>";
        var titleLink = c.detailUrl
            ? '<a class="sa-title" href="' + esc(c.detailUrl) + '" target="_blank" rel="noopener">' + esc(c.title) + "</a>"
            : '<span class="sa-title">' + esc(c.title) + "</span>";
        return "<tr" + (c.visible ? "" : ' class="sa-hidden-row"') + ">"
            + '<td class="col-center">' + srcBadge + "</td>"
            + '<td class="sa-type">' + esc(c.typeLabel) + "</td>"
            + "<td>" + titleLink + "</td>"
            + '<td class="sa-muted">' + esc(c.region || "-") + "</td>"
            + "<td>" + badge + '<div class="sa-period">' + esc(periodText(c)) + "</div></td>"
            + '<td class="col-center">' + toggle + "</td>"
            + "</tr>";
    }

    function renderTable() {
        var resp = state.resp;
        var list = resp.content || [];
        tbody.innerHTML = list.length ? list.map(rowHtml).join("")
            : emptyRow(state.keyword ? "검색 결과가 없습니다." : "표시할 공고가 없습니다.");
        wireRows();
        if (resp.totalPages > 1) {
            pagerEl.hidden = false;
            prevBtn.disabled = resp.page <= 0;
            nextBtn.disabled = resp.page >= resp.totalPages - 1;
            pageInfo.textContent = "페이지 " + (resp.page + 1) + " / " + resp.totalPages;
        } else {
            pagerEl.hidden = true;
        }
        metaEl.textContent = "총 " + num(resp.totalElements) + "건";
    }

    function wireRows() {
        Array.prototype.forEach.call(tbody.querySelectorAll(".sa-toggle"), function (btn) {
            btn.addEventListener("click", function () {
                toggleVisibility(btn.getAttribute("data-src"), btn.getAttribute("data-id"),
                    btn.getAttribute("data-visible") === "1", btn);
            });
        });
    }

    function toggleVisibility(src, id, currentlyVisible, btn) {
        var next = !currentlyVisible;
        btn.disabled = true;
        var url = "/api/admin/support-programs/" + encodeURIComponent(src) + "/" + encodeURIComponent(id) + "/visibility";
        api(url, jsonOpts("PATCH", { visible: next })).then(function (r) {
            btn.disabled = false;
            if (!r.ok) { flash(msgOf(r, "노출 변경에 실패했습니다."), true); return; }
            flash(next ? "노출로 바꿨습니다." : "숨김으로 바꿨습니다.");
            load();
            loadCounts();
        }, function () { btn.disabled = false; flash("노출 변경에 실패했습니다.", true); });
    }

    Array.prototype.forEach.call(chipsEl.querySelectorAll(".sa-chip"), function (chip) {
        chip.addEventListener("click", function () {
            state.vis = chip.getAttribute("data-vis");
            state.page = 0;
            Array.prototype.forEach.call(chipsEl.querySelectorAll(".sa-chip"), function (c) {
                c.classList.toggle("is-active", c === chip);
            });
            load();
        });
    });
    sourceEl.addEventListener("change", function () { state.source = sourceEl.value; state.page = 0; load(); });
    typeEl.addEventListener("change", function () { state.type = typeEl.value; state.page = 0; load(); });
    searchEl.addEventListener("input", function () {
        if (searchTimer) { clearTimeout(searchTimer); }
        searchTimer = setTimeout(function () { state.keyword = searchEl.value.trim(); state.page = 0; load(); }, 300);
    });
    prevBtn.addEventListener("click", function () { if (state.page > 0) { state.page--; load(); } });
    nextBtn.addEventListener("click", function () { if (state.resp && state.page < state.resp.totalPages - 1) { state.page++; load(); } });

    load();
    loadCounts();
})();
