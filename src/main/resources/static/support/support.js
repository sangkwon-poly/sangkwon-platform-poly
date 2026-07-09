(function () {
  "use strict";

  var SOURCE_LABEL = { BIZINFO: "기업마당", KSTARTUP: "K-Startup" };
  var FOUNDING_LABEL = {
    PRELIMINARY: "예비창업자", Y1: "1년 미만", Y3: "3년 미만",
    Y5: "5년 미만", Y7: "7년 미만", Y10: "10년 미만"
  };
  var AGE_LABEL = { UNDER20: "만 20세 미만", A20_39: "만 20~39세", OVER40: "만 40세 이상" };

  var state = {
    type: "", region: "", source: "", target: "", q: "",
    founding: "", age: "", recruiting: true, includeUnknown: false, page: 0
  };

  function esc(s) {
    var d = document.createElement("div");
    d.textContent = (s == null) ? "" : String(s);
    return d.innerHTML.replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  }
  function $(id) { return document.getElementById(id); }

  function api(path) {
    return fetch(path, { headers: { Accept: "application/json" } })
      .then(function (r) { return r.ok ? r.json() : null; })
      .catch(function () { return null; });
  }

  function detailActive() { return !!(state.founding || state.age); }

  function ddayLabel(d) { return d === 0 ? "D-DAY" : "D-" + d; }

  function statusOf(c) {
    switch (c.status) {
      case "RECRUITING": return { cls: "ok", text: c.dday != null ? "모집중 · " + ddayLabel(c.dday) : "모집중" };
      case "CLOSING": return { cls: "danger", text: "마감 임박" + (c.dday != null ? " · " + ddayLabel(c.dday) : "") };
      case "UPCOMING": return { cls: "info", text: "예정" };
      case "CLOSED": return { cls: "muted", text: "마감" };
      case "ALWAYS": return { cls: "always", text: "상시" };
      default: return { cls: "muted", text: c.status };
    }
  }

  function periodText(c) {
    if (c.status === "ALWAYS") { return c.applyPeriodRaw || "상시"; }
    if (c.applyBgngDe && c.applyEndDe) { return c.applyBgngDe + " ~ " + c.applyEndDe.slice(5); }
    if (c.applyEndDe) { return "~ " + c.applyEndDe; }
    return c.applyPeriodRaw || "";
  }

  function cardHtml(c) {
    var st = statusOf(c);
    var meta = [c.org, periodText(c)].filter(Boolean).map(esc).join(" · ");
    var href = "/support/detail?source=" + encodeURIComponent(c.sourceCd) + "&id=" + encodeURIComponent(c.programId);
    return '<li class="sp-card">'
      + '<a class="sp-card-info" href="' + href + '">'
      + '<div class="sp-card-head">'
      + '<span class="sp-type">' + esc(c.typeLabel) + "</span>"
      + '<h2 class="sp-title">' + esc(c.title) + "</h2>"
      + "</div>"
      + (meta ? '<p class="sp-meta">' + meta + "</p>" : "")
      + "</a>"
      + '<div class="sp-card-side">'
      + '<span class="sp-status sp-status-' + st.cls + '">' + esc(st.text) + "</span>"
      + '<span class="sp-src sp-src-' + esc(c.sourceCd) + '">' + esc(SOURCE_LABEL[c.sourceCd] || c.sourceCd) + "</span>"
      + (c.detailUrl ? '<a class="sp-origin" href="' + esc(c.detailUrl) + '" target="_blank" rel="noopener">원문 공고 보기 ↗</a>' : "")
      + "</div>"
      + "</li>";
  }

  function renderTabs(typeCounts) {
    $("sp-type-tabs").innerHTML = typeCounts.map(function (t) {
      var code = t.tab === "ALL" ? "" : t.tab;
      var active = (state.type === code) ? " is-active" : "";
      return '<button type="button" class="sp-tab' + active + '" data-type="' + esc(code) + '">'
        + esc(t.label) + ' <span class="sp-tab-n">' + t.count + "</span></button>";
    }).join("");
  }

  function renderBanner(excluded) {
    var banner = $("sp-banner");
    if (detailActive() && excluded > 0) {
      $("sp-banner-text").textContent =
        "창업기간·연령 정보는 K-Startup 공고에만 있어 기업마당 공고 " + excluded + "건이 제외되었습니다.";
      $("sp-include-unknown").checked = state.includeUnknown;
      banner.hidden = false;
    } else {
      banner.hidden = true;
    }
  }

  function renderActiveChips() {
    var chips = [];
    if (state.founding) { chips.push({ key: "founding", label: "창업기간 · " + FOUNDING_LABEL[state.founding] }); }
    if (state.age) { chips.push({ key: "age", label: "연령 · " + AGE_LABEL[state.age] }); }
    $("sp-active-chips").innerHTML = chips.map(function (c) {
      return '<li class="sp-active-chip">' + esc(c.label)
        + '<button type="button" class="sp-chip-x" data-clear="' + c.key + '" aria-label="필터 제거">×</button></li>';
    }).join("");
  }

  function syncChips() {
    var list = document.querySelectorAll(".sp-chip");
    for (var i = 0; i < list.length; i++) {
      var ch = list[i];
      var k = ch.getAttribute("data-key");
      ch.classList.toggle("is-active", (state[k] || "") === ch.getAttribute("data-value"));
    }
    var rec = document.querySelectorAll(".sp-recruit-btn");
    for (var j = 0; j < rec.length; j++) {
      rec[j].classList.toggle("is-active", String(state.recruiting) === rec[j].getAttribute("data-recruiting"));
    }
  }

  function updateSub() {
    $("sp-sub").textContent = detailActive()
      ? "상세필터 적용 중. K-Startup 공고만 표시."
      : "기업마당, K-Startup 공고 통합. 기본 정렬은 마감임박순.";
  }

  function buildQuery() {
    var p = [];
    function add(k, v) { if (v) { p.push(k + "=" + encodeURIComponent(v)); } }
    add("type", state.type);
    add("region", state.region);
    add("source", state.source);
    add("target", state.target);
    add("q", state.q);
    add("founding", state.founding);
    add("age", state.age);
    p.push("recruiting=" + state.recruiting);
    if (state.includeUnknown) { p.push("includeUnknown=true"); }
    p.push("page=" + state.page);
    p.push("size=20");
    return p.join("&");
  }

  function load(reset) {
    if (reset) { state.page = 0; }
    syncChips();
    api("/api/support-programs?" + buildQuery()).then(function (res) {
      var d = res && res.data;
      if (!d) { return; }
      var list = $("sp-list");
      if (reset) { list.innerHTML = ""; }
      list.insertAdjacentHTML("beforeend", d.content.map(cardHtml).join(""));

      $("sp-count").textContent = Number(d.totalElements).toLocaleString();
      if (reset) { renderTabs(d.typeCounts); }
      renderBanner(d.excludedByDetailFilter);
      renderActiveChips();
      updateSub();

      var isEmpty = d.totalElements === 0;
      $("sp-empty").hidden = !isEmpty;
      list.hidden = isEmpty;
      $("sp-more-wrap").hidden = (d.page + 1) >= d.totalPages;
    });
  }

  function pick(key, value) {
    state[key] = (state[key] === value) ? "" : value;
    load(true);
  }

  function resetAll() {
    state.type = ""; state.region = ""; state.source = ""; state.target = "";
    state.q = ""; state.founding = ""; state.age = ""; state.includeUnknown = false;
    state.recruiting = true;
    $("sp-q").value = "";
    var det = $("sp-detail"); if (det) { det.open = false; }
    load(true);
  }

  // 이벤트 위임: 필터 칩(지역/출처/대상/창업기간/연령)
  document.addEventListener("click", function (e) {
    var chip = e.target.closest ? e.target.closest(".sp-chip") : null;
    if (chip) { pick(chip.getAttribute("data-key"), chip.getAttribute("data-value")); return; }

    var tab = e.target.closest ? e.target.closest(".sp-tab") : null;
    if (tab) { state.type = tab.getAttribute("data-type"); load(true); return; }

    var rec = e.target.closest ? e.target.closest(".sp-recruit-btn") : null;
    if (rec) { state.recruiting = rec.getAttribute("data-recruiting") === "true"; load(true); return; }

    var x = e.target.closest ? e.target.closest(".sp-chip-x") : null;
    if (x) { state[x.getAttribute("data-clear")] = ""; load(true); return; }
  });

  $("sp-search").addEventListener("submit", function (e) {
    e.preventDefault();
    state.q = $("sp-q").value.trim();
    load(true);
  });

  $("sp-include-unknown").addEventListener("change", function (e) {
    state.includeUnknown = e.target.checked;
    load(true);
  });

  $("sp-clear-detail").addEventListener("click", function () {
    state.founding = ""; state.age = ""; state.includeUnknown = false;
    var det = $("sp-detail"); if (det) { det.open = false; }
    load(true);
  });

  $("sp-reset").addEventListener("click", resetAll);
  $("sp-more").addEventListener("click", function () { state.page += 1; load(false); });

  load(true);
})();
