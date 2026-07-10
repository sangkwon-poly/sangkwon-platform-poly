(function () {
    "use strict";

    var ROLES = [
        { value: "SUPER_ADMIN", label: "최고관리자" },
        { value: "OPERATOR", label: "운영자" },
        { value: "VIEWER", label: "뷰어" }
    ];
    function roleLabel(v) {
        for (var i = 0; i < ROLES.length; i++) { if (ROLES[i].value === v) { return ROLES[i].label; } }
        return v;
    }

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

    var tbody = document.getElementById("ua-tbody");
    var filtersEl = document.getElementById("ua-filters");
    var modal = document.getElementById("ua-modal");
    var form = document.getElementById("ua-form");
    var formMsg = document.getElementById("ua-form-msg");
    var addBtn = document.getElementById("ua-add");
    var submitBtn = document.getElementById("ua-submit");
    var fLoginId = form.querySelector('[name="loginId"]');
    var fName = form.querySelector('[name="adminName"]');
    var fRole = form.querySelector('[name="role"]');
    var fPassword = form.querySelector('[name="password"]');

    var state = { me: null, admins: [], filter: "ALL" };

    function emptyRow(msg) { return '<tr><td colspan="7" class="ua-empty">' + esc(msg) + "</td></tr>"; }
    function find(id) { for (var i = 0; i < state.admins.length; i++) { if (state.admins[i].adminId === id) { return state.admins[i]; } } return null; }

    function load() {
        api("/api/admin/admin-users").then(function (r) {
            if (r.status === 401) { return; } // 세션 만료는 admin-shell이 로그인으로 보냄
            if (r.status === 403) {
                tbody.innerHTML = emptyRow("최고관리자(SUPER_ADMIN)만 접근할 수 있습니다.");
                filtersEl.style.display = "none";
                if (addBtn) { addBtn.style.display = "none"; }
                return;
            }
            state.admins = (r.ok && r.body && r.body.data) ? r.body.data : [];
            render();
        });
    }

    function counts() {
        var c = { ALL: state.admins.length, ACTIVE: 0, LOCKED: 0, DISABLED: 0 };
        state.admins.forEach(function (a) { if (c[a.status] != null) { c[a.status]++; } });
        return c;
    }

    // 활성 최고관리자 수. 마지막 1명이면 강등·잠금·비활성 버튼을 막는다(서버도 거부).
    function activeSuperAdminCount() {
        return state.admins.filter(function (a) { return a.role === "SUPER_ADMIN" && a.status === "ACTIVE"; }).length;
    }
    function statusBadge(status) {
        if (status === "ACTIVE") { return '<span class="badge badge-ok"><span class="dot dot-ok" aria-hidden="true"></span>활성</span>'; }
        if (status === "LOCKED") { return '<span class="badge badge-danger"><span class="dot dot-danger" aria-hidden="true"></span>잠김</span>'; }
        return '<span class="badge badge-muted"><span class="dot dot-muted" aria-hidden="true"></span>비활성</span>';
    }

    function render() {
        var c = counts();
        Array.prototype.forEach.call(filtersEl.querySelectorAll(".ua-chip"), function (chip) {
            var f = chip.getAttribute("data-filter");
            var b = chip.querySelector("b");
            if (b) { b.textContent = c[f] != null ? c[f] : 0; }
            chip.classList.toggle("ua-chip-active", f === state.filter);
        });

        var list = state.admins.filter(function (a) { return state.filter === "ALL" || a.status === state.filter; });
        tbody.innerHTML = list.length ? list.map(rowHtml).join("") : emptyRow("표시할 계정이 없습니다.");
        wire();
    }

    function rowHtml(a) {
        var isSelf = state.me && a.adminId === state.me.adminId;
        // 마지막 활성 최고관리자면 강등·잠금·비활성을 UI에서 먼저 막는다(서버도 이중으로 거부)
        var lastSA = a.role === "SUPER_ADMIN" && a.status === "ACTIVE" && activeSuperAdminCount() <= 1;
        var badge = statusBadge(a.status);

        var roleSelect = '<select class="ua-role-select" data-id="' + a.adminId + '"' + (isSelf || lastSA ? " disabled" : "") + ' aria-label="권한 변경">'
            + ROLES.map(function (r) {
                return '<option value="' + r.value + '"' + (r.value === a.role ? " selected" : "") + ">" + esc(r.label) + "</option>";
            }).join("")
            + "</select>";

        var act;
        if (isSelf) {
            act = '<button type="button" class="ua-act" disabled>본인</button>';
        } else {
            var btns = "";
            if (a.status === "ACTIVE") {
                btns += '<button type="button" class="ua-act ua-act-lock" data-act="lock" data-id="' + a.adminId + '"' + (lastSA ? " disabled" : "") + ">잠금</button>";
                btns += '<button type="button" class="ua-act" data-act="disable" data-id="' + a.adminId + '"' + (lastSA ? " disabled" : "") + ">비활성화</button>";
            } else if (a.status === "LOCKED") {
                btns += '<button type="button" class="ua-act" data-act="unlock" data-id="' + a.adminId + '">해제</button>';
                btns += '<button type="button" class="ua-act" data-act="disable" data-id="' + a.adminId + '">비활성화</button>';
            } else {
                btns += '<button type="button" class="ua-act" data-act="enable" data-id="' + a.adminId + '">재활성화</button>';
            }
            btns += '<button type="button" class="ua-act" data-act="reset" data-id="' + a.adminId + '">비번 재설정</button>';
            act = '<div class="ua-actions">' + btns + "</div>";
        }

        var failCls = a.failedLoginCnt >= 3 ? " is-hot" : "";

        return "<tr>"
            + '<td><div class="ua-user"><span class="ua-avatar" aria-hidden="true">' + esc((a.adminName || "?").charAt(0)) + "</span>"
            + '<span class="ua-id"><span class="ua-name">' + esc(a.adminName) + (isSelf ? ' <span class="ua-self">나</span>' : "") + "</span>"
            + '<span class="ua-login">' + esc(a.loginId) + "</span></span></div></td>"
            + "<td>" + roleSelect + "</td>"
            + '<td class="col-center">' + badge + "</td>"
            + '<td class="col-center"><span class="ua-fail' + failCls + '">' + a.failedLoginCnt + "</span></td>"
            + '<td class="ua-muted">' + fmtDateTime(a.lastLoginAt) + "</td>"
            + '<td class="ua-muted">' + fmtDate(a.createdAt) + "</td>"
            + '<td class="col-center">' + act + "</td>"
            + "</tr>";
    }

    function wire() {
        Array.prototype.forEach.call(tbody.querySelectorAll(".ua-role-select"), function (sel) {
            sel.addEventListener("change", function () { changeRole(Number(sel.getAttribute("data-id")), sel.value, sel); });
        });
        Array.prototype.forEach.call(tbody.querySelectorAll(".ua-act[data-act]"), function (btn) {
            var act = btn.getAttribute("data-act");
            btn.addEventListener("click", function () {
                var id = Number(btn.getAttribute("data-id"));
                if (act === "reset") { openReset(id); return; }
                var next = act === "lock" ? "LOCKED" : (act === "disable" ? "DISABLED" : "ACTIVE");
                changeStatus(id, next, btn);
            });
        });
    }

    function changeRole(id, role, sel) {
        var prev = (find(id) || {}).role;
        sel.disabled = true;
        api("/api/admin/admin-users/" + id + "/role", jsonOpts("PATCH", { role: role })).then(function (r) {
            sel.disabled = false;
            if (!r.ok) { alert(msgOf(r, "권한 변경에 실패했습니다.")); sel.value = prev; return; }
            var a = find(id); if (a) { a.role = role; }
        });
    }

    function changeStatus(id, status, btn) {
        btn.disabled = true;
        api("/api/admin/admin-users/" + id + "/status", jsonOpts("PATCH", { status: status })).then(function (r) {
            if (!r.ok) { btn.disabled = false; alert(msgOf(r, "상태 변경에 실패했습니다.")); return; }
            var a = find(id);
            if (a) { a.status = status; if (status === "ACTIVE") { a.failedLoginCnt = 0; } }
            render();
        });
    }

    // 관리자 추가 모달
    function openModal() { form.reset(); hideMsg(); modal.hidden = false; if (fLoginId) { fLoginId.focus(); } }
    function closeModal() { modal.hidden = true; }
    function showMsg(m) { formMsg.textContent = m; formMsg.hidden = false; }
    function hideMsg() { formMsg.textContent = ""; formMsg.hidden = true; }

    if (addBtn) { addBtn.addEventListener("click", openModal); }
    Array.prototype.forEach.call(modal.querySelectorAll("[data-close]"), function (el) { el.addEventListener("click", closeModal); });
    document.addEventListener("keydown", function (e) {
        if (e.key !== "Escape") { return; }
        if (!modal.hidden) { closeModal(); }
        if (!resetModal.hidden) { closeReset(); }
    });

    // 비밀번호 재설정 모달 (최고관리자가 다른 관리자 비번 초기화)
    var resetModal = document.getElementById("ua-reset-modal");
    var resetForm = document.getElementById("ua-reset-form");
    var resetMsg = document.getElementById("ua-reset-msg");
    var resetTarget = document.getElementById("ua-reset-target");
    var resetSubmit = document.getElementById("ua-reset-submit");
    var resetPw = resetForm.querySelector('[name="newPassword"]');
    var resetId = null;

    function openReset(id) {
        var a = find(id);
        resetId = id;
        resetTarget.textContent = (a ? a.adminName + " (" + a.loginId + ")" : "관리자") + " 계정의 새 비밀번호를 설정합니다.";
        resetPw.value = "";
        resetMsg.hidden = true;
        resetModal.hidden = false;
        resetPw.focus();
    }
    function closeReset() { resetModal.hidden = true; resetId = null; }

    Array.prototype.forEach.call(resetModal.querySelectorAll("[data-reset-close]"), function (el) { el.addEventListener("click", closeReset); });

    resetForm.addEventListener("submit", function (e) {
        e.preventDefault();
        resetMsg.hidden = true;
        var pw = resetPw.value;
        if (!pw || pw.length < 4) { resetMsg.textContent = "비밀번호는 4자 이상으로 입력하세요."; resetMsg.hidden = false; return; }
        if (resetId == null) { return; }
        resetSubmit.disabled = true;
        api("/api/admin/admin-users/" + resetId + "/reset-password", jsonOpts("PATCH", { newPassword: pw })).then(function (r) {
            resetSubmit.disabled = false;
            if (!r.ok) { resetMsg.textContent = msgOf(r, "재설정에 실패했습니다."); resetMsg.hidden = false; return; }
            var a = find(resetId);
            if (a) { a.status = "ACTIVE"; a.failedLoginCnt = 0; } // 재설정은 잠금도 해제
            closeReset();
            render();
        });
    });

    form.addEventListener("submit", function (e) {
        e.preventDefault();
        hideMsg();
        var payload = {
            loginId: fLoginId.value.trim(),
            adminName: fName.value.trim(),
            role: fRole.value,
            password: fPassword.value
        };
        if (!payload.loginId || !payload.adminName || !payload.password) { showMsg("모든 항목을 입력하세요."); return; }
        submitBtn.disabled = true;
        api("/api/admin/admin-users", jsonOpts("POST", payload)).then(function (r) {
            submitBtn.disabled = false;
            if (!r.ok) { showMsg(msgOf(r, "추가에 실패했습니다.")); return; }
            closeModal();
            load();
        });
    });

    Array.prototype.forEach.call(filtersEl.querySelectorAll(".ua-chip"), function (chip) {
        chip.addEventListener("click", function () { state.filter = chip.getAttribute("data-filter"); render(); });
    });

    // 현재 로그인 관리자(adminId)를 먼저 확보해 본인 행을 보호한 뒤 목록을 그린다
    api("/api/admin/auth/me").then(function (r) {
        if (r.ok && r.body && r.body.data) { state.me = r.body.data; }
        load();
    });
})();
