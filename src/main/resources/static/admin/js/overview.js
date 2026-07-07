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
    function pad(n) { return String(n).padStart(2, "0"); }
    function hhmm(iso) { if (!iso) { return "-"; } var d = new Date(iso); return pad(d.getHours()) + ":" + pad(d.getMinutes()); }
    function stamp(iso) { if (!iso) { return "-"; } var d = new Date(iso); return (d.getMonth() + 1) + "/" + d.getDate() + " " + pad(d.getHours()) + ":" + pad(d.getMinutes()); }
    function set(id, text) { var el = document.getElementById(id); if (el) { el.textContent = text; } }
    function setSub(id, text, cls) {
        var el = document.getElementById(id);
        if (el) { el.textContent = text; el.className = "stat-delta " + (cls || "ov-flat"); }
    }

    // 오늘 날짜 메타
    (function () {
        var d = new Date();
        set("ov-meta", d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate()) + " · 서울공화국 관리자 콘솔");
    })();

    // 1) 분석 상권 수 (공개 API)
    api("/api/districts/summary").then(function (r) {
        var rows = data(r) || [];
        set("ov-stat-trdar", rows.length ? rows.length.toLocaleString() : "—");
    });

    // 2) 관리자 수 (SUPER_ADMIN 전용)
    api("/api/admin/admin-users").then(function (r) {
        if (r.status === 403) { set("ov-stat-admin", "—"); setSub("ov-sub-admin", "권한 필요"); return; }
        var rows = data(r) || [];
        var active = rows.filter(function (a) { return a.status === "ACTIVE"; }).length;
        set("ov-stat-admin", rows.length.toLocaleString());
        setSub("ov-sub-admin", "활성 " + active + " · 전체 " + rows.length);
    });

    // 3) 오늘 API 최고 사용률 (SUPER_ADMIN 전용)
    api("/api/admin/ops/api-usage").then(function (r) {
        if (r.status === 403) { set("ov-stat-api", "—"); setSub("ov-sub-api", "권한 필요"); return; }
        var rows = data(r) || [];
        if (!rows.length) { set("ov-stat-api", "0%"); setSub("ov-sub-api", "집계 없음"); return; }
        var top = rows.slice().sort(function (a, b) { return b.usagePct - a.usagePct; })[0];
        set("ov-stat-api", top.usagePct + "%");
        setSub("ov-sub-api", API_LABELS[top.apiName] || top.apiName, top.usagePct >= 90 ? "ov-danger" : (top.usagePct >= 70 ? "ov-warn" : "ov-flat"));
    });

    // 4) 최근 배치 + 적재 현황 리스트 (SUPER_ADMIN 전용)
    api("/api/admin/ops/batch").then(function (r) {
        var load = document.getElementById("ov-load");
        if (r.status === 403) {
            set("ov-stat-batch", "—"); setSub("ov-sub-batch", "권한 필요");
            load.innerHTML = '<li class="ov-note">SUPER_ADMIN만 볼 수 있습니다.</li>';
            return;
        }
        var rows = data(r) || [];
        if (!rows.length) {
            set("ov-stat-batch", "없음"); setSub("ov-sub-batch", "이력 없음");
            load.innerHTML = '<li class="ov-note">적재 이력이 없습니다.</li>';
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
    });

    // 5) 최근 활동 (감사 로그, SUPER_ADMIN 전용)
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
