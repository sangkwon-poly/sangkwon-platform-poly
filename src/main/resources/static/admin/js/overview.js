(function () {
    "use strict";

    var BATCH = {
        SUCCESS: { ko: "정상", card: "ov-up", badge: "badge-ok", dot: "dot-ok" },
        PARTIAL: { ko: "부분", card: "ov-warn", badge: "badge-warn", dot: "dot-warn" },
        RUNNING: { ko: "진행", card: "ov-warn", badge: "badge-warn", dot: "dot-warn" },
        FAILED: { ko: "오류", card: "ov-danger", badge: "badge-danger", dot: "dot-danger" }
    };
    var ACTIONS = {
        LOGIN: { label: "로그인", dot: "feed-brand" },
        ADMIN_CREATE: { label: "관리자 생성", dot: "feed-ok" },
        ADMIN_ROLE_UPDATE: { label: "역할 변경", dot: "feed-ok" },
        ADMIN_STATUS_UPDATE: { label: "상태 변경", dot: "feed-ok" },
        OTP_ENABLE: { label: "2단계 인증 켬", dot: "feed-warn" },
        OTP_DISABLE: { label: "2단계 인증 끔", dot: "feed-warn" }
    };
    var API_LABELS = {
        SBIZ: "소상공인시장진흥공단", NTS: "국세청", FTC_FRANCHISE: "공정위 가맹사업",
        REB_RONE: "부동산원 R-ONE", SEOUL: "서울 열린데이터광장", KAKAO: "카카오맵"
    };

    function api(path) {
        return fetch(path).then(function (r) {
            return r.json().catch(function () { return null; }).then(function (b) {
                return { ok: r.ok, status: r.status, body: b };
            });
        });
    }
    function data(r) { return (r.ok && r.body && r.body.data) ? r.body.data : null; }
    function esc(s) { var d = document.createElement("div"); d.textContent = (s == null) ? "" : String(s); return d.innerHTML; }
    function num(n) { return Number(n || 0).toLocaleString(); }
    function pad(n) { return String(n).padStart(2, "0"); }
    function hhmm(iso) { if (!iso) { return "-"; } var d = new Date(iso); return pad(d.getHours()) + ":" + pad(d.getMinutes()); }
    function stamp(iso) { if (!iso) { return "-"; } var d = new Date(iso); return (d.getMonth() + 1) + "/" + d.getDate() + " " + pad(d.getHours()) + ":" + pad(d.getMinutes()); }
    function set(id, text) { var el = document.getElementById(id); if (el) { el.textContent = text; } }
    function setSub(id, text, cls) {
        var el = document.getElementById(id);
        if (el) { el.textContent = text; el.className = "stat-delta " + (cls || "ov-flat"); }
    }

    // 사용률 구간: 70% 미만 정상, 90% 미만 주의, 그 이상 경고
    function level(pct) { return pct >= 90 ? "danger" : (pct >= 70 ? "warn" : "ok"); }

    // 배치 상태별 SVG 도넛(성공률) 마크업
    function donutSvg(pct, seg) {
        var r = 52, circ = 2 * Math.PI * r, off = 0, rings = "";
        var total = seg.reduce(function (s, x) { return s + x.val; }, 0);
        if (total > 0) {
            rings = seg.filter(function (x) { return x.val > 0; }).map(function (x) {
                var len = x.val / total * circ;
                var c = '<circle class="donut-seg ' + x.cls + '" cx="80" cy="80" r="' + r
                    + '" stroke-dasharray="' + len.toFixed(2) + " " + (circ - len).toFixed(2)
                    + '" stroke-dashoffset="' + (-off).toFixed(2) + '"></circle>';
                off += len;
                return c;
            }).join("");
        }
        return '<svg class="donut" viewBox="0 0 160 160" role="img" aria-label="배치 성공률 ' + pct + '퍼센트">'
            + '<circle class="donut-bg" cx="80" cy="80" r="' + r + '"></circle>'
            + '<g transform="rotate(-90 80 80)">' + rings + '</g>'
            + '<text class="donut-pct" x="80" y="78">' + pct + '%</text>'
            + '<text class="donut-cap" x="80" y="98">성공률</text>'
            + '</svg>';
    }

    // 오늘 날짜 메타
    (function () {
        var d = new Date();
        set("ov-meta", d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate()) + " · 서울공화국 관리자 콘솔");
    })();

    // 1) 회원·리포트 누적 (SUPER_ADMIN 전용)
    api("/api/admin/ops/overview").then(function (r) {
        if (r.status === 403) {
            setSub("ov-sub-member", "권한 필요"); setSub("ov-sub-report", "권한 필요");
            return;
        }
        var d = data(r);
        if (!d) { return; }
        set("ov-stat-member", num(d.memberCount));
        setSub("ov-sub-member", d.todaySignups > 0 ? "오늘 +" + d.todaySignups : "가입 회원",
            d.todaySignups > 0 ? "ov-up" : "ov-flat");
        set("ov-stat-report", num(d.reportCount));
        setSub("ov-sub-report", d.todayReports > 0 ? "오늘 +" + d.todayReports : "AI 생성",
            d.todayReports > 0 ? "ov-up" : "ov-flat");
    });

    // 2) 분석 상권 수 (공개 API)
    api("/api/districts/summary").then(function (r) {
        var rows = data(r) || [];
        set("ov-stat-trdar", rows.length ? rows.length.toLocaleString() : "—");
    });

    // 3) 관리자 수 (SUPER_ADMIN 전용)
    api("/api/admin/admin-users").then(function (r) {
        if (r.status === 403) { set("ov-stat-admin", "—"); setSub("ov-sub-admin", "권한 필요"); return; }
        var rows = data(r) || [];
        var active = rows.filter(function (a) { return a.status === "ACTIVE"; }).length;
        set("ov-stat-admin", rows.length.toLocaleString());
        setSub("ov-sub-admin", "활성 " + active + " · 전체 " + rows.length);
    });

    // 4) 오늘 API 사용량: 최고 사용 카드 + 사용률 막대 (SUPER_ADMIN 전용)
    api("/api/admin/ops/api-usage").then(function (r) {
        var bars = document.getElementById("ov-api-bars");
        if (r.status === 403) {
            set("ov-stat-api", "—"); setSub("ov-sub-api", "권한 필요");
            bars.innerHTML = '<li class="ov-note">SUPER_ADMIN만 볼 수 있습니다.</li>';
            return;
        }
        var rows = data(r) || [];
        if (!rows.length) {
            set("ov-stat-api", "0%"); setSub("ov-sub-api", "집계 없음");
            bars.innerHTML = '<li class="ov-note">오늘 호출 집계가 없습니다.</li>';
            return;
        }
        var sorted = rows.slice().sort(function (a, b) { return b.usagePct - a.usagePct; });
        var top = sorted[0];
        var topLv = level(top.usagePct);
        set("ov-stat-api", top.usagePct + "%");
        setSub("ov-sub-api", API_LABELS[top.apiName] || top.apiName, topLv === "ok" ? "ov-flat" : "ov-" + topLv);

        bars.innerHTML = sorted.map(function (a) {
            return '<li class="bar-row">'
                + '<span class="bar-label">' + esc(API_LABELS[a.apiName] || a.apiName) + "</span>"
                + '<span class="bar-track"><span class="bar-fill bar-' + level(a.usagePct)
                + '" style="width:' + Math.max(a.usagePct, 2) + '%"></span></span>'
                + '<span class="bar-val mono">' + a.usagePct + "%</span>"
                + "</li>";
        }).join("");
    });

    // 5) 최근 배치: 상태 카드 + 적재 리스트 + 성공률 도넛 (SUPER_ADMIN 전용)
    api("/api/admin/ops/batch").then(function (r) {
        var load = document.getElementById("ov-load");
        var donut = document.getElementById("ov-batch-donut");
        if (r.status === 403) {
            set("ov-stat-batch", "—"); setSub("ov-sub-batch", "권한 필요");
            load.innerHTML = '<li class="ov-note">SUPER_ADMIN만 볼 수 있습니다.</li>';
            donut.innerHTML = '<p class="ov-note">SUPER_ADMIN만 볼 수 있습니다.</p>';
            return;
        }
        var rows = data(r) || [];
        if (!rows.length) {
            set("ov-stat-batch", "없음"); setSub("ov-sub-batch", "이력 없음");
            load.innerHTML = '<li class="ov-note">적재 이력이 없습니다.</li>';
            donut.innerHTML = '<p class="ov-note">적재 이력이 없습니다.</p>';
            return;
        }
        var first = BATCH[rows[0].status] || { ko: rows[0].status, card: "ov-flat" };
        set("ov-stat-batch", first.ko);
        setSub("ov-sub-batch", hhmm(rows[0].startedAt) + " 마지막", first.card);

        load.innerHTML = rows.slice(0, 5).map(function (b) {
            var m = BATCH[b.status] || { ko: b.status, badge: "badge-warn", dot: "dot-warn" };
            return '<li class="load-item">'
                + '<span class="dot ' + m.dot + '" aria-hidden="true"></span>'
                + '<span class="load-name mono">' + esc(b.datasetCd) + "</span>"
                + '<span class="load-time">' + hhmm(b.startedAt) + "</span>"
                + '<span class="badge ' + m.badge + '">' + esc(m.ko) + "</span>"
                + "</li>";
        }).join("");

        var ok = 0, mid = 0, fail = 0;
        rows.forEach(function (b) {
            if (b.status === "SUCCESS") { ok++; }
            else if (b.status === "FAILED") { fail++; }
            else { mid++; }
        });
        var pct = Math.round(ok / rows.length * 100);
        donut.innerHTML = donutSvg(pct, [
            { cls: "seg-ok", val: ok },
            { cls: "seg-warn", val: mid },
            { cls: "seg-danger", val: fail }
        ]) + '<ul class="donut-legend">'
            + '<li><span class="lg lg-ok" aria-hidden="true"></span>정상 <b>' + ok + "</b></li>"
            + '<li><span class="lg lg-warn" aria-hidden="true"></span>부분·진행 <b>' + mid + "</b></li>"
            + '<li><span class="lg lg-danger" aria-hidden="true"></span>오류 <b>' + fail + "</b></li>"
            + "</ul>";
    });

    // 6) 최근 활동 (감사 로그, SUPER_ADMIN 전용)
    api("/api/admin/ops/audit").then(function (r) {
        var feed = document.getElementById("ov-feed");
        if (r.status === 403) { feed.innerHTML = '<li class="ov-note">SUPER_ADMIN만 볼 수 있습니다.</li>'; return; }
        var rows = data(r) || [];
        if (!rows.length) { feed.innerHTML = '<li class="ov-note">최근 활동이 없습니다.</li>'; return; }
        feed.innerHTML = rows.slice(0, 6).map(function (a) {
            var act = ACTIONS[a.action] || { label: a.action, dot: "feed-brand" };
            var target = a.targetType ? (" · " + esc(a.targetType) + (a.targetId ? " #" + esc(a.targetId) : "")) : "";
            return '<li class="feed-item">'
                + '<span class="feed-dot ' + act.dot + '" aria-hidden="true"></span>'
                + "<div><div class=\"feed-text\"><b>" + esc(a.adminLoginId || "-") + "</b> · " + esc(act.label) + target + "</div>"
                + '<div class="feed-time">' + stamp(a.createdAt) + "</div></div>"
                + "</li>";
        }).join("");
    });
})();
