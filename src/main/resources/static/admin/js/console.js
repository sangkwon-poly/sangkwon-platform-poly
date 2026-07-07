(function () {
    "use strict";

    var msg = document.getElementById("msg");
    var setupBox = document.getElementById("otp-setup");
    var actions = document.getElementById("otp-actions");
    var statusEl = document.getElementById("otp-status");

    function showMsg(text, type) { msg.textContent = text; msg.className = "msg " + type; msg.hidden = false; }
    function clearMsg() { msg.hidden = true; }

    // fetch → {ok, status, body}
    function api(path, opts) {
        return fetch(path, opts).then(function (res) {
            return res.json().catch(function () { return null; }).then(function (b) {
                return { ok: res.ok, status: res.status, body: b };
            });
        });
    }

    function toLogin() { window.location.href = "/admin/login.html"; }

    // 초기 로드: 세션 확인 + 관리자 정보 + OTP 상태
    api("/api/admin/auth/me").then(function (me) {
        if (me.status === 401) { toLogin(); return null; }
        if (me.ok && me.body && me.body.data) {
            var d = me.body.data;
            document.getElementById("who").textContent = d.adminName + " · " + d.role;
            document.getElementById("who-sub").textContent = d.loginId + " · " + d.role;
        }
        return api("/api/admin/auth/otp/status").then(function (r) {
            renderStatus(r.ok && r.body && r.body.data ? r.body.data.enabled : false);
        });
    });

    function renderStatus(enabled) {
        setupBox.hidden = true;
        if (enabled) {
            statusEl.textContent = "사용 중";
            statusEl.className = "status on";
            actions.innerHTML = '<button type="button" class="btn btn-login act-btn" id="otp-off">2단계 인증 끄기</button>';
            document.getElementById("otp-off").addEventListener("click", disableOtp);
        } else {
            statusEl.textContent = "미사용";
            statusEl.className = "status off";
            actions.innerHTML = '<button type="button" class="btn btn-sso act-btn" id="otp-on">2단계 인증 켜기</button>';
            document.getElementById("otp-on").addEventListener("click", startSetup);
        }
    }

    function startSetup() {
        clearMsg();
        api("/api/admin/auth/otp/setup", { method: "POST" }).then(function (r) {
            if (r.status === 401) { toLogin(); return; }
            if (!r.ok) { showMsg((r.body && r.body.message) || "설정을 시작하지 못했습니다.", "error"); return; }
            document.getElementById("otp-secret").textContent = r.body.data.secret;
            document.getElementById("otp-qr").src = "/api/admin/auth/otp/qr?t=" + Date.now();
            actions.innerHTML = "";
            setupBox.hidden = false;
            var code = document.getElementById("otp-code");
            code.value = "";
            code.focus();
        });
    }

    document.getElementById("otp-confirm").addEventListener("click", function () {
        clearMsg();
        var code = document.getElementById("otp-code").value.trim();
        if (code.length !== 6) { showMsg("앱에 표시된 6자리 코드를 입력하세요.", "error"); return; }
        api("/api/admin/auth/otp/enable", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ otp: code })
        }).then(function (r) {
            if (!r.ok) { showMsg((r.body && r.body.message) || "코드가 올바르지 않습니다.", "error"); return; }
            showMsg("2단계 인증이 켜졌습니다. 다음 로그인부터 OTP가 필요합니다.", "ok");
            renderStatus(true);
        });
    });

    function disableOtp() {
        clearMsg();
        api("/api/admin/auth/otp/disable", { method: "POST" }).then(function (r) {
            if (!r.ok) { showMsg((r.body && r.body.message) || "해제하지 못했습니다.", "error"); return; }
            showMsg("2단계 인증을 껐습니다.", "ok");
            renderStatus(false);
        });
    }

    document.getElementById("otp-code").addEventListener("input", function (e) {
        e.target.value = e.target.value.replace(/\D/g, "").slice(0, 6);
    });

    document.getElementById("logout").addEventListener("click", function () {
        api("/api/admin/auth/logout", { method: "POST" }).then(toLogin, toLogin);
    });
})();
