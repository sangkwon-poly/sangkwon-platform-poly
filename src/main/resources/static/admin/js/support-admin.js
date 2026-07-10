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
    var visEl = document.getElementById("sa-visibility");
    var sourceEl = document.getElementById("sa-source");
    var typeEl = document.getElementById("sa-type");
    var searchEl = document.getElementById("sa-search");
    var pagerEl = document.getElementById("sa-pager");
    var prevBtn = document.getElementById("sa-prev");
    var nextBtn = document.getElementById("sa-next");
    var pagesEl = document.getElementById("sa-pages");
    var metaEl = document.getElementById("sa-meta");
    var flashEl = document.getElementById("sa-flash");

    var state = { status: "OPEN", vis: "", source: "", type: "", keyword: "", page: 0, size: 20, resp: null };
    var flashTimer = null, searchTimer = null;

    function flash(msg, isError) {
        flashEl.textContent = msg;
        flashEl.classList.toggle("is-error", !!isError);
        flashEl.hidden = false;
        if (flashTimer) { clearTimeout(flashTimer); }
        flashTimer = setTimeout(function () { flashEl.hidden = true; }, 2600);
    }
    function emptyRow(msg) { return '<tr><td colspan="6" class="sa-empty">' + esc(msg) + "</td></tr>"; }

    function loadCounts() {
        api("/api/admin/support-programs/counts").then(function (r) {
            if (!r.ok || !r.body || !r.body.data) { return; }
            var c = r.body.data;
            document.getElementById("sa-c-total").textContent = num(c.total);
            document.getElementById("sa-c-open").textContent = num(c.open);
            document.getElementById("sa-c-closed").textContent = num(c.closed);
            document.getElementById("sa-c-visible").textContent = num(c.visible);
            document.getElementById("sa-c-hidden").textContent = num(c.hidden);
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
        if (state.status !== "ALL") { q += "&status=" + state.status; }
        if (state.vis) { q += "&visibility=" + state.vis; }
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

    function statusCell(c) {
        var s = statusMeta(c.status);
        var badge = '<span class="badge ' + s.badge + '"><span class="dot ' + s.dot + '" aria-hidden="true"></span>' + esc(s.label) + "</span>";
        var dday = "";
        if (c.dday != null && c.status === "CLOSING") { dday = '<span class="sa-dday sa-dday-danger">' + ddayText(c.dday) + "</span>"; }
        else if (c.dday != null && c.status === "RECRUITING") { dday = '<span class="sa-dday">' + ddayText(c.dday) + "</span>"; }
        return '<div class="sa-status">' + badge + dday + "</div>"
            + '<div class="sa-period">' + esc(periodText(c)) + "</div>";
    }

    function rowHtml(c) {
        var srcBadge = '<span class="sa-src sa-src-' + esc(c.sourceCd) + '">' + esc(SOURCE_LABEL[c.sourceCd] || c.sourceCd) + "</span>";
        var toggle = '<button type="button" class="sa-toggle ' + (c.visible ? "is-on" : "is-off") + '"'
            + ' data-src="' + esc(c.sourceCd) + '" data-id="' + esc(c.programId) + '" data-visible="' + (c.visible ? "1" : "0") + '">'
            + (c.visible ? "노출" : "숨김") + "</button>";
        var detailHref = "/admin/support-detail?source=" + encodeURIComponent(c.sourceCd) + "&id=" + encodeURIComponent(c.programId);
        var titleLink = '<a class="sa-title" href="' + detailHref + '">' + esc(c.title) + "</a>";
        var cls = [];
        if (!c.visible) { cls.push("sa-hidden-row"); }
        if (c.status === "CLOSED") { cls.push("sa-closed-row"); }
        return "<tr" + (cls.length ? ' class="' + cls.join(" ") + '"' : "") + ">"
            + '<td class="col-center">' + srcBadge + "</td>"
            + '<td class="sa-type">' + esc(c.typeLabel) + "</td>"
            + "<td>" + titleLink + "</td>"
            + '<td class="sa-muted">' + esc(c.region || "-") + "</td>"
            + "<td>" + statusCell(c) + "</td>"
            + '<td class="col-center">' + toggle + "</td>"
            + "</tr>";
    }

    function renderTable() {
        var resp = state.resp;
        var list = resp.content || [];
        tbody.innerHTML = list.length ? list.map(rowHtml).join("")
            : emptyRow(state.keyword ? "검색 결과가 없습니다." : "조건에 맞는 공고가 없습니다.");
        wireRows();
        if (resp.totalPages > 1) {
            pagerEl.hidden = false;
            prevBtn.disabled = resp.page <= 0;
            nextBtn.disabled = resp.page >= resp.totalPages - 1;
            renderPages(resp.page, resp.totalPages);
        } else {
            pagerEl.hidden = true;
        }
        metaEl.textContent = "조건에 맞는 공고 " + num(resp.totalElements) + "건";
    }

    // 번호 페이징: 현재 페이지 주변 창 + 처음/끝 + 생략(...) 표시
    function pageWindow(cur, total) {
        var out = [], i;
        var from = Math.max(1, cur - 1), to = Math.min(total, cur + 3);
        out.push(1);
        if (from > 2) { out.push("gap"); }
        for (i = Math.max(2, from); i <= Math.min(total - 1, to); i++) { out.push(i); }
        if (to < total - 1) { out.push("gap"); }
        if (total > 1) { out.push(total); }
        return out;
    }

    function renderPages(page, totalPages) {
        var items = pageWindow(page + 1, totalPages);
        pagesEl.innerHTML = items.map(function (it) {
            if (it === "gap") { return '<span class="sa-page-gap">…</span>'; }
            var active = (it === page + 1) ? " is-active" : "";
            return '<button type="button" class="sa-page-num' + active + '" data-page="' + (it - 1) + '">' + it + "</button>";
        }).join("");
        Array.prototype.forEach.call(pagesEl.querySelectorAll(".sa-page-num"), function (btn) {
            btn.addEventListener("click", function () {
                var p = Number(btn.getAttribute("data-page"));
                if (p !== state.page) { state.page = p; load(); }
            });
        });
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
            state.status = chip.getAttribute("data-status");
            state.page = 0;
            Array.prototype.forEach.call(chipsEl.querySelectorAll(".sa-chip"), function (c) {
                c.classList.toggle("is-active", c === chip);
            });
            load();
        });
    });
    visEl.addEventListener("change", function () { state.vis = visEl.value; state.page = 0; load(); });
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
