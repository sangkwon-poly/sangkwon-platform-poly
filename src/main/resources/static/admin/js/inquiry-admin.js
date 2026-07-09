(function () {
    "use strict";

    var STATUSES = [
        { value: "OPEN",     label: "대기중",   badge: "badge-warn",  dot: "dot-warn" },
        { value: "ANSWERED", label: "답변완료", badge: "badge-ok",    dot: "dot-ok" },
        { value: "CLOSED",   label: "닫힘",     badge: "badge-muted", dot: "dot-muted" }
    ];
    function statusMeta(v) {
        for (var i = 0; i < STATUSES.length; i++) { if (STATUSES[i].value === v) { return STATUSES[i]; } }
        return { value: v, label: v, badge: "badge-muted", dot: "dot-muted" };
    }
    var FILTERS = [{ key: "ALL", label: "전체" }].concat(
        STATUSES.map(function (s) { return { key: s.value, label: s.label }; }));

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
    function fmtDate(iso) {
        if (!iso) { return "—"; }
        var d = new Date(iso);
        if (isNaN(d.getTime())) { return "—"; }
        return d.getFullYear() + "-" + two(d.getMonth() + 1) + "-" + two(d.getDate());
    }
    function fmtDateTime(iso) {
        if (!iso) { return "—"; }
        var d = new Date(iso);
        if (isNaN(d.getTime())) { return "—"; }
        return d.getFullYear() + "-" + two(d.getMonth() + 1) + "-" + two(d.getDate())
            + " " + two(d.getHours()) + ":" + two(d.getMinutes());
    }

    var tbody = document.getElementById("iq-tbody");
    var chipsEl = document.getElementById("iq-chips");
    var pagerEl = document.getElementById("iq-pager");
    var prevBtn = document.getElementById("iq-prev");
    var nextBtn = document.getElementById("iq-next");
    var pageInfo = document.getElementById("iq-page-info");
    var metaEl = document.getElementById("iq-meta");
    var flashEl = document.getElementById("iq-flash");

    var modal = document.getElementById("iq-modal");
    var form = document.getElementById("iq-form");
    var formMsg = document.getElementById("iq-form-msg");
    var submitBtn = document.getElementById("iq-submit");
    var modalTitle = document.getElementById("iq-modal-title");
    var qTitle = document.getElementById("iq-q-title");
    var qMeta = document.getElementById("iq-q-meta");
    var qContent = document.getElementById("iq-q-content");
    var fAnswer = form.querySelector('[name="answer"]');

    var state = { me: null, filter: "ALL", page: 0, size: 20, resp: null, answerId: null };
    var flashTimer = null;

    function canManage() { return state.me && state.me.role !== "VIEWER"; }
    function flash(msg, isError) {
        flashEl.textContent = msg;
        flashEl.classList.toggle("is-error", !!isError);
        flashEl.hidden = false;
        if (flashTimer) { clearTimeout(flashTimer); }
        flashTimer = setTimeout(function () { flashEl.hidden = true; }, 2600);
    }
    function emptyRow(msg) { return '<tr><td colspan="6" class="iq-empty">' + esc(msg) + "</td></tr>"; }
    function findInquiry(id) {
        var list = (state.resp && state.resp.content) || [];
        for (var i = 0; i < list.length; i++) { if (list[i].inquiryId === id) { return list[i]; } }
        return null;
    }

    function buildChips() {
        chipsEl.innerHTML = FILTERS.map(function (f) {
            return '<button type="button" class="iq-chip" data-filter="' + f.key + '">' + esc(f.label) + "</button>";
        }).join("");
        Array.prototype.forEach.call(chipsEl.querySelectorAll(".iq-chip"), function (chip) {
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
        Array.prototype.forEach.call(chipsEl.querySelectorAll(".iq-chip"), function (chip) {
            chip.classList.toggle("is-active", chip.getAttribute("data-filter") === state.filter);
        });
    }

    function rowHtml(i) {
        var s = statusMeta(i.status);
        var badge = '<span class="badge ' + s.badge + '"><span class="dot ' + s.dot + '" aria-hidden="true"></span>' + esc(s.label) + "</span>";
        var actions;
        if (canManage()) {
            var answer = '<button type="button" class="iq-act iq-act-primary" data-act="answer" data-id="' + i.inquiryId + '">'
                + (i.status === "OPEN" ? "답변" : "답변 보기") + "</button>";
            var close = i.status !== "CLOSED"
                ? '<button type="button" class="iq-act iq-act-close" data-act="close" data-id="' + i.inquiryId + '">닫기</button>'
                : "";
            actions = '<div class="iq-actions">' + answer + close + "</div>";
        } else {
            actions = '<span class="iq-muted">—</span>';
        }
        return "<tr>"
            + '<td><span class="iq-title-text">' + esc(i.title) + "</span></td>"
            + '<td class="iq-member">' + esc(i.memberName || "-") + "</td>"
            + '<td class="col-center">' + badge + "</td>"
            + '<td class="iq-muted">' + fmtDateTime(i.answeredAt) + "</td>"
            + '<td class="iq-muted">' + fmtDate(i.createdAt) + "</td>"
            + '<td class="col-center">' + actions + "</td>"
            + "</tr>";
    }

    function render() {
        var resp = state.resp;
        var list = resp.content || [];
        tbody.innerHTML = list.length ? list.map(rowHtml).join("") : emptyRow("표시할 문의가 없습니다.");
        wireRows();
        if (resp.totalPages > 1) {
            pagerEl.hidden = false;
            prevBtn.disabled = resp.page <= 0;
            nextBtn.disabled = resp.page >= resp.totalPages - 1;
            pageInfo.textContent = "페이지 " + (resp.page + 1) + " / " + resp.totalPages;
        } else {
            pagerEl.hidden = true;
        }
        metaEl.textContent = "총 " + Number(resp.totalElements).toLocaleString() + "건";
    }

    function load() {
        tbody.innerHTML = emptyRow("불러오는 중…");
        var q = "/api/admin/inquiries?page=" + state.page + "&size=" + state.size;
        if (state.filter !== "ALL") { q += "&status=" + state.filter; }
        api(q).then(function (r) {
            if (r.status === 401) { return; }
            if (r.status === 403) { tbody.innerHTML = emptyRow("접근 권한이 없습니다."); pagerEl.hidden = true; return; }
            if (!r.ok || !r.body || !r.body.data) { tbody.innerHTML = emptyRow("목록을 불러오지 못했습니다."); pagerEl.hidden = true; return; }
            state.resp = r.body.data;
            render();
        }, function () {
            tbody.innerHTML = emptyRow("목록을 불러오지 못했습니다.");
            pagerEl.hidden = true;
        });
    }

    function wireRows() {
        Array.prototype.forEach.call(tbody.querySelectorAll(".iq-act[data-act]"), function (btn) {
            var act = btn.getAttribute("data-act");
            btn.addEventListener("click", function () {
                var id = Number(btn.getAttribute("data-id"));
                if (act === "answer") { openAnswer(id); } else { closeInquiry(id); }
            });
        });
    }

    function openAnswer(id) {
        var row = findInquiry(id);
        api("/api/admin/inquiries/" + id).then(function (r) {
            if (!r.ok || !r.body || !r.body.data) { flash(msgOf(r, "문의를 불러오지 못했습니다."), true); return; }
            var d = r.body.data;
            state.answerId = id;
            modalTitle.textContent = (d.answer ? "문의 답변 수정" : "문의 답변");
            qTitle.textContent = d.title || "";
            var who = (d.memberName || (row && row.memberName) || "익명");
            qMeta.textContent = who + " · " + fmtDate(d.createdAt);
            qContent.textContent = d.content || "";
            fAnswer.value = d.answer || "";
            formMsg.hidden = true;
            modal.hidden = false;
            fAnswer.focus();
        });
    }
    function closeModal() { modal.hidden = true; }

    form.addEventListener("submit", function (e) {
        e.preventDefault();
        formMsg.hidden = true;
        var answer = fAnswer.value.trim();
        if (!answer) { formMsg.textContent = "답변 내용을 입력하세요."; formMsg.hidden = false; return; }
        if (state.answerId == null) { return; }
        submitBtn.disabled = true;
        api("/api/admin/inquiries/" + state.answerId + "/answer", jsonOpts("POST", { answer: answer })).then(function (r) {
            submitBtn.disabled = false;
            if (!r.ok) { formMsg.textContent = msgOf(r, "답변 등록에 실패했습니다."); formMsg.hidden = false; return; }
            closeModal();
            flash("답변을 등록했습니다.");
            load();
        }, function () {
            submitBtn.disabled = false;
            formMsg.textContent = "답변 등록에 실패했습니다."; formMsg.hidden = false;
        });
    });

    function closeInquiry(id) {
        if (!window.confirm("이 문의를 닫을까요?")) { return; }
        api("/api/admin/inquiries/" + id + "/close", { method: "PATCH" }).then(function (r) {
            if (!r.ok) { flash(msgOf(r, "닫기에 실패했습니다."), true); return; }
            flash("문의를 닫았습니다.");
            load();
        }, function () { flash("닫기에 실패했습니다.", true); });
    }

    Array.prototype.forEach.call(modal.querySelectorAll("[data-close]"), function (el) { el.addEventListener("click", closeModal); });
    document.addEventListener("keydown", function (e) { if (e.key === "Escape" && !modal.hidden) { closeModal(); } });

    prevBtn.addEventListener("click", function () { if (state.page > 0) { state.page--; load(); } });
    nextBtn.addEventListener("click", function () {
        if (state.resp && state.page < state.resp.totalPages - 1) { state.page++; load(); }
    });

    buildChips();
    // 권한을 먼저 확인해 답변·닫기 노출을 정한 뒤 목록을 그린다
    api("/api/admin/auth/me").then(function (r) {
        if (r.ok && r.body && r.body.data) { state.me = r.body.data; }
        load();
    });
})();
