(function () {
    "use strict";

    var STATUSES = [
        { value: "DRAFT",     label: "임시저장", badge: "badge-muted", dot: "dot-muted" },
        { value: "PUBLISHED", label: "발행",     badge: "badge-ok",    dot: "dot-ok" },
        { value: "HIDDEN",    label: "숨김",     badge: "badge-warn",  dot: "dot-warn" }
    ];
    function statusMeta(v) {
        for (var i = 0; i < STATUSES.length; i++) { if (STATUSES[i].value === v) { return STATUSES[i]; } }
        return { value: v, label: v, badge: "badge-muted", dot: "dot-muted" };
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
    function fmtDate(iso) {
        if (!iso) { return "—"; }
        var d = new Date(iso);
        if (isNaN(d.getTime())) { return "—"; }
        return d.getFullYear() + "-" + two(d.getMonth() + 1) + "-" + two(d.getDate());
    }

    var tbody = document.getElementById("nt-tbody");
    var addBtn = document.getElementById("nt-add");
    var pagerEl = document.getElementById("nt-pager");
    var prevBtn = document.getElementById("nt-prev");
    var nextBtn = document.getElementById("nt-next");
    var pageInfo = document.getElementById("nt-page-info");
    var flashEl = document.getElementById("nt-flash");

    var modal = document.getElementById("nt-modal");
    var form = document.getElementById("nt-form");
    var formMsg = document.getElementById("nt-form-msg");
    var submitBtn = document.getElementById("nt-submit");
    var modalTitle = document.getElementById("nt-modal-title");
    var fTitle = form.querySelector('[name="title"]');
    var fContent = form.querySelector('[name="content"]');
    var fPinned = form.querySelector('[name="isPinned"]');

    var state = { me: null, page: 0, size: 20, resp: null, editId: null };
    var flashTimer = null;

    function canManage() { return state.me && state.me.role !== "VIEWER"; }
    function flash(msg, isError) {
        flashEl.textContent = msg;
        flashEl.classList.toggle("is-error", !!isError);
        flashEl.hidden = false;
        if (flashTimer) { clearTimeout(flashTimer); }
        flashTimer = setTimeout(function () { flashEl.hidden = true; }, 2600);
    }
    function emptyRow(msg) { return '<tr><td colspan="6" class="nt-empty">' + esc(msg) + "</td></tr>"; }
    function findNotice(id) {
        var list = (state.resp && state.resp.content) || [];
        for (var i = 0; i < list.length; i++) { if (list[i].noticeId === id) { return list[i]; } }
        return null;
    }

    function rowHtml(n) {
        var s = statusMeta(n.status);
        var badge = '<span class="badge ' + s.badge + '"><span class="dot ' + s.dot + '" aria-hidden="true"></span>' + esc(s.label) + "</span>";
        var pin = n.isPinned === "Y" ? '<span class="nt-pin">고정</span>' : "";
        // 발행된 공지만 공개 페이지에 실제 노출되므로, 그 화면을 새 탭으로 확인하는 링크를 단다
        var open = n.status === "PUBLISHED"
            ? '<a class="nt-open" href="/notice/detail?id=' + n.noticeId + '" target="_blank" rel="noopener" title="공개 화면에서 보기">공개 ↗</a>'
            : "";
        var actions;
        if (canManage()) {
            var sel = '<select class="nt-status-select" data-id="' + n.noticeId + '" aria-label="공지 상태 변경">'
                + STATUSES.map(function (o) {
                    return '<option value="' + o.value + '"' + (o.value === n.status ? " selected" : "") + ">" + esc(o.label) + "</option>";
                }).join("")
                + "</select>";
            actions = '<div class="nt-actions">' + sel
                + '<button type="button" class="nt-act" data-act="edit" data-id="' + n.noticeId + '">수정</button>'
                + '<button type="button" class="nt-act nt-act-del" data-act="del" data-id="' + n.noticeId + '">삭제</button>'
                + "</div>";
        } else {
            actions = '<span class="nt-muted">—</span>';
        }
        return "<tr>"
            + '<td><div class="nt-title">' + pin + '<span class="nt-title-text">' + esc(n.title) + "</span>" + open + "</div></td>"
            + '<td class="col-center">' + badge + "</td>"
            + '<td class="col-num nt-views">' + Number(n.viewCnt || 0).toLocaleString() + "</td>"
            + '<td class="nt-muted">' + esc(n.adminName || "-") + "</td>"
            + '<td class="nt-muted">' + fmtDate(n.createdAt) + "</td>"
            + '<td class="col-center">' + actions + "</td>"
            + "</tr>";
    }

    function render() {
        var resp = state.resp;
        var list = resp.content || [];
        tbody.innerHTML = list.length ? list.map(rowHtml).join("") : emptyRow("등록된 공지가 없습니다.");
        wireRows();
        if (resp.totalPages > 1) {
            pagerEl.hidden = false;
            prevBtn.disabled = resp.page <= 0;
            nextBtn.disabled = resp.page >= resp.totalPages - 1;
            pageInfo.textContent = "페이지 " + (resp.page + 1) + " / " + resp.totalPages;
        } else {
            pagerEl.hidden = true;
        }
    }

    function load() {
        tbody.innerHTML = emptyRow("불러오는 중…");
        api("/api/admin/notices?page=" + state.page + "&size=" + state.size).then(function (r) {
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
        Array.prototype.forEach.call(tbody.querySelectorAll(".nt-status-select"), function (sel) {
            sel.addEventListener("change", function () { changeStatus(Number(sel.getAttribute("data-id")), sel.value, sel); });
        });
        Array.prototype.forEach.call(tbody.querySelectorAll(".nt-act[data-act]"), function (btn) {
            var act = btn.getAttribute("data-act");
            btn.addEventListener("click", function () {
                var id = Number(btn.getAttribute("data-id"));
                if (act === "edit") { openEdit(id); } else { removeNotice(id); }
            });
        });
    }

    function changeStatus(id, status, sel) {
        var n = findNotice(id);
        sel.disabled = true;
        api("/api/admin/notices/" + id + "/status", jsonOpts("PATCH", { status: status })).then(function (r) {
            sel.disabled = false;
            if (!r.ok) { if (n) { sel.value = n.status; } flash(msgOf(r, "상태 변경에 실패했습니다."), true); return; }
            if (n) { n.status = status; }
            flash("상태를 변경했습니다.");
        }, function () {
            sel.disabled = false;
            if (n) { sel.value = n.status; }
            flash("상태 변경에 실패했습니다.", true);
        });
    }

    function removeNotice(id) {
        if (!window.confirm("이 공지를 삭제할까요?")) { return; }
        api("/api/admin/notices/" + id, { method: "DELETE" }).then(function (r) {
            if (!r.ok) { flash(msgOf(r, "삭제에 실패했습니다."), true); return; }
            flash("공지를 삭제했습니다.");
            load();
        }, function () { flash("삭제에 실패했습니다.", true); });
    }

    function openCreate() {
        state.editId = null;
        modalTitle.textContent = "공지 작성";
        form.reset();
        formMsg.hidden = true;
        submitBtn.textContent = "저장";
        modal.hidden = false;
        fTitle.focus();
    }
    function openEdit(id) {
        var row = findNotice(id);
        api("/api/admin/notices/" + id).then(function (r) {
            if (!r.ok || !r.body || !r.body.data) { flash(msgOf(r, "공지를 불러오지 못했습니다."), true); return; }
            var d = r.body.data;
            state.editId = id;
            modalTitle.textContent = "공지 수정";
            fTitle.value = d.title || "";
            fContent.value = d.content || "";
            fPinned.checked = row ? row.isPinned === "Y" : false;
            formMsg.hidden = true;
            submitBtn.textContent = "수정";
            modal.hidden = false;
            fTitle.focus();
        });
    }
    function closeModal() { modal.hidden = true; }

    form.addEventListener("submit", function (e) {
        e.preventDefault();
        formMsg.hidden = true;
        var payload = {
            title: fTitle.value.trim(),
            content: fContent.value.trim(),
            isPinned: fPinned.checked ? "Y" : "N"
        };
        if (!payload.title || !payload.content) { formMsg.textContent = "제목과 내용을 입력하세요."; formMsg.hidden = false; return; }
        submitBtn.disabled = true;
        var req = state.editId
            ? api("/api/admin/notices/" + state.editId, jsonOpts("PUT", payload))
            : api("/api/admin/notices", jsonOpts("POST", payload));
        req.then(function (r) {
            submitBtn.disabled = false;
            if (!r.ok) { formMsg.textContent = msgOf(r, "저장에 실패했습니다."); formMsg.hidden = false; return; }
            var edited = state.editId != null;
            closeModal();
            flash(edited ? "공지를 수정했습니다." : "공지를 작성했습니다.");
            load();
        }, function () {
            submitBtn.disabled = false;
            formMsg.textContent = "저장에 실패했습니다."; formMsg.hidden = false;
        });
    });

    Array.prototype.forEach.call(modal.querySelectorAll("[data-close]"), function (el) { el.addEventListener("click", closeModal); });
    document.addEventListener("keydown", function (e) { if (e.key === "Escape" && !modal.hidden) { closeModal(); } });

    prevBtn.addEventListener("click", function () { if (state.page > 0) { state.page--; load(); } });
    nextBtn.addEventListener("click", function () {
        if (state.resp && state.page < state.resp.totalPages - 1) { state.page++; load(); }
    });
    if (addBtn) { addBtn.addEventListener("click", openCreate); }

    // 권한(role)을 먼저 확인해 작성·상태변경·삭제 컨트롤 노출을 정한 뒤 목록을 그린다
    api("/api/admin/auth/me").then(function (r) {
        if (r.ok && r.body && r.body.data) { state.me = r.body.data; }
        if (addBtn && canManage()) { addBtn.hidden = false; }
        load();
    });
})();
