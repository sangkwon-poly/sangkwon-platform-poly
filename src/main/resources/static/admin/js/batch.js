(function () {
    "use strict";

    function api(path) {
        return fetch(path).then(function (r) {
            return r.json().catch(function () { return null; }).then(function (b) {
                return { ok: r.ok, status: r.status, body: b };
            });
        });
    }
    function esc(s) { var d = document.createElement("div"); d.textContent = (s == null) ? "" : String(s); return d.innerHTML.replace(/"/g, "&quot;").replace(/'/g, "&#39;"); }
    function pad(n) { return String(n).padStart(2, "0"); }
    function hhmm(iso) { if (!iso) { return "-"; } var d = new Date(iso); return pad(d.getHours()) + ":" + pad(d.getMinutes()); }
    function badge(status) {
        var map = {
            SUCCESS: ["badge-ok", "dot-ok", "정상"],
            PARTIAL: ["badge-warn", "dot-warn", "부분"],
            RUNNING: ["badge-warn", "dot-warn", "진행"],
            FAILED: ["badge-danger", "dot-danger", "오류"]
        };
        var m = map[status] || ["badge-warn", "dot-warn", status];
        return '<span class="badge ' + m[0] + '"><span class="dot ' + m[1] + '" aria-hidden="true"></span>' + esc(m[2]) + "</span>";
    }

    var tbody = document.querySelector(".batch-table tbody");
    var statVals = document.querySelectorAll(".batch-stats .stat-card-value");
    var refreshBtn = document.getElementById("batch-refresh");

    function emptyRow(msg) { return '<tr><td colspan="6" class="ops-empty">' + esc(msg) + "</td></tr>"; }
    function setStats(a, b, c, d) {
        if (statVals.length >= 4) {
            statVals[0].textContent = a; statVals[1].textContent = b;
            statVals[2].textContent = c; statVals[3].textContent = d;
        }
    }

    function rowHtml(b) {
        var dur = (b.startedAt && b.endedAt)
            ? Math.max(0, Math.round((new Date(b.endedAt) - new Date(b.startedAt)) / 1000)) + "s"
            : "-";
        return "<tr>"
            + '<td class="mono bt-name">' + esc(b.datasetCd) + "</td>"
            + '<td class="bt-muted">' + hhmm(b.startedAt) + "</td>"
            + '<td class="col-num mono">' + (b.processedCnt != null ? Number(b.processedCnt).toLocaleString() : "-") + "</td>"
            + '<td class="col-num bt-muted">' + dur + "</td>"
            + '<td class="col-center">' + badge(b.status) + "</td>"
            + '<td class="bt-muted">' + esc(b.triggeredBy || "-") + "</td>"
            + "</tr>";
    }

    function render(rows) {
        if (tbody) {
            tbody.innerHTML = rows.length ? rows.map(rowHtml).join("") : emptyRow("적재 이력이 없습니다.");
        }
        var count = function (s) { return rows.filter(function (b) { return b.status === s; }).length; };
        setStats(
            rows.length ? hhmm(rows[0].startedAt) : "—",
            count("SUCCESS"),
            count("PARTIAL") + count("RUNNING"),
            count("FAILED"));
    }

    function fail(msg) {
        if (tbody) { tbody.innerHTML = emptyRow(msg); }
        setStats("—", "—", "—", "—");
    }

    function load() {
        if (tbody) { tbody.innerHTML = emptyRow("불러오는 중…"); }
        if (refreshBtn) { refreshBtn.disabled = true; }
        api("/api/admin/ops/batch").then(function (r) {
            if (refreshBtn) { refreshBtn.disabled = false; }
            if (r.status === 401) { return; } // 세션 만료는 admin-shell이 로그인으로 보냄
            if (r.status === 403) { fail("SUPER_ADMIN만 볼 수 있습니다."); return; }
            if (!r.ok) { fail("적재 이력을 불러오지 못했습니다."); return; }
            render((r.body && r.body.data) ? r.body.data : []);
        }, function () {
            if (refreshBtn) { refreshBtn.disabled = false; }
            fail("적재 이력을 불러오지 못했습니다.");
        });
    }

    if (refreshBtn) { refreshBtn.addEventListener("click", load); }
    load();
})();
