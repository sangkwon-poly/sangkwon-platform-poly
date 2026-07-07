(function () {
    "use strict";

    var form = document.getElementById("login-form");
    var loginBtn = document.getElementById("login-btn");
    var msg = document.getElementById("msg");
    var pw = document.getElementById("password");
    var pwToggle = document.getElementById("pw-toggle");
    var otpStep = document.getElementById("otp-step");
    var otpInputs = Array.prototype.slice.call(document.querySelectorAll(".otp-box"));

    function showMsg(text, type) { msg.textContent = text; msg.className = "msg " + type; msg.hidden = false; }
    function clearMsg() { msg.hidden = true; msg.textContent = ""; }
    function baseLabel() { return otpStep.hidden ? "관리자 로그인" : "인증 후 로그인"; }

    // 비밀번호 표시 토글
    pwToggle.addEventListener("click", function () {
        var show = pw.type === "password";
        pw.type = show ? "text" : "password";
        pwToggle.textContent = show ? "숨김" : "표시";
        pw.focus();
    });

    // OTP 6칸: 입력 시 다음 칸, 백스페이스로 이전 칸, 붙여넣기 분배
    otpInputs.forEach(function (box, i) {
        box.addEventListener("input", function () {
            box.value = box.value.replace(/\D/g, "").slice(0, 1);
            box.classList.toggle("filled", box.value !== "");
            if (box.value && i < otpInputs.length - 1) { otpInputs[i + 1].focus(); }
        });
        box.addEventListener("keydown", function (e) {
            if (e.key === "Backspace" && !box.value && i > 0) { otpInputs[i - 1].focus(); }
        });
        box.addEventListener("paste", function (e) {
            e.preventDefault();
            var digits = (e.clipboardData || window.clipboardData).getData("text").replace(/\D/g, "");
            for (var j = 0; j < otpInputs.length; j++) {
                otpInputs[j].value = digits[j] || "";
                otpInputs[j].classList.toggle("filled", !!digits[j]);
            }
            otpInputs[Math.min(digits.length, otpInputs.length - 1)].focus();
        });
    });

    function otpCode() { return otpInputs.map(function (b) { return b.value; }).join(""); }

    function revealOtpStep() {
        otpStep.hidden = false;
        loginBtn.textContent = "인증 후 로그인";
        otpInputs[0].focus();
    }

    form.addEventListener("submit", function (e) {
        e.preventDefault();
        clearMsg();

        var loginId = document.getElementById("loginId").value.trim();
        var password = pw.value;
        if (!loginId || !password) { showMsg("사번·이메일과 비밀번호를 입력하세요.", "error"); return; }

        var otp = otpStep.hidden ? "" : otpCode();
        if (!otpStep.hidden && otp.length !== 6) { showMsg("6자리 OTP 코드를 입력하세요.", "error"); return; }

        loginBtn.disabled = true;
        loginBtn.textContent = "확인 중...";

        fetch("/api/admin/auth/login", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ loginId: loginId, password: password, otp: otp })
        })
            .then(function (res) {
                return res.json().then(function (b) { return { ok: res.ok, body: b }; })
                    .catch(function () { return { ok: res.ok, body: null }; });
            })
            .then(function (r) {
                if (r.ok) {
                    var name = r.body && r.body.data ? r.body.data.adminName : "";
                    showMsg((name ? name + "님, " : "") + "로그인 성공. 대시보드로 이동합니다.", "ok");
                    setTimeout(function () { window.location.href = "/admin/index.html"; }, 700);
                } else if (r.body && r.body.code === "OTP_REQUIRED") {
                    revealOtpStep();
                    showMsg("인증 앱에 표시된 6자리 코드를 입력하세요.", "info");
                } else {
                    showMsg((r.body && r.body.message) || "로그인에 실패했습니다.", "error");
                }
            })
            .catch(function () { showMsg("서버에 연결할 수 없습니다.", "error"); })
            .finally(function () {
                loginBtn.disabled = false;
                if (loginBtn.textContent === "확인 중...") { loginBtn.textContent = baseLabel(); }
            });
    });

    // 아직 백엔드가 없는 기능은 안내만
    document.getElementById("reset-btn").addEventListener("click", function () {
        showMsg("비밀번호 재설정은 정보보안팀에 문의해 주세요. (셀프 재설정 준비 중)", "error");
    });
})();
