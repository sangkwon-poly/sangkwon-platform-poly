(function () {
    "use strict";

    var ACTIONS = {
        LOGIN:                { label: "로그인", cls: "act-login" },
        LOGIN_FAILED:         { label: "로그인 실패", cls: "act-danger" },
        LOGOUT:               { label: "로그아웃", cls: "act-login" },
        ACCOUNT_LOCKED:       { label: "계정 잠금", cls: "act-danger" },
        ADMIN_CREATE:         { label: "관리자 생성", cls: "act-create" },
        ADMIN_ROLE_UPDATE:    { label: "역할 변경", cls: "act-update" },
        ADMIN_STATUS_UPDATE:  { label: "관리자 상태 변경", cls: "act-update" },
        MEMBER_STATUS_UPDATE: { label: "회원 상태 변경", cls: "act-update" },
        PASSWORD_RESET:       { label: "비밀번호 재설정", cls: "act-sec" },
        OTP_ENABLE:           { label: "2단계 인증 켬", cls: "act-sec" },
        OTP_DISABLE:          { label: "2단계 인증 끔", cls: "act-sec" }
    };

    function api(path) {
        return fetch(path).then(function (r) {
            return r.json().catch(function () { return null; }).then(function (b) {
                return { ok: r.ok, status: r.status, body: b };
            });
        });
    }
    function esc(s) { var d = document.createElement("div"); d.textContent = (s == null) ? "" : String(s); return d.innerHTML.replace(/"/g, "&quot;").replace(/'/g, "&#39;"); }
    function pad(n) { return String(n).padStart(2, "0"); }
    function stamp(iso) {
        if (!iso) { return "-"; }
        var d = new Date(iso);
        return (d.getMonth() + 1) + "/" + d.getDate() + " " + pad(d.getHours()) + ":" + pad(d.getMinutes());
    }

    var tbody = document.querySelector(".audit-table tbody");
    var actionSel = document.getElementById("ag-action");
    var pagerEl = document.getElementById("ag-pager");
    var prevBtn = document.getElementById("ag-prev");
    var nextBtn = document.getElementById("ag-next");
    var pageInfo = document.getElementById("ag-page-info");

    var state = { action: "", page: 0, size: 30, resp: null };

    function emptyRow(msg) { return '<tr><td colspan="6" class="ops-empty">' + esc(msg) + "</td></tr>"; }

    function rowHtml(a) {
        var act = ACTIONS[a.action] || { label: a.action, cls: "act-update" };
        var target = a.targetType ? (esc(a.targetType) + (a.targetId ? " #" + esc(a.targetId) : "")) : "-";
        return "<tr>"
            + '<td class="audit-time mono">' + stamp(a.createdAt) + "</td>"
            + '<td class="audit-admin mono">' + esc(a.adminLoginId) + "</td>"
            + '<td><span class="audit-act ' + act.cls + '">' + esc(act.label) + "</span></td>"
            + '<td class="audit-muted">' + target + "</td>"
            + '<td class="audit-muted">' + esc(a.detail || "-") + "</td>"
            + '<td class="mono audit-muted">' + esc(a.ipAddr || "-") + "</td>"
            + "</tr>";
    }

    function render() {
        var resp = state.resp;
        var list = (resp && resp.content) || [];
        tbody.innerHTML = list.length ? list.map(rowHtml).join("") : emptyRow("기록이 없습니다.");
        if (resp && resp.totalPages > 1) {
            pagerEl.hidden = false;
            prevBtn.disabled = resp.page <= 0;
            nextBtn.disabled = resp.page >= resp.totalPages - 1;
            pageInfo.textContent = "페이지 " + (resp.page + 1) + " / " + resp.totalPages
                + " · 총 " + Number(resp.totalElements).toLocaleString() + "건";
        } else {
            pagerEl.hidden = true;
        }
    }

    function load() {
        tbody.innerHTML = emptyRow("불러오는 중…");
        var q = "/api/admin/ops/audit?page=" + state.page + "&size=" + state.size;
        if (state.action) { q += "&action=" + state.action; }
        api(q).then(function (r) {
            if (r.status === 401) { return; }
            if (r.status === 403) { tbody.innerHTML = emptyRow("SUPER_ADMIN만 볼 수 있습니다."); pagerEl.hidden = true; return; }
            if (!r.ok || !r.body || !r.body.data) { tbody.innerHTML = emptyRow("불러오지 못했습니다."); pagerEl.hidden = true; return; }
            state.resp = r.body.data;
            render();
        }, function () {
            tbody.innerHTML = emptyRow("불러오지 못했습니다.");
            pagerEl.hidden = true;
        });
    }

    if (actionSel) {
        actionSel.addEventListener("change", function () {
            state.action = actionSel.value;
            state.page = 0;
            load();
        });
    }
    prevBtn.addEventListener("click", function () { if (state.page > 0) { state.page--; load(); } });
    nextBtn.addEventListener("click", function () {
        if (state.resp && state.page < state.resp.totalPages - 1) { state.page++; load(); }
    });

    load();
})();
