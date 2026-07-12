(function () {
    "use strict";

    var LABELS = {
        SBIZ: "소상공인시장진흥공단",
        NTS: "국세청",
        FTC_FRANCHISE: "공정위 가맹사업",
        KIPRIS: "특허정보넷 KIPRIS",
        REB_RONE: "부동산원 R-ONE",
        SEOUL: "서울 열린데이터광장",
        KAKAO: "카카오맵",
        GEMINI: "Gemini · AI 리포트",
        GEMINI_NEWS: "Gemini · 뉴스 요약"
    };
    // 한도에 걸렸을 때 무슨 일이 생기는지 카드에서 바로 알 수 있게 한다.
    // 전용 안내가 없는 API는 공통 안내로 채워 카드 높이를 고르게 유지한다.
    var NOTES = {
        GEMINI: "한도 도달 시 AI 리포트 생성이 차단됩니다",
        GEMINI_NEWS: "뉴스 요약 배치 전용 키의 사용량입니다"
    };
    var DEFAULT_NOTE = "매일 자정(KST) 기준으로 집계가 초기화됩니다";

    function api(path) {
        return fetch(path).then(function (r) {
            return r.json().catch(function () { return null; }).then(function (b) {
                return { ok: r.ok, status: r.status, body: b };
            });
        });
    }
    function esc(s) { var d = document.createElement("div"); d.textContent = (s == null) ? "" : String(s); return d.innerHTML.replace(/"/g, "&quot;").replace(/'/g, "&#39;"); }
    function num(n) { return Number(n || 0).toLocaleString(); }
    function level(pct) { return pct >= 90 ? "danger" : (pct >= 70 ? "warn" : "ok"); }
    function set(id, text) { var el = document.getElementById(id); if (el) { el.textContent = text; } }
    function day(iso) { return new Date(iso + "T00:00:00"); }
    function fmtMD(iso) { var d = day(iso); return (d.getMonth() + 1) + "/" + d.getDate(); }
    function fmtFull(iso) { var d = day(iso); return (d.getMonth() + 1) + "월 " + d.getDate() + "일"; }

    var grid = document.getElementById("usage-grid");

    // ── 오늘 사용량: 요약 타일 + API별 카드 ──────────────────────────
    api("/api/admin/ops/api-usage").then(function (r) {
        if (r.status === 401) { return; }
        if (r.status === 403) { grid.innerHTML = '<p class="ops-empty">SUPER_ADMIN만 볼 수 있습니다.</p>'; return; }
        var rows = (r.ok && r.body && r.body.data) ? r.body.data : [];
        renderSummary(rows);
        if (!rows.length) {
            grid.innerHTML = '<p class="ops-empty">오늘 집계된 사용량이 없습니다.</p>';
            return;
        }
        grid.innerHTML = rows.map(cardHtml).join("");
    });

    function renderSummary(rows) {
        var total = rows.reduce(function (s, a) { return s + (Number(a.callCnt) || 0); }, 0);
        var near = rows.filter(function (a) { return Number(a.usagePct) >= 70; }).length;
        set("us-total", num(total));
        set("us-count", String(rows.length));
        set("us-near", String(near));
        var nearSub = document.getElementById("us-near-sub");
        if (nearSub) {
            nearSub.textContent = near > 0 ? "70% 이상 주의" : "모두 여유";
            nearSub.className = "usage-sub " + (near > 0 ? "usage-warn" : "usage-ok");
        }
        var top = rows.slice().sort(function (a, b) { return b.usagePct - a.usagePct; })[0];
        var topEl = document.getElementById("us-top");
        var topSub = document.getElementById("us-top-sub");
        if (top && topEl) {
            var pct = Number(top.usagePct) || 0;
            topEl.textContent = pct + "%";
            topEl.className = "stat-card-value usage-" + level(pct);
            if (topSub) { topSub.textContent = LABELS[top.apiName] || top.apiName; }
        }
    }

    function cardHtml(a) {
        var lv = level(a.usagePct);
        var pct = Number(a.usagePct) || 0;
        return '<article class="usage-card">'
            + '<div class="usage-head">'
            + '<span class="usage-name">' + esc(LABELS[a.apiName] || a.apiName) + "</span>"
            + '<span class="usage-code mono">' + esc(a.apiName) + "</span>"
            + "</div>"
            + '<div class="usage-num"><b>' + num(a.callCnt) + "</b>"
            + '<span class="usage-limit"> / ' + num(a.dailyLimit) + " 호출</span></div>"
            + '<div class="usage-bar"><span class="usage-fill usage-' + lv + '" style="width:' + pct + '%"></span></div>'
            + '<div class="usage-pct usage-' + lv + '">' + pct + "% 사용</div>"
            + '<p class="usage-note">' + esc(NOTES[a.apiName] || DEFAULT_NOTE) + "</p>"
            + "</article>";
    }

    // ── 일자별 호출 추이(막대 차트) ──────────────────────────────────
    api("/api/admin/ops/api-usage/daily?days=14").then(function (r) {
        var el = document.getElementById("us-trend");
        if (!el) { return; }
        if (r.status === 403) { el.innerHTML = '<p class="ops-empty">SUPER_ADMIN만 볼 수 있습니다.</p>'; return; }
        var pts = (r.ok && r.body && r.body.data) ? r.body.data : [];
        if (!pts.length) { el.innerHTML = '<p class="ops-empty">추이 데이터가 없습니다.</p>'; return; }
        el.innerHTML = trendSvg(pts);
        if (!pts.some(function (p) { return p.totalCalls > 0; })) {
            el.insertAdjacentHTML("beforeend", '<p class="ut-hint">최근 14일 외부 API 호출 기록이 없습니다.</p>');
        }
    });

    // 세로 막대 차트 SVG. 집계 없는 날은 옅은 막대(0)로 두어 연속 추이를 유지한다.
    function trendSvg(points) {
        var n = points.length;
        // viewBox는 넓은 카드 폭에 맞춰(약 6.7:1) 잡는다. CSS가 height:auto라 비율대로 폭을 꽉 채운다.
        var W = 1280, H = 190, padL = 8, padR = 8, padT = 16, padB = 26;
        var gap = n > 1 ? 10 : 0;
        var bw = (W - padL - padR - gap * (n - 1)) / n;
        var plotH = H - padT - padB;
        var max = points.reduce(function (m, p) { return Math.max(m, Number(p.totalCalls) || 0); }, 0);
        var scale = max > 0 ? plotH / max : 0;
        var baseY = padT + plotH;

        var bars = points.map(function (p, i) {
            var v = Number(p.totalCalls) || 0;
            var x = padL + i * (bw + gap);
            var h = v > 0 ? Math.max(v * scale, 3) : 0;
            var y = baseY - h;
            return '<rect class="utb' + (v > 0 ? "" : " utb-zero") + '" x="' + x.toFixed(1)
                + '" y="' + y.toFixed(1) + '" width="' + bw.toFixed(1) + '" height="' + h.toFixed(1) + '" rx="3">'
                + "<title>" + fmtFull(p.date) + " · " + num(v) + "회</title></rect>";
        }).join("");

        // 마지막(오늘) 막대 위 값 표기
        var lastVal = "";
        var last = points[n - 1];
        if (last && max > 0 && Number(last.totalCalls) > 0) {
            var lx = padL + (n - 1) * (bw + gap) + bw / 2;
            var ly = baseY - Math.max(Number(last.totalCalls) * scale, 3) - 5;
            lastVal = '<text class="ut-val" x="' + lx.toFixed(1) + '" y="' + ly.toFixed(1)
                + '" text-anchor="middle">' + num(last.totalCalls) + "</text>";
        }

        // x 라벨: 촘촘하면 격일로 솎아 겹침 방지(항상 마지막은 표시)
        var labels = points.map(function (p, i) {
            if (n > 10 && i % 2 !== 0 && i !== n - 1) { return ""; }
            var x = padL + i * (bw + gap) + bw / 2;
            return '<text class="ut-x" x="' + x.toFixed(1) + '" y="' + (H - 9) + '" text-anchor="middle">' + fmtMD(p.date) + "</text>";
        }).join("");

        return '<div class="ut-wrap">'
            + '<div class="ut-max">최대 <b>' + num(max) + "</b>회</div>"
            + '<svg class="ut-svg" viewBox="0 0 ' + W + " " + H + '" role="img" aria-label="일자별 총 호출 추이">'
            + '<line class="ut-base" x1="' + padL + '" y1="' + baseY + '" x2="' + (W - padR) + '" y2="' + baseY + '"></line>'
            + bars + lastVal + labels
            + "</svg></div>";
    }
})();
