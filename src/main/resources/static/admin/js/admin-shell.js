(function () {
    "use strict";

    function api(path, opts) {
        return fetch(path, opts).then(function (res) {
            return res.json().catch(function () { return null; }).then(function (b) {
                return { ok: res.ok, status: res.status, body: b };
            });
        });
    }
    function toLogin() { window.location.href = "/admin/login"; }

    // 세션 확인 → 비로그인이면 로그인으로, 로그인이면 사이드바에 실제 관리자 정보 표시
    api("/api/admin/auth/me").then(function (me) {
        if (me.status === 401 || !(me.ok && me.body && me.body.data)) { toLogin(); return; }
        var d = me.body.data;

        var nameEl = document.querySelector(".admin-user-name");
        var roleEl = document.querySelector(".admin-user-role");
        var avaEl = document.querySelector(".admin-user-avatar");
        if (nameEl) { nameEl.textContent = d.adminName; }
        if (roleEl) { roleEl.textContent = d.role; }
        if (avaEl) { avaEl.textContent = (d.adminName || "?").charAt(0); }

        // 메뉴는 각 페이지 정적 마크업(그룹 포함)으로 통일했다. 여기서는 계정 영역만 붙인다.
        // 사이드바 하단에 "내 계정·보안"과 "로그아웃" 추가
        var out = document.querySelector(".admin-nav-out");
        if (out) {
            var acc = document.createElement("a");
            acc.href = "/admin/dashboard";
            acc.textContent = "내 계정 · 보안";
            out.insertBefore(acc, out.firstChild);

            var logout = document.createElement("a");
            logout.href = "#";
            logout.textContent = "로그아웃";
            logout.addEventListener("click", function (e) {
                e.preventDefault();
                api("/api/admin/auth/logout", { method: "POST" }).then(toLogin, toLogin);
            });
            out.appendChild(logout);
        }
    });
})();
