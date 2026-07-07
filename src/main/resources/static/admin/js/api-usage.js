(function () {
    "use strict";

    var LABELS = {
        SBIZ: "소상공인시장진흥공단",
        NTS: "국세청",
        FTC_FRANCHISE: "공정위 가맹사업",
        REB_RONE: "부동산원 R-ONE",
        SEOUL: "서울 열린데이터광장",
        KAKAO: "카카오맵"
    };

    function api(path) {
        return fetch(path).then(function (r) {
            return r.json().catch(function () { return null; }).then(function (b) {
                return { ok: r.ok, status: r.status, body: b };
            });
        });
    }
    function esc(s) { var d = document.createElement("div"); d.textContent = (s == null) ? "" : String(s); return d.innerHTML; }
    function level(pct) { return pct >= 90 ? "danger" : (pct >= 70 ? "warn" : "ok"); }

    var grid = document.getElementById("usage-grid");

    api("/api/admin/ops/api-usage").then(function (r) {
        if (r.status === 401) { return; }
        if (r.status === 403) { grid.innerHTML = '<p class="ops-empty">SUPER_ADMIN만 볼 수 있습니다.</p>'; return; }
        var rows = (r.ok && r.body && r.body.data) ? r.body.data : [];
        if (!rows.length) {
            grid.innerHTML = '<p class="ops-empty">오늘 집계된 사용량이 없습니다.</p>';
            return;
        }
        grid.innerHTML = rows.map(function (a) {
            var lv = level(a.usagePct);
            return '<article class="usage-card">'
                + '<div class="usage-head">'
                + '<span class="usage-name">' + esc(LABELS[a.apiName] || a.apiName) + "</span>"
                + '<span class="usage-code mono">' + esc(a.apiName) + "</span>"
                + "</div>"
                + '<div class="usage-num"><b>' + Number(a.callCnt).toLocaleString() + "</b>"
                + '<span class="usage-limit"> / ' + Number(a.dailyLimit).toLocaleString() + " 호출</span></div>"
                + '<div class="usage-bar"><span class="usage-fill usage-' + lv + '" style="width:' + a.usagePct + '%"></span></div>'
                + '<div class="usage-pct usage-' + lv + '">' + a.usagePct + "% 사용</div>"
                + "</article>";
        }).join("");
    });
})();
