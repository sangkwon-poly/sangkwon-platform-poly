(function () {
    "use strict";

    var BATCH = {
        SUCCESS: { ko: "정상", card: "ov-up", badge: "badge-ok", dot: "dot-ok" },
        PARTIAL: { ko: "부분", card: "ov-warn", badge: "badge-warn", dot: "dot-warn" },
        RUNNING: { ko: "진행", card: "ov-warn", badge: "badge-warn", dot: "dot-warn" },
        FAILED: { ko: "오류", card: "ov-danger", badge: "badge-danger", dot: "dot-danger" }
    };
    var API_LABELS = {
        SBIZ: "소상공인시장진흥공단", NTS: "국세청", FTC_FRANCHISE: "공정위 가맹사업",
        KIPRIS: "특허정보넷 KIPRIS",
        REB_RONE: "부동산원 R-ONE", SEOUL: "서울 열린데이터광장", KAKAO: "카카오맵",
        GEMINI: "Gemini · AI 리포트", GEMINI_NEWS: "Gemini · 뉴스 요약"
    };

    function api(path) {
        return fetch(path).then(function (r) {
            return r.json().catch(function () { return null; }).then(function (b) {
                return { ok: r.ok, status: r.status, body: b };
            });
        });
    }
    function data(r) { return (r.ok && r.body && r.body.data) ? r.body.data : null; }
    function esc(s) { var d = document.createElement("div"); d.textContent = (s == null) ? "" : String(s); return d.innerHTML.replace(/"/g, "&quot;").replace(/'/g, "&#39;"); }
    function num(n) { return Number(n || 0).toLocaleString(); }
    function pad(n) { return String(n).padStart(2, "0"); }
    function hhmm(iso) { if (!iso) { return "-"; } var d = new Date(iso); return pad(d.getHours()) + ":" + pad(d.getMinutes()); }
    function set(id, text) { var el = document.getElementById(id); if (el) { el.textContent = text; } }
    function setSub(id, text, cls) {
        var el = document.getElementById(id);
        if (el) { el.textContent = text; el.className = "stat-delta " + (cls || "ov-flat"); }
    }

    // 사용률 구간: 70% 미만 정상, 90% 미만 주의, 그 이상 경고
    function level(pct) { return pct >= 90 ? "danger" : (pct >= 70 ? "warn" : "ok"); }

    // 데이터셋 적재 상태 SVG 도넛(정상 비율) 마크업
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
        return '<svg class="donut" viewBox="0 0 160 160" role="img" aria-label="데이터셋 정상 비율 ' + pct + '퍼센트">'
            + '<circle class="donut-bg" cx="80" cy="80" r="' + r + '"></circle>'
            + '<g transform="rotate(-90 80 80)">' + rings + '</g>'
            + '<text class="donut-pct" x="80" y="78">' + pct + '%</text>'
            + '<text class="donut-cap" x="80" y="98">정상</text>'
            + '</svg>';
    }

    // 오늘 날짜 메타
    (function () {
        var d = new Date();
        set("ov-meta", d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate()) + " · 여기콕 관리자 콘솔");
    })();

    // 1) 회원·리포트·매출·구독 + 오늘 검색·게시 공지·지원사업 (SUPER_ADMIN 전용)
    api("/api/admin/ops/overview").then(function (r) {
        if (r.status === 403) {
            ["member", "report", "revenue", "pro", "search", "notice", "support"].forEach(function (k) {
                setSub("ov-sub-" + k, "권한 필요");
            });
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
        set("ov-stat-revenue", "₩" + num(d.monthRevenue));
        setSub("ov-sub-revenue", d.monthRevenue > 0 ? "승인 완료 기준" : "이번 달 결제 없음");
        set("ov-stat-pro", num(d.activeProCount));
        setSub("ov-sub-pro", d.activeProCount > 0 ? "유효 구독 회원" : "구독자 없음");
        set("ov-stat-search", num(d.todaySearchCount));
        setSub("ov-sub-search", d.todaySearchCount > 0 ? "상권·업종 검색" : "오늘 검색 없음",
            d.todaySearchCount > 0 ? "ov-up" : "ov-flat");
        set("ov-stat-notice", num(d.publishedNoticeCount));
        setSub("ov-sub-notice", d.publishedNoticeCount > 0 ? "게시 중" : "게시 공지 없음");
        set("ov-stat-support", num(d.supportProgramCount));
        setSub("ov-sub-support", d.supportProgramCount > 0 ? "수집 공고" : "수집 이력 없음");
    });

    // 2) 분석 상권 수 (공개 API)
    api("/api/districts/summary").then(function (r) {
        var rows = data(r) || [];
        set("ov-stat-trdar", rows.length ? rows.length.toLocaleString() : "—");
    });

    // 2-1) 대기중 1:1 문의: 목록 API의 totalElements만 사용 (로그인 관리자 전원 조회 가능)
    api("/api/admin/inquiries?status=OPEN&page=0&size=1").then(function (r) {
        var d = data(r);
        if (!d) { return; }
        var open = Number(d.totalElements || 0);
        set("ov-stat-inquiry", num(open));
        setSub("ov-sub-inquiry", open > 0 ? "답변 대기" : "모두 처리됨", open > 0 ? "ov-warn" : "ov-flat");
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

    // 5) 최근 배치: 상태 카드 + 최근 실행 리스트 (SUPER_ADMIN 전용)
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

        // 같은 데이터셋을 여러 번 실행했으면 최신 1건만 보여준다(재실행 이력은 감사 로그·상세에서).
        // rows는 startedAt 내림차순이라 먼저 나온 것이 최신이다.
        var seen = {};
        var latest = rows.filter(function (b) {
            if (seen[b.datasetCd]) { return false; }
            seen[b.datasetCd] = true;
            return true;
        });
        load.innerHTML = latest.slice(0, 5).map(function (b) {
            var m = BATCH[b.status] || { ko: b.status, badge: "badge-warn", dot: "dot-warn" };
            return '<li class="load-item">'
                + '<span class="dot ' + m.dot + '" aria-hidden="true"></span>'
                + '<span class="load-name mono">' + esc(b.datasetCd) + "</span>"
                + '<span class="load-time">' + hhmm(b.startedAt) + "</span>"
                + '<span class="badge ' + m.badge + '">' + esc(m.ko) + "</span>"
                + "</li>";
        }).join("");
    });

    // 5-1) 데이터셋 적재 상태 도넛: 실행 이력 행이 아니라 데이터셋(14종) 기준.
    // 최근 실행이 실패면 오류, 진행·부분이면 주의, 그 외 데이터가 적재돼 있으면 정상(오프라인 적재 포함), 없으면 미적재.
    api("/api/admin/ops/batch/catalog").then(function (r) {
        var donut = document.getElementById("ov-batch-donut");
        if (r.status === 403) {
            donut.innerHTML = '<p class="ov-note">SUPER_ADMIN만 볼 수 있습니다.</p>';
            return;
        }
        var rows = data(r) || [];
        if (!rows.length) {
            donut.innerHTML = '<p class="ov-note">데이터셋 정보가 없습니다.</p>';
            return;
        }
        var ok = 0, mid = 0, fail = 0, none = 0;
        rows.forEach(function (c) {
            if (c.running || c.runStatus === "PARTIAL") { mid++; }
            else if (c.runStatus === "FAILED") { fail++; }
            else if (c.loaded) { ok++; }
            else { none++; }
        });
        var pct = Math.round(ok / rows.length * 100);
        donut.innerHTML = donutSvg(pct, [
            { cls: "seg-ok", val: ok },
            { cls: "seg-warn", val: mid },
            { cls: "seg-danger", val: fail },
            { cls: "seg-muted", val: none }
        ]) + '<ul class="donut-legend">'
            + '<li><span class="lg lg-ok" aria-hidden="true"></span>정상 <b>' + ok + "</b></li>"
            + '<li><span class="lg lg-warn" aria-hidden="true"></span>부분·진행 <b>' + mid + "</b></li>"
            + '<li><span class="lg lg-danger" aria-hidden="true"></span>오류 <b>' + fail + "</b></li>"
            + '<li><span class="lg lg-muted" aria-hidden="true"></span>미적재 <b>' + none + "</b></li>"
            + "</ul>";
    });

    // 6) 인기 검색어 (SUPER_ADMIN 전용): 회원 검색 기록을 지역·업종 수요 신호로 보여준다.
    // 키워드를 지도 검색 링크로 걸지 않는다: 검색 페이지가 진입 시 검색 로그를 적재해
    // 관리자가 확인차 누를수록 이 지표 자체가 부풀어 오르는 되먹임이 생긴다.
    api("/api/admin/ops/popular-searches?days=7&limit=8").then(function (r) {
        var el = document.getElementById("ov-popular-search");
        if (!el) { return; }
        if (r.status === 403) { el.innerHTML = '<li class="ov-note">SUPER_ADMIN만 볼 수 있습니다.</li>'; return; }
        var rows = data(r) || [];
        if (!rows.length) { el.innerHTML = '<li class="ov-note">최근 검색 기록이 없습니다.</li>'; return; }
        var max = rows[0].count || 1;
        el.innerHTML = rows.map(function (s, i) {
            var pct = Math.max(Math.round(s.count / max * 100), 4);
            return '<li class="bar-row">'
                + '<span class="bar-label">' + (i + 1) + ". " + esc(s.keyword) + "</span>"
                + '<span class="bar-track"><span class="bar-fill bar-ok" style="width:' + pct + '%"></span></span>'
                + '<span class="bar-val mono">' + num(s.count) + "</span>"
                + "</li>";
        }).join("");
    });
})();
