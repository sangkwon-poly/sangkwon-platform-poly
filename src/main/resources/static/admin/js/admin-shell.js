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

// 모달 접근성: [role=dialog][aria-modal] 패널이 열리면 포커스를 모달 안에 가두고(Tab 순환), 닫히면
// 열었던 요소로 포커스를 되돌린다. 각 모달의 여닫기 로직은 그대로 두고 hidden 토글만 관찰해 덧붙인다.
// (Escape·백드롭 닫기는 각 페이지가 이미 처리하므로 여기선 Tab 트랩과 포커스 복원만 담당)
(function () {
    "use strict";
    function setup() {
        var panels = document.querySelectorAll('[role="dialog"][aria-modal="true"]');
        Array.prototype.forEach.call(panels, function (panel) {
            var container = panel.parentElement; // hidden 이 토글되는 모달 컨테이너
            if (!container) { return; }
            var trigger = null;
            var open = false;

            function focusables() {
                return panel.querySelectorAll(
                    'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), '
                    + 'textarea:not([disabled]), [tabindex]:not([tabindex="-1"])');
            }
            function onKey(e) {
                if (e.key !== "Tab" || container.hidden) { return; }
                var f = focusables();
                if (!f.length) { return; }
                var first = f[0];
                var last = f[f.length - 1];
                if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
                else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
            }
            function onOpen() {
                if (open) { return; }
                open = true;
                trigger = document.activeElement;
                var f = focusables();
                if (f.length) { f[0].focus(); }
                else { panel.setAttribute("tabindex", "-1"); panel.focus(); }
                document.addEventListener("keydown", onKey, true);
            }
            function onClose() {
                if (!open) { return; }
                open = false;
                document.removeEventListener("keydown", onKey, true);
                if (trigger && typeof trigger.focus === "function") { trigger.focus(); }
            }

            new MutationObserver(function () {
                if (container.hidden) { onClose(); } else { onOpen(); }
            }).observe(container, { attributes: true, attributeFilter: ["hidden"] });

            if (!container.hidden) { onOpen(); } // 이미 열린 채 로드된 경우 대비
        });
    }
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", setup);
    } else {
        setup();
    }
})();
