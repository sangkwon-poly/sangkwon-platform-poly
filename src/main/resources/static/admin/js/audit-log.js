(function () {
    "use strict";

    var ACTIONS = {
        LOGIN: { label: "로그인", cls: "act-login" },
        ADMIN_CREATE: { label: "관리자 생성", cls: "act-create" },
        ADMIN_ROLE_UPDATE: { label: "역할 변경", cls: "act-update" },
        ADMIN_STATUS_UPDATE: { label: "상태 변경", cls: "act-update" },
        OTP_ENABLE: { label: "2단계 인증 켬", cls: "act-sec" },
        OTP_DISABLE: { label: "2단계 인증 끔", cls: "act-sec" }
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

    api("/api/admin/ops/audit").then(function (r) {
        if (r.status === 401) { return; }
        if (r.status === 403) { tbody.innerHTML = '<tr><td colspan="6" class="ops-empty">SUPER_ADMIN만 볼 수 있습니다.</td></tr>'; return; }
        var rows = (r.ok && r.body && r.body.data) ? r.body.data : [];
        if (!rows.length) {
            tbody.innerHTML = '<tr><td colspan="6" class="ops-empty">기록이 없습니다.</td></tr>';
            return;
        }
        tbody.innerHTML = rows.map(function (a) {
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
        }).join("");
    });
})();
