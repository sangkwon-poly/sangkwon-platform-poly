(function () {
    "use strict";

    var STATUSES = [
        { value: "ACTIVE",    label: "활성", badge: "badge-ok",     dot: "dot-ok" },
        { value: "DORMANT",   label: "휴면", badge: "badge-warn",   dot: "dot-warn" },
        { value: "BANNED",    label: "정지", badge: "badge-danger", dot: "dot-danger" },
        { value: "WITHDRAWN", label: "탈퇴", badge: "badge-muted",  dot: "dot-muted" }
    ];
    function statusMeta(v) {
        for (var i = 0; i < STATUSES.length; i++) { if (STATUSES[i].value === v) { return STATUSES[i]; } }
        return { value: v, label: v, badge: "badge-muted", dot: "dot-muted" };
    }
    // 필터 칩: 전체 + 상태 4종
    var FILTERS = [{ key: "ALL", label: "전체" }].concat(
        STATUSES.map(function (s) { return { key: s.value, label: s.label }; }));

    // 현재 상태에서 허용하는 전이만 버튼으로 노출한다. 휴면(DORMANT)은 무활동으로 시스템이 매기는 상태라 수동 부여 버튼을 두지 않는다.
    function actionsFor(status) {
        if (status === "ACTIVE") {
            return [{ to: "BANNED", label: "정지", danger: true }, { to: "WITHDRAWN", label: "강제 탈퇴", danger: true }];
        }
        if (status === "DORMANT") {
            return [{ to: "ACTIVE", label: "활성화" }, { to: "BANNED", label: "정지", danger: true }, { to: "WITHDRAWN", label: "강제 탈퇴", danger: true }];
        }
        if (status === "BANNED") {
            return [{ to: "ACTIVE", label: "정지 해제" }, { to: "WITHDRAWN", label: "강제 탈퇴", danger: true }];
        }
        if (status === "WITHDRAWN") {
            return [{ to: "ACTIVE", label: "재활성화", danger: true }];
        }
        return [];
    }
    // 되돌리기 어렵거나 파급이 큰 전이에만 확인창을 띄운다.
    function confirmMsg(from, to) {
        if (to === "WITHDRAWN") { return "이 회원을 강제 탈퇴 처리할까요? 되돌리기 어려운 작업입니다."; }
        if (to === "BANNED") { return "이 회원을 정지하면 로그인할 수 없습니다. 정지할까요?"; }
        if (from === "WITHDRAWN") { return "탈퇴한 회원을 다시 활성 상태로 되돌립니다. 진행할까요?"; }
        return null;
    }

    function api(path, opts) {
        return fetch(path, opts).then(function (res) {
            return res.json().catch(function () { return null; }).then(function (b) {
                return { ok: res.ok, status: res.status, body: b };
            });
        });
    }
    function jsonOpts(method, body) {
        return { method: method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) };
    }
    function esc(s) { var d = document.createElement("div"); d.textContent = (s == null) ? "" : String(s); return d.innerHTML.replace(/"/g, "&quot;").replace(/'/g, "&#39;"); }
    function msgOf(r, fallback) { return (r.body && r.body.message) ? r.body.message : fallback; }
    function two(n) { return n < 10 ? "0" + n : "" + n; }
    function fmtDateTime(iso) {
        if (!iso) { return "—"; }
        var d = new Date(iso);
        if (isNaN(d.getTime())) { return "—"; }
        return d.getFullYear() + "-" + two(d.getMonth() + 1) + "-" + two(d.getDate())
            + " " + two(d.getHours()) + ":" + two(d.getMinutes());
    }
    function fmtDate(iso) {
        if (!iso) { return "—"; }
        var d = new Date(iso);
        if (isNaN(d.getTime())) { return "—"; }
        return d.getFullYear() + "-" + two(d.getMonth() + 1) + "-" + two(d.getDate());
    }

    var tbody = document.getElementById("ma-tbody");
    var chipsEl = document.getElementById("ma-chips");
    var searchEl = document.getElementById("ma-search");
    var pagerEl = document.getElementById("ma-pager");
    var pagesEl = document.getElementById("ma-pages");
    var prevBtn = document.getElementById("ma-prev");
    var nextBtn = document.getElementById("ma-next");
    var metaEl = document.getElementById("ma-meta");
    var flashEl = document.getElementById("ma-flash");

    var state = { filter: "ALL", keyword: "", sort: { key: "createdAt", dir: "desc" }, page: 0, size: 20, resp: null, counts: null };
    var flashTimer = null;
    var searchTimer = null;
    // 요청 순번. 조작이 겹칠 때 늦게 온 낡은 응답이 최신 화면을 덮지 않게 한다.
    var reqSeq = 0;
    // 상태 변경 후 재조회하면 innerHTML이 갈리므로, 그 행 버튼으로 포커스를 되돌리기 위한 대상.
    var pendingFocusId = null;

    function flash(msg, isError) {
        flashEl.textContent = msg;
        flashEl.classList.toggle("is-error", !!isError);
        flashEl.hidden = false;
        if (flashTimer) { clearTimeout(flashTimer); }
        flashTimer = setTimeout(function () { flashEl.hidden = true; }, 2600);
    }
    function emptyRow(msg) { return '<tr><td colspan="7" class="ma-empty">' + esc(msg) + "</td></tr>"; }
    function findMember(id) {
        var list = (state.resp && state.resp.content) || [];
        for (var i = 0; i < list.length; i++) { if (list[i].memberId === id) { return list[i]; } }
        return null;
    }
    function setBusy(on) {
        tbody.classList.toggle("is-busy", on);
        tbody.setAttribute("aria-busy", on ? "true" : "false");
    }

    function buildChips() {
        chipsEl.innerHTML = FILTERS.map(function (f) {
            return '<button type="button" class="ma-chip" data-filter="' + f.key + '" aria-pressed="false">'
                + esc(f.label) + ' <b data-count="' + f.key + '">0</b></button>';
        }).join("");
        Array.prototype.forEach.call(chipsEl.querySelectorAll(".ma-chip"), function (chip) {
            chip.addEventListener("click", function () {
                state.filter = chip.getAttribute("data-filter");
                state.page = 0;
                renderChipsActive();
                load();
            });
        });
        renderChipsActive();
    }
    function renderChipsActive() {
        Array.prototype.forEach.call(chipsEl.querySelectorAll(".ma-chip"), function (chip) {
            var on = chip.getAttribute("data-filter") === state.filter;
            chip.classList.toggle("is-active", on);
            chip.setAttribute("aria-pressed", on ? "true" : "false");
        });
    }
    function renderCounts() {
        if (!state.counts) { return; }
        var c = state.counts;
        var map = { ALL: c.total, ACTIVE: c.active, DORMANT: c.dormant, BANNED: c.banned, WITHDRAWN: c.withdrawn };
        Array.prototype.forEach.call(chipsEl.querySelectorAll("b[data-count]"), function (b) {
            var k = b.getAttribute("data-count");
            b.textContent = map[k] != null ? Number(map[k]).toLocaleString() : "0";
        });
    }

    function forbidden() {
        tbody.innerHTML = emptyRow("최고관리자(SUPER_ADMIN)만 접근할 수 있습니다.");
        chipsEl.style.display = "none";
        if (searchEl) { searchEl.parentNode.style.display = "none"; }
        pagerEl.hidden = true;
        metaEl.textContent = "접근 권한이 없습니다.";
    }

    function load() {
        var seq = ++reqSeq;
        var q = "/api/admin/members?page=" + state.page + "&size=" + state.size
            + "&sort=" + state.sort.key + "&direction=" + state.sort.dir;
        if (state.filter !== "ALL") { q += "&status=" + state.filter; }
        if (state.keyword) { q += "&keyword=" + encodeURIComponent(state.keyword); }

        // 첫 로딩만 로딩 문구, 이후 갱신은 기존 행을 흐리게만 해서 깜빡임을 없앤다.
        if (state.resp == null) { tbody.innerHTML = emptyRow("불러오는 중…"); }
        else { setBusy(true); }

        api(q).then(function (r) {
            if (seq !== reqSeq) { return; } // 더 최신 요청이 있으면 이 응답은 버린다
            setBusy(false);
            if (r.status === 401) { return; } // 세션 만료는 admin-shell이 로그인으로 보냄
            if (r.status === 403) { forbidden(); return; }
            if (!r.ok || !r.body || !r.body.data) {
                tbody.innerHTML = emptyRow("목록을 불러오지 못했습니다.");
                pagerEl.hidden = true;
                return;
            }
            state.resp = r.body.data;
            // 상태 필터 마지막 페이지의 유일 행이 필터 밖으로 바뀌면 빈 페이지에 갇힌다. 마지막 유효 페이지로 한 번 당겨 재조회.
            if (state.resp.content.length === 0 && state.resp.page > 0) {
                state.page = Math.max(0, state.resp.totalPages - 1);
                load();
                return;
            }
            renderTable();
        }, function () {
            if (seq !== reqSeq) { return; }
            setBusy(false);
            tbody.innerHTML = emptyRow("목록을 불러오지 못했습니다.");
            pagerEl.hidden = true;
        });
    }
    function loadCounts() {
        api("/api/admin/members/counts").then(function (r) {
            if (r.ok && r.body && r.body.data) { state.counts = r.body.data; renderCounts(); }
        });
    }

    // 구독 셀: Pro면 만료일까지, 아니면 무료 배지만
    function planHtml(m) {
        if (!m.pro) { return '<span class="badge badge-muted">무료</span>'; }
        return '<div class="ma-plan"><span class="badge badge-info">Pro</span>'
            + '<span class="ma-plan-until">~ ' + esc(fmtDate(m.planUntil)) + "</span></div>";
    }
    // 구독 조작: 탈퇴 회원은 대상에서 제외. 부여·연장은 1개월 단위(CS 보상 기본 단위).
    function planActionsFor(m) {
        if (m.status === "WITHDRAWN") { return []; }
        var acts = [{ op: "EXTEND", label: m.pro ? "구독 연장" : "Pro 부여" }];
        if (m.pro) { acts.push({ op: "REVOKE", label: "구독 회수", danger: true }); }
        return acts;
    }

    function rowHtml(m) {
        var s = statusMeta(m.status);
        var badge = '<span class="badge ' + s.badge + '"><span class="dot ' + s.dot + '" aria-hidden="true"></span>' + esc(s.label) + "</span>";
        var acts = '<button type="button" class="ma-act" data-detail="' + m.memberId + '">상세</button>'
            + actionsFor(m.status).map(function (a) {
            return '<button type="button" class="ma-act' + (a.danger ? " ma-act-danger" : "") + '" data-id="' + m.memberId + '" data-status="' + a.to + '">' + esc(a.label) + "</button>";
        }).concat(planActionsFor(m).map(function (a) {
            return '<button type="button" class="ma-act' + (a.danger ? " ma-act-danger" : "") + '" data-id="' + m.memberId + '" data-plan="' + a.op + '">' + esc(a.label) + "</button>";
        })).join("");
        var auditHref = "/admin/audit-log?targetType=MEMBER&targetId=" + m.memberId + "&label=" + encodeURIComponent(m.loginId);
        return "<tr>"
            + '<td><div class="ma-user"><span class="ma-avatar" aria-hidden="true">' + esc((m.nickname || "?").charAt(0)) + "</span>"
            + '<span class="ma-id"><span class="ma-name">' + esc(m.nickname) + "</span>"
            + '<span class="ma-login">' + esc(m.loginId) + "</span>"
            + '<span class="ma-audit"><a href="' + auditHref + '">변경 이력</a></span></span></div></td>'
            + '<td class="ma-email">' + esc(m.email) + "</td>"
            + '<td class="col-center">' + badge + "</td>"
            + '<td class="col-center">' + planHtml(m) + "</td>"
            + '<td class="ma-muted">' + fmtDate(m.createdAt) + "</td>"
            + '<td class="ma-muted">' + fmtDateTime(m.lastLoginAt) + "</td>"
            + '<td class="col-center"><div class="ma-actions">' + acts + "</div></td>"
            + "</tr>";
    }

    function renderTable() {
        var resp = state.resp;
        var list = resp.content || [];
        tbody.innerHTML = list.length
            ? list.map(rowHtml).join("")
            : emptyRow(state.keyword ? "검색 결과가 없습니다." : "표시할 회원이 없습니다.");
        wireRows();
        syncSortHeaders();
        renderPager(resp);
        metaEl.textContent = "총 " + Number(resp.totalElements).toLocaleString() + "명";

        // 방금 상태를 바꾼 행의 버튼으로 포커스를 되돌린다(필터에서 빠졌으면 없으니 건너뜀).
        if (pendingFocusId != null) {
            var btn = tbody.querySelector('.ma-act[data-id="' + pendingFocusId + '"]');
            if (btn) { btn.focus(); }
            pendingFocusId = null;
        }
    }

    function wireRows() {
        Array.prototype.forEach.call(tbody.querySelectorAll(".ma-act[data-detail]"), function (btn) {
            btn.addEventListener("click", function () { openDetail(Number(btn.getAttribute("data-detail"))); });
        });
        Array.prototype.forEach.call(tbody.querySelectorAll(".ma-act[data-status]"), function (btn) {
            btn.addEventListener("click", function () {
                changeStatus(Number(btn.getAttribute("data-id")), btn.getAttribute("data-status"), btn);
            });
        });
        Array.prototype.forEach.call(tbody.querySelectorAll(".ma-act[data-plan]"), function (btn) {
            btn.addEventListener("click", function () {
                changePlan(Number(btn.getAttribute("data-id")), btn.getAttribute("data-plan"), btn);
            });
        });
    }

    // ── 회원 상세 모달: 결제·문의 이력 ────────────────────
    var modal = document.getElementById("ma-modal");
    var PAY_STATUS = { PENDING: "대기", PAID: "완료", FAILED: "실패", CANCELED: "환불" };
    var INQ_STATUS = { OPEN: "접수", ANSWERED: "답변완료", CLOSED: "종료" };

    function closeDetail() { if (modal) { modal.hidden = true; } }

    function openDetail(id) {
        if (!modal) { return; }
        var info = document.getElementById("ma-modal-info");
        info.innerHTML = '<div class="ma-modal-row"><dt>불러오는 중…</dt><dd></dd></div>';
        document.getElementById("ma-modal-payments").innerHTML = "";
        document.getElementById("ma-modal-inquiries").innerHTML = "";
        modal.hidden = false;
        api("/api/admin/members/" + id).then(function (r) {
            if (r.status === 401) { return; }
            if (!r.ok || !r.body || !r.body.data) {
                info.innerHTML = '<div class="ma-modal-row"><dt>회원 정보를 불러오지 못했습니다.</dt><dd></dd></div>';
                return;
            }
            renderDetail(r.body.data);
        }, function () {
            info.innerHTML = '<div class="ma-modal-row"><dt>회원 정보를 불러오지 못했습니다.</dt><dd></dd></div>';
        });
    }

    function infoRow(k, v) { return '<div class="ma-modal-row"><dt>' + esc(k) + "</dt><dd>" + v + "</dd></div>"; }

    function renderDetail(d) {
        var m = d.member;
        document.getElementById("ma-modal-title").textContent = (m.nickname || m.loginId) + " 상세";
        var s = statusMeta(m.status);
        document.getElementById("ma-modal-info").innerHTML =
            infoRow("아이디", esc(m.loginId)) +
            infoRow("이메일", esc(m.email)) +
            infoRow("상태", '<span class="badge ' + s.badge + '">' + esc(s.label) + "</span>") +
            infoRow("구독", m.pro ? ("Pro (~" + fmtDate(m.planUntil) + ")") : "무료") +
            infoRow("가입일", fmtDate(m.createdAt)) +
            infoRow("최근 로그인", fmtDateTime(m.lastLoginAt));

        var pays = d.payments || [];
        document.getElementById("ma-modal-payments").innerHTML = pays.length
            ? ('<thead><tr><th>주문명</th><th>금액</th><th>상태</th><th>신청</th></tr></thead><tbody>'
                + pays.map(function (p) {
                    return "<tr><td>" + esc(p.orderName) + '</td><td class="ma-num">₩' + Number(p.amount).toLocaleString()
                        + "</td><td>" + esc(PAY_STATUS[p.status] || p.status) + "</td><td>" + fmtDate(p.createdAt) + "</td></tr>";
                }).join("") + "</tbody>")
            : '<tbody><tr><td class="ma-modal-empty">결제 이력이 없습니다.</td></tr></tbody>';

        var inqs = d.inquiries || [];
        document.getElementById("ma-modal-inquiries").innerHTML = inqs.length
            ? ('<thead><tr><th>제목</th><th>상태</th><th>접수</th><th>답변</th></tr></thead><tbody>'
                + inqs.map(function (i) {
                    return "<tr><td>" + esc(i.title) + "</td><td>" + esc(INQ_STATUS[i.status] || i.status)
                        + "</td><td>" + fmtDate(i.createdAt) + "</td><td>" + (i.answeredAt ? fmtDate(i.answeredAt) : "-") + "</td></tr>";
                }).join("") + "</tbody>")
            : '<tbody><tr><td class="ma-modal-empty">문의 이력이 없습니다.</td></tr></tbody>';
    }

    if (modal) {
        modal.addEventListener("click", function (e) { if (e.target.closest("[data-close]")) { closeDetail(); } });
        document.addEventListener("keydown", function (e) { if (e.key === "Escape" && !modal.hidden) { closeDetail(); } });
    }

    function changeStatus(id, status, btn) {
        var m = findMember(id);
        var msg = confirmMsg(m ? m.status : null, status);
        if (msg && !window.confirm(msg)) { return; }
        btn.disabled = true;
        api("/api/admin/members/" + id + "/status", jsonOpts("PATCH", { status: status })).then(function (r) {
            if (!r.ok) {
                btn.disabled = false;
                flash(msgOf(r, "상태 변경에 실패했습니다."), true);
                return;
            }
            flash("상태를 변경했습니다.");
            pendingFocusId = id; // 재조회 후 이 행 버튼으로 포커스 복원
            load();              // 필터에 안 맞으면 목록에서 빠지도록 재조회
            loadCounts();        // 상태별 카운트 갱신
        }, function () {
            btn.disabled = false;
            flash("상태 변경에 실패했습니다.", true);
        });
    }

    // 구독 부여·연장·회수. 돈이 걸린 조치라 세 경우 모두 확인창을 거친다.
    function planConfirmMsg(m, op) {
        if (op === "REVOKE") { return "구독을 회수할까요? 만료일이 사라지고 즉시 무료로 전환됩니다."; }
        if (m && m.pro) { return "구독을 1개월 연장할까요? 현재 만료일 뒤에 이어집니다."; }
        return "이 회원에게 Pro 1개월을 부여할까요?";
    }
    function changePlan(id, op, btn) {
        var m = findMember(id);
        if (!window.confirm(planConfirmMsg(m, op))) { return; }
        btn.disabled = true;
        var body = op === "REVOKE" ? { op: "REVOKE" } : { op: "EXTEND", months: 1 };
        api("/api/admin/members/" + id + "/plan", jsonOpts("PATCH", body)).then(function (r) {
            if (!r.ok) {
                btn.disabled = false;
                flash(msgOf(r, "구독 변경에 실패했습니다."), true);
                return;
            }
            flash(op === "REVOKE" ? "구독을 회수했습니다." : "구독을 적용했습니다.");
            pendingFocusId = id;
            load();
        }, function () {
            btn.disabled = false;
            flash("구독 변경에 실패했습니다.", true);
        });
    }

    // 정렬 헤더 화살표 표시 동기화. 서버 정렬이라 현재 정렬 상태만 반영한다.
    function syncSortHeaders() {
        Array.prototype.forEach.call(document.querySelectorAll(".ma-th-sort"), function (btn) {
            var on = btn.getAttribute("data-sort") === state.sort.key;
            btn.classList.toggle("is-sorted", on);
            var arrow = btn.querySelector(".ma-arrow");
            if (arrow) { arrow.textContent = on ? (state.sort.dir === "asc" ? "▲" : "▼") : ""; }
        });
    }

    // 번호 페이징: 현재 페이지 주변 창 + 처음/끝 + 생략(...)
    function pageWindow(cur, total) {
        var out = [], i, from = Math.max(1, cur - 1), to = Math.min(total, cur + 3);
        out.push(1);
        if (from > 2) { out.push("gap"); }
        for (i = Math.max(2, from); i <= Math.min(total - 1, to); i++) { out.push(i); }
        if (to < total - 1) { out.push("gap"); }
        if (total > 1) { out.push(total); }
        return out;
    }
    function renderPager(resp) {
        var total = resp.totalPages;
        if (total <= 1) { pagerEl.hidden = true; return; }
        pagerEl.hidden = false;
        prevBtn.disabled = resp.page <= 0;
        nextBtn.disabled = resp.page >= total - 1;
        pagesEl.innerHTML = pageWindow(resp.page + 1, total).map(function (it) {
            if (it === "gap") { return '<span class="ma-page-gap">…</span>'; }
            var active = (it === resp.page + 1) ? " is-active" : "";
            return '<button type="button" class="ma-page-num' + active + '" data-page="' + (it - 1) + '">' + it + "</button>";
        }).join("");
        Array.prototype.forEach.call(pagesEl.querySelectorAll(".ma-page-num"), function (btn) {
            btn.addEventListener("click", function () {
                var p = Number(btn.getAttribute("data-page"));
                if (p !== state.page) { state.page = p; load(); }
            });
        });
    }

    prevBtn.addEventListener("click", function () { if (state.page > 0) { state.page--; load(); } });
    nextBtn.addEventListener("click", function () {
        if (state.resp && state.page < state.resp.totalPages - 1) { state.page++; load(); }
    });
    if (searchEl) {
        searchEl.addEventListener("input", function () {
            if (searchTimer) { clearTimeout(searchTimer); }
            searchTimer = setTimeout(function () {
                state.keyword = searchEl.value.trim();
                state.page = 0;
                load();
            }, 300);
        });
    }

    // 헤더 클릭으로 정렬. 같은 열을 다시 누르면 오름/내림 토글. 정렬 변경 시 첫 페이지로.
    Array.prototype.forEach.call(document.querySelectorAll(".ma-th-sort"), function (btn) {
        btn.addEventListener("click", function () {
            var k = btn.getAttribute("data-sort");
            if (state.sort.key === k) { state.sort.dir = state.sort.dir === "asc" ? "desc" : "asc"; }
            else { state.sort.key = k; state.sort.dir = "asc"; }
            state.page = 0;
            load();
        });
    });

    buildChips();
    load();
    loadCounts();
})();
