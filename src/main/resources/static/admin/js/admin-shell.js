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

        // 운영 대시보드 메뉴(감사 로그·API 사용)를 공통 주입
        var nav = document.querySelector(".admin-nav");
        var sep = nav ? nav.querySelector(".admin-nav-sep") : null;
        if (nav && sep) {
            // 구형 .html 주소로 들어와도 활성 표시가 맞도록 확장자를 떼고 비교
            var path = window.location.pathname.replace(/\.html$/, "");
            [["/admin/support-admin", "지원사업"], ["/admin/member-admin", "회원 관리"], ["/admin/notice-admin", "공지사항"], ["/admin/inquiry-admin", "1:1 문의"], ["/admin/audit-log", "감사 로그"], ["/admin/api-usage", "API 사용"]].forEach(function (item) {
                if (nav.querySelector('a[href="' + item[0] + '"]')) { return; }
                var link = document.createElement("a");
                link.href = item[0];
                link.textContent = item[1];
                if (path === item[0]) { link.setAttribute("aria-current", "page"); }
                nav.insertBefore(link, sep);
            });
        }

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
