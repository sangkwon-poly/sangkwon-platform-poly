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
        return '<span class="badge ' + m[0] + '"><span class="dot ' + m[1] + '"></span>' + esc(m[2]) + "</span>";
    }

    api("/api/admin/ops/batch").then(function (r) {
        if (r.status === 401) { return; }
        var rows = (r.ok && r.body && r.body.data) ? r.body.data : [];
        var tbody = document.querySelector(".batch-table tbody");
        if (tbody) {
            if (!rows.length) {
                tbody.innerHTML = '<tr><td colspan="6" class="ops-empty">적재 이력이 없습니다.</td></tr>';
            } else {
                tbody.innerHTML = rows.map(function (b) {
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
                }).join("");
            }
        }
        var vals = document.querySelectorAll(".batch-stats .stat-card-value");
        if (vals.length >= 4) {
            var count = function (s) { return rows.filter(function (b) { return b.status === s; }).length; };
            vals[0].textContent = rows.length ? hhmm(rows[0].startedAt) : "-";
            vals[1].textContent = count("SUCCESS");
            vals[2].textContent = count("PARTIAL") + count("RUNNING");
            vals[3].textContent = count("FAILED");
        }
    });
})();
