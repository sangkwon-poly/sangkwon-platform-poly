(function () {
    "use strict";

    var STATUSES = [
        { value: "PENDING", label: "대기", badge: "badge-warn",   dot: "dot-warn" },
        { value: "PAID",    label: "완료", badge: "badge-ok",     dot: "dot-ok" },
        { value: "FAILED",  label: "실패", badge: "badge-danger", dot: "dot-danger" }
    ];
    function statusMeta(v) {
        for (var i = 0; i < STATUSES.length; i++) { if (STATUSES[i].value === v) { return STATUSES[i]; } }
        return { value: v, label: v, badge: "badge-muted", dot: "dot-muted" };
    }
    var FILTERS = [{ key: "ALL", label: "전체" }].concat(
        STATUSES.map(function (s) { return { key: s.value, label: s.label }; }));

    function api(path, opts) {
        return fetch(path, opts).then(function (res) {
            return res.json().catch(function () { return null; }).then(function (b) {
                return { ok: res.ok, status: res.status, body: b };
            });
        });
    }
    function esc(s) { var d = document.createElement("div"); d.textContent = (s == null) ? "" : String(s); return d.innerHTML.replace(/"/g, "&quot;").replace(/'/g, "&#39;"); }
    function num(n) { return Number(n || 0).toLocaleString(); }
    function won(n) { return "₩" + num(n); }
    function two(n) { return n < 10 ? "0" + n : "" + n; }
    function fmtDateTime(iso) {
        if (!iso) { return "—"; }
        var d = new Date(iso);
        if (isNaN(d.getTime())) { return "—"; }
        return d.getFullYear() + "-" + two(d.getMonth() + 1) + "-" + two(d.getDate())
            + " " + two(d.getHours()) + ":" + two(d.getMinutes());
    }

    var tbody = document.getElementById("pa-tbody");
    var chipsEl = document.getElementById("pa-chips");
    var cycleEl = document.getElementById("pa-cycle");
    var searchEl = document.getElementById("pa-search");
    var pagerEl = document.getElementById("pa-pager");
    var pagesEl = document.getElementById("pa-pages");
    var prevBtn = document.getElementById("pa-prev");
    var nextBtn = document.getElementById("pa-next");
    var metaEl = document.getElementById("pa-meta");

    var state = { filter: "ALL", cycle: "", keyword: "", page: 0, size: 20, resp: null, summary: null };
    var searchTimer = null;
    // 요청 순번. 필터 조작이 겹칠 때 늦게 온 낡은 응답이 최신 화면을 덮지 않게 한다.
    var reqSeq = 0;

    function emptyRow(msg) { return '<tr><td colspan="7" class="pa-empty">' + esc(msg) + "</td></tr>"; }
    function set(id, text) { var el = document.getElementById(id); if (el) { el.textContent = text; } }
    function setBusy(on) {
        tbody.classList.toggle("is-busy", on);
        tbody.setAttribute("aria-busy", on ? "true" : "false");
    }

    function buildChips() {
        chipsEl.innerHTML = FILTERS.map(function (f) {
            return '<button type="button" class="pa-chip" data-filter="' + f.key + '" aria-pressed="false">'
                + esc(f.label) + ' <b data-count="' + f.key + '">0</b></button>';
        }).join("");
        Array.prototype.forEach.call(chipsEl.querySelectorAll(".pa-chip"), function (chip) {
            chip.addEventListener("click", function () {
                state.filter = chip.getAttribute("data-filter");
                state.page = 0;
                renderChipsActive();
                load();
            });
        });
        renderChipsActive();
    }
    function renderChipsActive() {
        Array.prototype.forEach.call(chipsEl.querySelectorAll(".pa-chip"), function (chip) {
            var on = chip.getAttribute("data-filter") === state.filter;
            chip.classList.toggle("is-active", on);
            chip.setAttribute("aria-pressed", on ? "true" : "false");
        });
    }
    function renderSummary() {
        var d = state.summary;
        if (!d) { return; }
        set("pa-stat-revenue", won(d.monthRevenue));
        set("pa-stat-paid", num(d.monthPaidCount) + "건");
        set("pa-stat-pro", num(d.activeProCount) + "명");
        set("pa-stat-failed", num(d.failedCount) + "건");
        var subFail = document.getElementById("pa-sub-failed");
        if (subFail) { subFail.textContent = "결제 대기 " + num(d.pendingCount) + "건"; }
        var map = { ALL: d.totalCount, PENDING: d.pendingCount, PAID: d.paidCount, FAILED: d.failedCount };
        Array.prototype.forEach.call(chipsEl.querySelectorAll("b[data-count]"), function (b) {
            var k = b.getAttribute("data-count");
            b.textContent = map[k] != null ? num(map[k]) : "0";
        });
    }

    function forbidden() {
        tbody.innerHTML = emptyRow("최고관리자(SUPER_ADMIN)만 접근할 수 있습니다.");
        chipsEl.style.display = "none";
        cycleEl.style.display = "none";
        if (searchEl) { searchEl.parentNode.style.display = "none"; }
        pagerEl.hidden = true;
        metaEl.textContent = "접근 권한이 없습니다.";
    }

    function load() {
        var seq = ++reqSeq;
        var q = "/api/admin/payments?page=" + state.page + "&size=" + state.size;
        if (state.filter !== "ALL") { q += "&status=" + state.filter; }
        if (state.cycle) { q += "&cycle=" + state.cycle; }
        if (state.keyword) { q += "&keyword=" + encodeURIComponent(state.keyword); }

        // 첫 로딩만 로딩 문구, 이후 갱신은 기존 행을 흐리게만 해서 깜빡임을 없앤다.
        if (state.resp == null) { tbody.innerHTML = emptyRow("불러오는 중…"); }
        else { setBusy(true); }

        api(q).then(function (r) {
            if (seq !== reqSeq) { return; }
            setBusy(false);
            if (r.status === 401) { return; } // 세션 만료는 admin-shell이 로그인으로 보냄
            if (r.status === 403) { forbidden(); return; }
            if (!r.ok || !r.body || !r.body.data) {
                tbody.innerHTML = emptyRow("목록을 불러오지 못했습니다.");
                pagerEl.hidden = true;
                return;
            }
            state.resp = r.body.data;
            // 상태 필터 뒤 페이지를 보던 중 건수가 줄면 빈 페이지에 갇힌다. 마지막 유효 페이지로 당겨 재조회.
            if (state.resp.content.length === 0 && state.resp.page > 0) {
                state.page = Math.max(0, state.resp.totalPages - 1);
                load();
                return;
            }
            renderTable();
        }, function () {
            if (seq !== reqSeq) { return; }
            setBusy(false);
            tbody.innerHTML = emptyRow("목록을 불러오지 못했습니다.");
            pagerEl.hidden = true;
        });
    }
    function loadSummary() {
        api("/api/admin/payments/summary").then(function (r) {
            if (r.ok && r.body && r.body.data) { state.summary = r.body.data; renderSummary(); }
        });
    }

    function memberHtml(o) {
        // 회원이 하드삭제된 주문은 표시명이 없다. 결제 기록 자체는 남겨 보여준다.
        if (o.memberId == null) { return '<span class="pa-orphan">탈퇴 회원</span>'; }
        return '<div class="pa-member"><span class="pa-name">' + esc(o.nickname || "—") + "</span>"
            + '<span class="pa-login">' + esc(o.loginId || "—") + "</span></div>";
    }

    function rowHtml(o) {
        var s = statusMeta(o.status);
        var badge = '<span class="badge ' + s.badge + '"><span class="dot ' + s.dot + '" aria-hidden="true"></span>' + esc(s.label) + "</span>";
        return "<tr>"
            + '<td><span class="pa-oid" title="' + esc(o.orderId) + '">' + esc((o.orderId || "").slice(0, 8)) + "</span></td>"
            + "<td>" + memberHtml(o) + "</td>"
            + '<td class="pa-product">' + esc(o.orderName) + "</td>"
            + '<td class="pa-amount">' + won(o.amount) + "</td>"
            + '<td class="col-center">' + badge + "</td>"
            + '<td class="pa-muted">' + fmtDateTime(o.createdAt) + "</td>"
            + '<td class="pa-muted">' + fmtDateTime(o.approvedAt) + "</td>"
            + "</tr>";
    }

    function renderTable() {
        var resp = state.resp;
        var list = resp.content || [];
        tbody.innerHTML = list.length
            ? list.map(rowHtml).join("")
            : emptyRow(state.keyword ? "검색 결과가 없습니다." : "표시할 주문이 없습니다.");
        renderPager(resp);
        metaEl.textContent = "총 " + num(resp.totalElements) + "건";
    }

    // 번호 페이징: 현재 페이지 주변 창 + 처음/끝 + 생략(...)
    function pageWindow(cur, total) {
        var out = [], i, from = Math.max(1, cur - 1), to = Math.min(total, cur + 3);
        out.push(1);
        if (from > 2) { out.push("gap"); }
        for (i = Math.max(2, from); i <= Math.min(total - 1, to); i++) { out.push(i); }
        if (to < total - 1) { out.push("gap"); }
        if (total > 1) { out.push(total); }
        return out;
    }
    function renderPager(resp) {
        var total = resp.totalPages;
        if (total <= 1) { pagerEl.hidden = true; return; }
        pagerEl.hidden = false;
        prevBtn.disabled = resp.page <= 0;
        nextBtn.disabled = resp.page >= total - 1;
        pagesEl.innerHTML = pageWindow(resp.page + 1, total).map(function (it) {
            if (it === "gap") { return '<span class="pa-page-gap">…</span>'; }
            var active = (it === resp.page + 1) ? " is-active" : "";
            return '<button type="button" class="pa-page-num' + active + '" data-page="' + (it - 1) + '">' + it + "</button>";
        }).join("");
        Array.prototype.forEach.call(pagesEl.querySelectorAll(".pa-page-num"), function (btn) {
            btn.addEventListener("click", function () {
                var p = Number(btn.getAttribute("data-page"));
                if (p !== state.page) { state.page = p; load(); }
            });
        });
    }

    prevBtn.addEventListener("click", function () { if (state.page > 0) { state.page--; load(); } });
    nextBtn.addEventListener("click", function () {
        if (state.resp && state.page < state.resp.totalPages - 1) { state.page++; load(); }
    });
    cycleEl.addEventListener("change", function () {
        state.cycle = cycleEl.value;
        state.page = 0;
        load();
    });
    if (searchEl) {
        searchEl.addEventListener("input", function () {
            if (searchTimer) { clearTimeout(searchTimer); }
            searchTimer = setTimeout(function () {
                state.keyword = searchEl.value.trim();
                state.page = 0;
                load();
            }, 300);
        });
    }

    buildChips();
    load();
    loadSummary();
})();
