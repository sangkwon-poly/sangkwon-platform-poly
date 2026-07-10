(function () {
  "use strict";

  var SOURCE_LABEL = { BIZINFO: "기업마당", KSTARTUP: "K-Startup" };

  function esc(s) {
    var d = document.createElement("div");
    d.textContent = (s == null) ? "" : String(s);
    return d.innerHTML.replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  }
  // href에는 http/https만 허용해 javascript: 등 스킴 주입(저장형 XSS)을 막는다
  function safeUrl(u) {
    if (!u) { return ""; }
    var s = String(u).trim();
    return /^https?:\/\//i.test(s) ? s : "";
  }
  function $(id) { return document.getElementById(id); }
  function param(name) {
    var m = new RegExp("[?&]" + name + "=([^&]*)").exec(location.search);
    return m ? decodeURIComponent(m[1].replace(/\+/g, " ")) : "";
  }

  function statusLabel(c) {
    switch (c.status) {
      case "RECRUITING": return { cls: "ok", text: "모집중" };
      case "CLOSING": return { cls: "danger", text: "마감 임박" };
      case "UPCOMING": return { cls: "info", text: "예정" };
      case "CLOSED": return { cls: "muted", text: "마감" };
      case "ALWAYS": return { cls: "always", text: "상시" };
      default: return { cls: "muted", text: c.status };
    }
  }

  function periodText(c) {
    if (c.status === "ALWAYS") { return c.applyPeriodRaw || "상시 접수"; }
    if (c.applyBgngDe && c.applyEndDe) { return c.applyBgngDe + " ~ " + c.applyEndDe; }
    if (c.applyEndDe) { return "~ " + c.applyEndDe; }
    return c.applyPeriodRaw || "-";
  }

  function remainText(c) {
    if (c.status === "ALWAYS") { return "상시"; }
    if (c.status === "CLOSED") { return "마감"; }
    if (c.status === "UPCOMING") { return "예정"; }
    if (c.dday == null) { return "-"; }
    return c.dday === 0 ? "D-DAY" : "D-" + c.dday;
  }

  function infoCell(label, value, strong) {
    return '<div class="spd-info-cell">'
      + '<span class="spd-info-label">' + esc(label) + "</span>"
      + '<span class="spd-info-value' + (strong ? " is-strong" : "") + '">' + esc(value || "-") + "</span>"
      + "</div>";
  }

  function ksCell(label, value) {
    if (!value) { return ""; }
    return '<div class="spd-ks-cell">'
      + '<span class="spd-ks-label">' + esc(label) + "</span>"
      + '<span class="spd-ks-value">' + esc(value) + "</span>"
      + "</div>";
  }

  function kstartupSection(k) {
    var methods = (k.applyMethods && k.applyMethods.length) ? k.applyMethods.join(", ") : "";
    var cells = [
      ksCell("창업기간", k.foundingPeriod),
      ksCell("대상 연령", k.targetAge),
      ksCell("신청방법", methods),
      ksCell("신청 제외대상", k.exclusion),
      ksCell("우대사항", k.preference),
      ksCell("주관기관", k.supervisor),
      ksCell("수행기관", k.operator)
    ].join("");
    if (!cells) { return ""; }
    return '<section class="spd-section">'
      + '<h2 class="spd-section-title">K-Startup 상세 정보 <span class="spd-ks-badge">K-Startup 공고 전용</span></h2>'
      + '<div class="spd-ks-grid">' + cells + "</div>"
      + "</section>";
  }

  function render(c) {
    var st = statusLabel(c);
    var kstartup = c.kstartup ? kstartupSection(c.kstartup) : "";

    var html = ""
      + '<div class="spd-top">'
      + '<span class="sp-type">' + esc(c.typeLabel) + "</span>"
      + '<span class="sp-src sp-src-' + esc(c.sourceCd) + '">' + esc(SOURCE_LABEL[c.sourceCd] || c.sourceCd) + "</span>"
      + '<span class="spd-status sp-status-' + st.cls + '">' + esc(st.text) + "</span>"
      + "</div>"
      + '<h1 class="spd-title">' + esc(c.title) + "</h1>"
      + '<div class="spd-info">'
      + infoCell("신청기간", periodText(c))
      + infoCell("남은 기간", remainText(c), true)
      + infoCell("지역", c.region)
      + infoCell("문의", c.contact)
      + "</div>";

    if (c.target) {
      html += '<section class="spd-section">'
        + '<h2 class="spd-section-title">지원대상 <span class="spd-note-inline">원문 그대로 표시</span></h2>'
        + '<p class="spd-text">' + esc(c.target) + "</p>"
        + "</section>";
    }
    if (c.description) {
      html += '<section class="spd-section">'
        + '<h2 class="spd-section-title">지원내용</h2>'
        + '<p class="spd-text">' + esc(c.description) + "</p>"
        + "</section>";
    }
    html += kstartup;
    if (c.contact) {
      html += '<section class="spd-section">'
        + '<h2 class="spd-section-title">문의처</h2>'
        + '<p class="spd-text">' + esc(c.contact) + "</p>"
        + "</section>";
    }

    $("spd-root").innerHTML = html;

    var cta = $("spd-cta");
    var note = c.applyEndDe ? "신청은 원문 공고 사이트에서 진행됩니다. 마감 " + c.applyEndDe : "신청은 원문 공고 사이트에서 진행됩니다.";
    $("spd-cta-note").textContent = note;
    var link = $("spd-cta-link");
    var origin = safeUrl(c.detailUrl);
    if (origin) {
      link.href = origin;
      cta.hidden = false;
    } else {
      link.hidden = true;
      cta.hidden = false;
    }
  }

  function notFound() {
    $("spd-root").innerHTML = '<div class="spd-missing">'
      + '<p class="spd-missing-title">지원사업을 찾을 수 없습니다.</p>'
      + '<a class="btn btn-primary" href="/support">목록으로 돌아가기</a></div>';
  }

  var source = param("source");
  var id = param("id");
  if (!source || !id) { notFound(); return; }

  fetch("/api/support-programs/" + encodeURIComponent(source) + "/" + encodeURIComponent(id),
    { headers: { Accept: "application/json" } })
    .then(function (r) { return r.ok ? r.json() : null; })
    .then(function (b) {
      if (b && b.data) { render(b.data); } else { notFound(); }
    })
    .catch(notFound);
})();
