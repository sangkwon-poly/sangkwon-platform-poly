(function () {
    "use strict";

    var SOURCE_LABEL = { BIZINFO: "기업마당", KSTARTUP: "K-Startup" };
    var STATUS = {
        RECRUITING: { label: "모집중", badge: "badge-ok", dot: "dot-ok" },
        CLOSING: { label: "마감 임박", badge: "badge-danger", dot: "dot-danger" },
        UPCOMING: { label: "예정", badge: "badge-warn", dot: "dot-warn" },
        CLOSED: { label: "마감", badge: "badge-muted", dot: "dot-muted" },
        ALWAYS: { label: "상시", badge: "badge-muted", dot: "dot-muted" }
    };

    function esc(s) { var d = document.createElement("div"); d.textContent = (s == null) ? "" : String(s); return d.innerHTML.replace(/"/g, "&quot;").replace(/'/g, "&#39;"); }
    function attr(s) { return esc(s); }
    // href에는 http/https만 허용해 javascript: 등 스킴 주입(저장형 XSS)을 막는다
    function safeUrl(u) { if (!u) { return ""; } var s = String(u).trim(); return /^https?:\/\//i.test(s) ? s : ""; }
    function api(path, opts) {
        return fetch(path, opts).then(function (res) {
            return res.json().catch(function () { return null; }).then(function (b) { return { ok: res.ok, status: res.status, body: b }; });
        });
    }
    function jsonOpts(method, body) { return { method: method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }; }
    function param(name) { var m = new RegExp("[?&]" + name + "=([^&]*)").exec(location.search); return m ? decodeURIComponent(m[1].replace(/\+/g, " ")) : ""; }
    function statusMeta(v) { return STATUS[v] || { label: v, badge: "badge-muted", dot: "dot-muted" }; }
    function remain(c) {
        if (c.status === "ALWAYS") { return "상시"; }
        if (c.status === "CLOSED") { return "마감"; }
        if (c.status === "UPCOMING") { return "예정"; }
        if (c.dday == null) { return "-"; }
        return c.dday === 0 ? "D-DAY" : "D-" + c.dday;
    }
    function period(c) {
        if (c.status === "ALWAYS") { return c.applyPeriodRaw || "상시 접수"; }
        if (c.applyBgngDe && c.applyEndDe) { return c.applyBgngDe + " ~ " + c.applyEndDe; }
        if (c.applyEndDe) { return "~ " + c.applyEndDe; }
        return c.applyPeriodRaw || "-";
    }

    var root = document.getElementById("sd-root");
    var flashEl = document.getElementById("sd-flash");
    var flashTimer = null;
    var state = { detail: null, source: param("source"), id: param("id"), busy: false };

    function flash(msg, isError) {
        flashEl.textContent = msg;
        flashEl.classList.toggle("is-error", !!isError);
        flashEl.hidden = false;
        if (flashTimer) { clearTimeout(flashTimer); }
        flashTimer = setTimeout(function () { flashEl.hidden = true; }, 2600);
    }

    function load() {
        if (!state.source || !state.id) { notFound(); return; }
        api("/api/admin/support-programs/" + encodeURIComponent(state.source) + "/" + encodeURIComponent(state.id))
            .then(function (r) {
                if (r.status === 403) { root.innerHTML = '<p class="sd-loading">최고관리자(SUPER_ADMIN)만 접근할 수 있습니다.</p>'; return; }
                if (!r.ok || !r.body || !r.body.data) { notFound(); return; }
                state.detail = r.body.data;
                renderView();
            }, notFound);
    }
    function notFound() {
        root.innerHTML = '<div class="sd-missing"><p class="sd-missing-title">지원사업을 찾을 수 없습니다.</p>'
            + '<a class="btn btn-primary" href="/admin/support-admin">목록으로</a></div>';
    }

    function infoCell(label, value, strong) {
        return '<div class="sd-info-cell"><span class="sd-info-label">' + esc(label) + "</span>"
            + '<span class="sd-info-value' + (strong ? " is-strong" : "") + '">' + esc(value || "-") + "</span></div>";
    }
    function section(title, note, bodyHtml) {
        return '<section class="sd-section"><h2 class="sd-section-title">' + esc(title)
            + (note ? ' <span class="sd-note">' + esc(note) + "</span>" : "") + "</h2>" + bodyHtml + "</section>";
    }
    function ksCell(label, value) {
        if (!value) { return ""; }
        return '<div class="sd-ks-cell"><span class="sd-ks-label">' + esc(label) + "</span>"
            + '<span class="sd-ks-value">' + esc(value) + "</span></div>";
    }

    function renderView() {
        var d = state.detail;
        var st = statusMeta(d.status);
        var org = [d.kstartup ? d.kstartup.supervisor : null, d.kstartup ? d.kstartup.operator : null].filter(Boolean).map(esc).join(" · ");
        var visBadge = d.visible
            ? '<span class="sd-vis sd-vis-on">노출 중</span>'
            : '<span class="sd-vis sd-vis-off">숨김</span>';

        var ks = "";
        if (d.kstartup) {
            var k = d.kstartup;
            var methods = (k.applyMethods && k.applyMethods.length) ? k.applyMethods.join(", ") : "";
            var cells = ksCell("창업기간", k.foundingPeriod) + ksCell("대상 연령", k.targetAge) + ksCell("신청방법", methods)
                + ksCell("신청 제외대상", k.exclusion) + ksCell("우대사항", k.preference)
                + ksCell("주관기관", k.supervisor) + ksCell("수행기관", k.operator);
            if (cells) { ks = section("K-Startup 상세 정보", "원본 제공, 수정 불가", '<div class="sd-ks-grid">' + cells + "</div>"); }
        }

        root.innerHTML = ""
            + '<div class="sd-top">'
            + '<span class="sd-type">' + esc(d.typeLabel) + "</span>"
            + '<span class="sd-src sd-src-' + esc(d.sourceCd) + '">' + esc(SOURCE_LABEL[d.sourceCd] || d.sourceCd) + "</span>"
            + '<span class="badge ' + st.badge + '"><span class="dot ' + st.dot + '"></span>' + esc(st.label) + "</span>"
            + visBadge
            + "</div>"
            + '<h1 class="sd-title">' + esc(d.title) + "</h1>"
            + (org ? '<p class="sd-org">' + org + "</p>" : "")
            + '<div class="sd-actions">'
            + '<button type="button" class="sd-btn sd-btn-primary" id="sd-edit">수정</button>'
            + '<button type="button" class="sd-btn" id="sd-toggle">' + (d.visible ? "숨김으로" : "노출로") + "</button>"
            + (safeUrl(d.detailUrl) ? '<a class="sd-btn" href="' + attr(safeUrl(d.detailUrl)) + '" target="_blank" rel="noopener">원문 보기</a>' : "")
            + '<a class="sd-btn" href="/support/detail?source=' + encodeURIComponent(d.sourceCd) + "&id=" + encodeURIComponent(d.programId) + '" target="_blank" rel="noopener">공개 상세 미리보기</a>'
            + "</div>"
            + '<div class="sd-info">'
            + infoCell("신청기간", period(d))
            + infoCell("남은 기간", remain(d), true)
            + infoCell("지역", d.region)
            + infoCell("문의", d.contact)
            + "</div>"
            + (d.target ? section("지원대상", "원문", '<p class="sd-text">' + esc(d.target) + "</p>") : "")
            + (d.description ? section("지원내용", null, '<p class="sd-text">' + esc(d.description) + "</p>") : "")
            + ks;

        document.getElementById("sd-edit").addEventListener("click", renderEdit);
        document.getElementById("sd-toggle").addEventListener("click", toggleVisibility);
    }

    function field(label, id, value, type) {
        var input = (type === "textarea")
            ? '<textarea class="sd-input" id="' + id + '" rows="4">' + esc(value || "") + "</textarea>"
            : '<input class="sd-input" id="' + id + '" type="' + (type || "text") + '" value="' + attr(value || "") + '">';
        return '<div class="sd-field"><label class="sd-label" for="' + id + '">' + esc(label) + "</label>" + input + "</div>";
    }

    function renderEdit() {
        var d = state.detail;
        root.innerHTML = ""
            + '<div class="sd-edit-head"><h1 class="sd-title">공고 수정</h1>'
            + '<p class="sd-edit-note">배치가 다시 적재하면 덮어써질 수 있는 필드입니다. 원본 식별자와 유형, K-Startup 상세는 수정할 수 없습니다.</p></div>'
            + '<form id="sd-form" class="sd-form">'
            + field("제목", "f-title", d.title)
            + '<div class="sd-field-row">' + field("지역", "f-region", d.region) + field("문의처", "f-contact", d.contact) + "</div>"
            + '<div class="sd-field-row">' + field("신청 시작일", "f-bgn", d.applyBgngDe, "date") + field("신청 마감일", "f-end", d.applyEndDe, "date") + "</div>"
            + field("신청기간 원문 (상시 등, 마감일 없을 때)", "f-raw", d.applyPeriodRaw)
            + field("원문 URL", "f-url", d.detailUrl)
            + field("지원대상", "f-target", d.target, "textarea")
            + field("지원내용", "f-desc", d.description, "textarea")
            + '<div class="sd-form-actions">'
            + '<button type="submit" class="sd-btn sd-btn-primary">저장</button>'
            + '<button type="button" class="sd-btn" id="sd-cancel">취소</button>'
            + "</div>"
            + "</form>";
        document.getElementById("sd-form").addEventListener("submit", save);
        document.getElementById("sd-cancel").addEventListener("click", renderView);
    }

    function val(id) { var el = document.getElementById(id); var v = el ? el.value.trim() : ""; return v === "" ? null : v; }

    function save(e) {
        e.preventDefault();
        if (state.busy) { return; }
        var title = val("f-title");
        if (!title) { flash("제목은 필수입니다.", true); return; }
        var body = {
            title: title, region: val("f-region"), target: val("f-target"), description: val("f-desc"),
            contact: val("f-contact"), detailUrl: val("f-url"),
            applyBgngDe: val("f-bgn"), applyEndDe: val("f-end"), applyPeriodRaw: val("f-raw")
        };
        state.busy = true;
        api("/api/admin/support-programs/" + encodeURIComponent(state.source) + "/" + encodeURIComponent(state.id),
            jsonOpts("PATCH", body)).then(function (r) {
                state.busy = false;
                if (!r.ok || !r.body || !r.body.data) { flash((r.body && r.body.message) || "저장에 실패했습니다.", true); return; }
                state.detail = r.body.data;
                renderView();
                flash("수정 내용을 저장했습니다.");
            }, function () { state.busy = false; flash("저장에 실패했습니다.", true); });
    }

    function toggleVisibility() {
        if (state.busy) { return; }
        var next = !state.detail.visible;
        state.busy = true;
        api("/api/admin/support-programs/" + encodeURIComponent(state.source) + "/" + encodeURIComponent(state.id) + "/visibility",
            jsonOpts("PATCH", { visible: next })).then(function (r) {
                state.busy = false;
                if (!r.ok) { flash("노출 변경에 실패했습니다.", true); return; }
                state.detail.visible = next;
                renderView();
                flash(next ? "노출로 바꿨습니다." : "숨김으로 바꿨습니다.");
            }, function () { state.busy = false; flash("노출 변경에 실패했습니다.", true); });
    }

    load();
})();
