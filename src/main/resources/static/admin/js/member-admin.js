(function () {
    "use strict";

    var STATUSES = [
        { value: "ACTIVE",    label: "활성", badge: "badge-ok",     dot: "dot-ok" },
        { value: "DORMANT",   label: "휴면", badge: "badge-warn",   dot: "dot-warn" },
        { value: "BANNED",    label: "정지", badge: "badge-danger", dot: "dot-danger" },
        { value: "WITHDRAWN", label: "탈퇴", badge: "badge-muted",  dot: "dot-muted" }
    ];
    function statusMeta(v) {
        for (var i = 0; i < STATUSES.length; i++) { if (STATUSES[i].value === v) { return STATUSES[i]; } }
        return { value: v, label: v, badge: "badge-muted", dot: "dot-muted" };
    }
    // 필터 칩: 전체 + 상태 4종
    var FILTERS = [{ key: "ALL", label: "전체" }].concat(
        STATUSES.map(function (s) { return { key: s.value, label: s.label }; }));

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
    function msgOf(r, fallback) { return (r.body && r.body.message) ? r.body.message : fallback; }
    function two(n) { return n < 10 ? "0" + n : "" + n; }
    function fmtDateTime(iso) {
        if (!iso) { return "—"; }
        var d = new Date(iso);
        if (isNaN(d.getTime())) { return "—"; }
        return d.getFullYear() + "-" + two(d.getMonth() + 1) + "-" + two(d.getDate())
            + " " + two(d.getHours()) + ":" + two(d.getMinutes());
    }
    function fmtDate(iso) {
        if (!iso) { return "—"; }
        var d = new Date(iso);
        if (isNaN(d.getTime())) { return "—"; }
        return d.getFullYear() + "-" + two(d.getMonth() + 1) + "-" + two(d.getDate());
    }

    var tbody = document.getElementById("ma-tbody");
    var chipsEl = document.getElementById("ma-chips");
    var searchEl = document.getElementById("ma-search");
    var pagerEl = document.getElementById("ma-pager");
    var prevBtn = document.getElementById("ma-prev");
    var nextBtn = document.getElementById("ma-next");
    var pageInfo = document.getElementById("ma-page-info");
    var metaEl = document.getElementById("ma-meta");
    var flashEl = document.getElementById("ma-flash");

    var state = { filter: "ALL", keyword: "", page: 0, size: 20, resp: null, counts: null };
    var flashTimer = null;
    var searchTimer = null;

    function flash(msg, isError) {
        flashEl.textContent = msg;
        flashEl.classList.toggle("is-error", !!isError);
        flashEl.hidden = false;
        if (flashTimer) { clearTimeout(flashTimer); }
        flashTimer = setTimeout(function () { flashEl.hidden = true; }, 2600);
    }
    function emptyRow(msg) { return '<tr><td colspan="6" class="ma-empty">' + esc(msg) + "</td></tr>"; }
    function findMember(id) {
        var list = (state.resp && state.resp.content) || [];
        for (var i = 0; i < list.length; i++) { if (list[i].memberId === id) { return list[i]; } }
        return null;
    }

    function buildChips() {
        chipsEl.innerHTML = FILTERS.map(function (f) {
            return '<button type="button" class="ma-chip" data-filter="' + f.key + '">'
                + esc(f.label) + ' <b data-count="' + f.key + '">0</b></button>';
        }).join("");
        Array.prototype.forEach.call(chipsEl.querySelectorAll(".ma-chip"), function (chip) {
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
        Array.prototype.forEach.call(chipsEl.querySelectorAll(".ma-chip"), function (chip) {
            chip.classList.toggle("is-active", chip.getAttribute("data-filter") === state.filter);
        });
    }
    function renderCounts() {
        if (!state.counts) { return; }
        var c = state.counts;
        var map = { ALL: c.total, ACTIVE: c.active, DORMANT: c.dormant, BANNED: c.banned, WITHDRAWN: c.withdrawn };
        Array.prototype.forEach.call(chipsEl.querySelectorAll("b[data-count]"), function (b) {
            var k = b.getAttribute("data-count");
            b.textContent = map[k] != null ? Number(map[k]).toLocaleString() : "0";
        });
    }

    function forbidden() {
        tbody.innerHTML = emptyRow("최고관리자(SUPER_ADMIN)만 접근할 수 있습니다.");
        chipsEl.style.display = "none";
        if (searchEl) { searchEl.parentNode.style.display = "none"; }
        pagerEl.hidden = true;
        metaEl.textContent = "접근 권한이 없습니다.";
    }

    function load() {
        var q = "/api/admin/members?page=" + state.page + "&size=" + state.size;
        if (state.filter !== "ALL") { q += "&status=" + state.filter; }
        if (state.keyword) { q += "&keyword=" + encodeURIComponent(state.keyword); }
        tbody.innerHTML = emptyRow("불러오는 중…");
        api(q).then(function (r) {
            if (r.status === 401) { return; } // 세션 만료는 admin-shell이 로그인으로 보냄
            if (r.status === 403) { forbidden(); return; }
            if (!r.ok || !r.body || !r.body.data) {
                tbody.innerHTML = emptyRow("목록을 불러오지 못했습니다.");
                pagerEl.hidden = true;
                return;
            }
            state.resp = r.body.data;
            renderTable();
        }, function () {
            tbody.innerHTML = emptyRow("목록을 불러오지 못했습니다.");
            pagerEl.hidden = true;
        });
    }
    function loadCounts() {
        api("/api/admin/members/counts").then(function (r) {
            if (r.ok && r.body && r.body.data) { state.counts = r.body.data; renderCounts(); }
        });
    }

    function rowHtml(m) {
        var s = statusMeta(m.status);
        var badge = '<span class="badge ' + s.badge + '"><span class="dot ' + s.dot + '" aria-hidden="true"></span>' + esc(s.label) + "</span>";
        var select = '<select class="ma-status-select" data-id="' + m.memberId + '" aria-label="' + esc(m.nickname) + " 상태 변경" + '">'
            + STATUSES.map(function (o) {
                return '<option value="' + o.value + '"' + (o.value === m.status ? " selected" : "") + ">" + esc(o.label) + "</option>";
            }).join("")
            + "</select>";
        return "<tr>"
            + '<td><div class="ma-user"><span class="ma-avatar" aria-hidden="true">' + esc((m.nickname || "?").charAt(0)) + "</span>"
            + '<span class="ma-id"><span class="ma-name">' + esc(m.nickname) + "</span>"
            + '<span class="ma-login">' + esc(m.loginId) + "</span></span></div></td>"
            + '<td class="ma-email">' + esc(m.email) + "</td>"
            + '<td class="col-center">' + badge + "</td>"
            + '<td class="ma-muted">' + fmtDate(m.createdAt) + "</td>"
            + '<td class="ma-muted">' + fmtDateTime(m.lastLoginAt) + "</td>"
            + '<td class="col-center">' + select + "</td>"
            + "</tr>";
    }

    function renderTable() {
        var resp = state.resp;
        var list = resp.content || [];
        tbody.innerHTML = list.length
            ? list.map(rowHtml).join("")
            : emptyRow(state.keyword ? "검색 결과가 없습니다." : "표시할 회원이 없습니다.");
        wireRows();

        if (resp.totalPages > 1) {
            pagerEl.hidden = false;
            prevBtn.disabled = resp.page <= 0;
            nextBtn.disabled = resp.page >= resp.totalPages - 1;
            pageInfo.textContent = "페이지 " + (resp.page + 1) + " / " + resp.totalPages;
        } else {
            pagerEl.hidden = true;
        }
        metaEl.textContent = "총 " + Number(resp.totalElements).toLocaleString() + "명";
    }

    function wireRows() {
        Array.prototype.forEach.call(tbody.querySelectorAll(".ma-status-select"), function (sel) {
            sel.addEventListener("change", function () {
                changeStatus(Number(sel.getAttribute("data-id")), sel.value, sel);
            });
        });
    }

    function changeStatus(id, status, sel) {
        var m = findMember(id);
        // 강제 탈퇴는 되돌리기 어려우므로 확인
        if (status === "WITHDRAWN" && !window.confirm("이 회원을 강제 탈퇴 처리할까요? 되돌리기 어려운 작업입니다.")) {
            if (m) { sel.value = m.status; }
            return;
        }
        sel.disabled = true;
        api("/api/admin/members/" + id + "/status", jsonOpts("PATCH", { status: status })).then(function (r) {
            sel.disabled = false;
            if (!r.ok) {
                if (m) { sel.value = m.status; }
                flash(msgOf(r, "상태 변경에 실패했습니다."), true);
                return;
            }
            flash("상태를 변경했습니다.");
            load();       // 필터에 안 맞으면 목록에서 빠지도록 재조회
            loadCounts(); // 상태별 카운트 갱신
        }, function () {
            sel.disabled = false;
            if (m) { sel.value = m.status; }
            flash("상태 변경에 실패했습니다.", true);
        });
    }

    prevBtn.addEventListener("click", function () { if (state.page > 0) { state.page--; load(); } });
    nextBtn.addEventListener("click", function () {
        if (state.resp && state.page < state.resp.totalPages - 1) { state.page++; load(); }
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
    loadCounts();
})();
