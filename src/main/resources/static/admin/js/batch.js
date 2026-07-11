(function () {
    "use strict";

    function api(path, opts) {
        return fetch(path, opts).then(function (r) {
            return r.json().catch(function () { return null; }).then(function (b) {
                return { ok: r.ok, status: r.status, body: b };
            });
        });
    }
    function jsonOpts(method) { return { method: method, headers: { "Content-Type": "application/json" } }; }
    function esc(s) { var d = document.createElement("div"); d.textContent = (s == null) ? "" : String(s); return d.innerHTML.replace(/"/g, "&quot;").replace(/'/g, "&#39;"); }
    function pad(n) { return String(n).padStart(2, "0"); }
    function fmtTime(iso) {
        if (!iso) { return "-"; }
        var d = new Date(iso);
        return pad(d.getMonth() + 1) + "-" + pad(d.getDate()) + " " + pad(d.getHours()) + ":" + pad(d.getMinutes());
    }
    function dur(start, end) {
        if (!start || !end) { return "-"; }
        var s = Math.max(0, Math.round((new Date(end) - new Date(start)) / 1000));
        return s < 60 ? s + "초" : Math.floor(s / 60) + "분 " + (s % 60) + "초";
    }
    function num(n) { return (n == null) ? "-" : Number(n).toLocaleString(); }
    function ageLabel(days) { return (days == null) ? "" : (days <= 0 ? "오늘" : days + "일 전"); }
    function ageTag(r) { return (r.ageDays == null) ? "" : ' <span class="bt-age' + (r.stale ? " is-stale" : "") + '">' + ageLabel(r.ageDays) + "</span>"; }

    function statusBadge(status, running) {
        if (running) { return '<span class="badge badge-warn"><span class="dot dot-warn is-live" aria-hidden="true"></span>진행 중</span>'; }
        var map = {
            SUCCESS: ["badge-ok", "dot-ok", "정상"],
            PARTIAL: ["badge-warn", "dot-warn", "부분"],
            RUNNING: ["badge-warn", "dot-warn", "진행 중"],
            FAILED: ["badge-danger", "dot-danger", "실패"]
        };
        if (!status) { return '<span class="badge badge-muted">미적재</span>'; }
        var m = map[status] || ["badge-muted", "dot-muted", status];
        return '<span class="badge ' + m[0] + '"><span class="dot ' + m[1] + '" aria-hidden="true"></span>' + esc(m[2]) + "</span>";
    }
    function tierBadge(tier) {
        return tier === "APP"
            ? '<span class="badge badge-info"><span class="dot dot-info" aria-hidden="true"></span>앱 적재</span>'
            : '<span class="badge badge-muted">오프라인</span>';
    }

    var tbody = document.querySelector(".bt-catalog tbody");
    var refreshBtn = document.getElementById("batch-refresh");
    var flashEl = document.querySelector(".bt-flash");
    var modal = document.getElementById("bt-modal");
    var statEls = {
        total: document.querySelector('[data-stat="total"]'),
        loaded: document.querySelector('[data-stat="loaded"]'),
        records: document.querySelector('[data-stat="records"]'),
        running: document.querySelector('[data-stat="running"]')
    };

    var rowsByCode = {};
    var poller = null;
    var flashTimer = null;

    function flash(msg) {
        if (!flashEl) { return; }
        flashEl.textContent = msg;
        flashEl.hidden = false;
        if (flashTimer) { clearTimeout(flashTimer); }
        flashTimer = setTimeout(function () { flashEl.hidden = true; }, 2600);
    }

    function emptyRow(cols, msg) { return '<tr><td colspan="' + cols + '" class="ops-empty">' + esc(msg) + "</td></tr>"; }

    // 카탈로그 상태: 실행 중 > 적재됨(레코드 있음) > 미적재
    function catStatus(r) {
        if (r.running) { return '<span class="badge badge-warn"><span class="dot dot-warn is-live" aria-hidden="true"></span>진행 중</span>'; }
        if (r.loaded) { return '<span class="badge badge-ok"><span class="dot dot-ok" aria-hidden="true"></span>적재됨</span>'; }
        return '<span class="badge badge-muted">미적재</span>';
    }

    function rowHtml(r) {
        var action = r.appRunnable
            ? '<button type="button" class="bt-run" data-code="' + esc(r.code) + '"' + (r.running ? " disabled" : "") + ">" + (r.running ? "진행 중" : "적재") + "</button>"
            : '<button type="button" class="bt-detail" data-code="' + esc(r.code) + '">상세</button>';
        return '<tr data-code="' + esc(r.code) + '" class="bt-row' + (r.running ? " is-running" : "") + '">'
            + '<td><span class="bt-name">' + esc(r.label) + '</span><span class="bt-note">' + esc(r.note) + "</span></td>"
            + '<td class="col-center">' + tierBadge(r.tier) + "</td>"
            + '<td class="col-num mono">' + num(r.recordCount) + "</td>"
            + '<td class="bt-muted">' + esc(r.dataPeriod || "-") + "</td>"
            + '<td class="bt-muted">' + (r.lastLoadedAt ? (fmtTime(r.lastLoadedAt) + ageTag(r)) : "-") + "</td>"
            + '<td class="col-center">' + catStatus(r) + "</td>"
            + '<td class="col-center bt-act">' + action + "</td>"
            + "</tr>";
    }

    function setStats(rows) {
        var running = rows.filter(function (r) { return r.running; }).length;
        var loaded = rows.filter(function (r) { return r.loaded; }).length;
        var records = rows.reduce(function (s, r) { return s + (r.recordCount || 0); }, 0);
        if (statEls.total) { statEls.total.textContent = rows.length; }
        if (statEls.loaded) { statEls.loaded.textContent = loaded; }
        if (statEls.records) { statEls.records.textContent = records.toLocaleString(); }
        if (statEls.running) { statEls.running.textContent = running; }
        return running;
    }

    function render(rows) {
        rowsByCode = {};
        rows.forEach(function (r) { rowsByCode[r.code] = r; });
        if (tbody) { tbody.innerHTML = rows.length ? rows.map(rowHtml).join("") : emptyRow(7, "데이터셋이 없습니다."); }
        var running = setStats(rows);
        // 진행 중이 있으면 자동 폴링, 없으면 중단
        if (running > 0) { startPoll(); } else { stopPoll(); }
    }

    function loadCatalog(quiet) {
        if (!quiet && tbody) { tbody.innerHTML = emptyRow(7, "불러오는 중…"); }
        return api("/api/admin/ops/batch/catalog").then(function (r) {
            if (r.status === 401) { return; }
            if (r.status === 403) { if (tbody) { tbody.innerHTML = emptyRow(7, "SUPER_ADMIN만 볼 수 있습니다."); } return; }
            if (!r.ok) { if (!quiet && tbody) { tbody.innerHTML = emptyRow(7, "카탈로그를 불러오지 못했습니다."); } return; }
            render((r.body && r.body.data) ? r.body.data : []);
        }, function () {
            if (!quiet && tbody) { tbody.innerHTML = emptyRow(7, "카탈로그를 불러오지 못했습니다."); }
        });
    }

    function startPoll() {
        if (poller) { return; }
        poller = setInterval(function () { loadCatalog(true); }, 4000);
    }
    function stopPoll() {
        if (poller) { clearInterval(poller); poller = null; }
    }

    // ── 모달 ──────────────────────────────────────────────
    function q(sel) { return modal.querySelector(sel); }

    function openModal(code) {
        var r = rowsByCode[code];
        if (!r) { return; }
        q("#bt-modal-title").textContent = r.label;
        q('[data-modal="note"]').textContent = r.note || "";
        q('[data-modal="tier"]').innerHTML = tierBadge(r.tier);
        q('[data-modal="cycle"]').textContent = r.cycle || "-";
        q('[data-modal="table"]').textContent = r.table;
        q('[data-modal="count"]').textContent = num(r.recordCount);
        q('[data-modal="period"]').textContent = r.dataPeriod || "-";
        q('[data-modal="loaded"]').textContent = r.lastLoadedAt
            ? (fmtTime(r.lastLoadedAt) + (r.ageDays != null ? " (" + ageLabel(r.ageDays) + ")" : ""))
            : "미적재";

        var srcLink = q('[data-modal="source"]');
        if (r.sourceUrl) { srcLink.href = r.sourceUrl; srcLink.style.display = ""; }
        else { srcLink.style.display = "none"; }

        var appBox = q('[data-modal="app"]');
        var offBox = q('[data-modal="offline"]');
        appBox.hidden = !r.appRunnable;
        offBox.hidden = r.appRunnable;

        if (r.appRunnable) {
            var runBtn = q('[data-modal="run"]');
            runBtn.dataset.code = r.code;
            runBtn.disabled = !!r.running;
            runBtn.textContent = r.running ? "진행 중…" : "지금 적재";
            // 진행 중일 때만 초기화(스테일 RUNNING 탈출구)를 노출한다
            var resetBtn = q('[data-modal="reset"]');
            var resetNote = q('[data-modal="reset-note"]');
            resetBtn.dataset.code = r.code;
            resetBtn.hidden = !r.running;
            resetBtn.disabled = false;
            resetBtn.textContent = "진행 중 상태 초기화";
            resetNote.hidden = !r.running;
        }

        renderHistory([{ loading: true }]);
        modal.hidden = false;
        loadHistory(code);
    }
    function closeModal() { modal.hidden = true; }

    function histRow(b) {
        return "<tr>"
            + "<td>" + fmtTime(b.startedAt) + "</td>"
            + '<td class="col-num mono">' + num(b.processedCnt) + "</td>"
            + '<td class="col-num bt-muted">' + dur(b.startedAt, b.endedAt) + "</td>"
            + '<td class="col-center">' + statusBadge(b.status, b.status === "RUNNING") + "</td>"
            + '<td class="bt-muted">' + esc(b.triggeredBy || "-") + "</td>"
            + "</tr>";
    }
    function renderHistory(rows) {
        var body = modal.querySelector(".bt-hist tbody");
        if (!body) { return; }
        if (rows.length === 1 && rows[0].loading) { body.innerHTML = emptyRow(5, "불러오는 중…"); return; }
        body.innerHTML = rows.length ? rows.map(histRow).join("") : emptyRow(5, "실행 이력이 없습니다.");
    }
    function loadHistory(code) {
        api("/api/admin/ops/batch/" + encodeURIComponent(code) + "/history").then(function (r) {
            renderHistory((r.ok && r.body && r.body.data) ? r.body.data : []);
        }, function () { renderHistory([]); });
    }

    function trigger(code, btn) {
        if (btn) { btn.disabled = true; btn.textContent = "시작 중…"; }
        api("/api/admin/ops/batch/" + encodeURIComponent(code) + "/run", jsonOpts("POST")).then(function (r) {
            if (r.status === 401) { return; }
            var msg = (r.body && r.body.message) ? r.body.message : null;
            if (r.ok) {
                flash("적재를 시작했습니다. 진행 상황이 곧 반영됩니다.");
                closeModal();
                startPoll();
                loadCatalog(true);
                return;
            }
            flash(msg || "적재를 시작하지 못했습니다.");
            if (btn) { btn.disabled = false; btn.textContent = "지금 적재"; }
        }, function () {
            flash("적재 요청에 실패했습니다.");
            if (btn) { btn.disabled = false; btn.textContent = "지금 적재"; }
        });
    }

    // 스테일 RUNNING 초기화: 중단된 좀비로 적재가 막혔을 때 서버 재시작 없이 푼다.
    function resetStuck(code, btn) {
        if (!window.confirm("이 데이터셋의 '진행 중' 상태를 초기화할까요? 실제 적재가 돌고 있지 않을 때만 사용하세요.")) { return; }
        if (btn) { btn.disabled = true; btn.textContent = "초기화 중…"; }
        api("/api/admin/ops/batch/" + encodeURIComponent(code) + "/reset", jsonOpts("POST")).then(function (r) {
            if (r.status === 401) { return; }
            if (r.ok) {
                flash("진행 중 상태를 초기화했습니다. 다시 적재할 수 있습니다.");
                closeModal();
                loadCatalog(true);
                return;
            }
            flash((r.body && r.body.message) || "초기화하지 못했습니다.");
            if (btn) { btn.disabled = false; btn.textContent = "진행 중 상태 초기화"; }
        }, function () {
            flash("초기화 요청에 실패했습니다.");
            if (btn) { btn.disabled = false; btn.textContent = "진행 중 상태 초기화"; }
        });
    }

    // ── 이벤트 ────────────────────────────────────────────
    if (refreshBtn) { refreshBtn.addEventListener("click", function () { loadCatalog(false); }); }

    if (tbody) {
        tbody.addEventListener("click", function (e) {
            var runBtn = e.target.closest(".bt-run");
            var detailBtn = e.target.closest(".bt-detail");
            if (runBtn) { openModal(runBtn.dataset.code); return; }
            if (detailBtn) { openModal(detailBtn.dataset.code); return; }
            var row = e.target.closest(".bt-row");
            if (row) { openModal(row.dataset.code); }
        });
    }

    if (modal) {
        modal.addEventListener("click", function (e) {
            if (e.target.closest("[data-close]")) { closeModal(); return; }
            var runBtn = e.target.closest('[data-modal="run"]');
            if (runBtn) { trigger(runBtn.dataset.code, runBtn); return; }
            var resetBtn = e.target.closest('[data-modal="reset"]');
            if (resetBtn) { resetStuck(resetBtn.dataset.code, resetBtn); return; }
        });
    }
    document.addEventListener("keydown", function (e) {
        if (e.key === "Escape" && modal && !modal.hidden) { closeModal(); }
    });

    loadCatalog(false);
})();
