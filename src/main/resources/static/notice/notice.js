(function () {
  "use strict";

  var state = { page: 0, size: 10 };

  function $(id) { return document.getElementById(id); }

  function esc(s) {
    var d = document.createElement("div");
    d.textContent = (s == null) ? "" : String(s);
    return d.innerHTML.replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  }

  function api(path) {
    return fetch(path, { headers: { Accept: "application/json" } })
      .then(function (r) { return r.ok ? r.json() : null; })
      .catch(function () { return null; });
  }

  function fmtDate(iso) {
    if (!iso) { return "-"; }
    var d = new Date(iso);
    if (isNaN(d.getTime())) { return "-"; }
    var y = d.getFullYear();
    var mo = String(d.getMonth() + 1).padStart(2, "0");
    var da = String(d.getDate()).padStart(2, "0");
    return y + "." + mo + "." + da;
  }

  // 등록 7일 이내면 NEW 점 표시
  function isNew(iso) {
    if (!iso) { return false; }
    var d = new Date(iso);
    if (isNaN(d.getTime())) { return false; }
    return (Date.now() - d.getTime()) < 7 * 24 * 60 * 60 * 1000;
  }

  function rowHtml(n) {
    var pinned = n.isPinned === "Y";
    return '<li class="nt-row' + (pinned ? " is-pinned" : "") + '">'
      + '<a class="nt-row-link" href="/notice/detail?id=' + encodeURIComponent(n.noticeId) + '">'
      + (pinned ? '<span class="nt-flag">고정</span>' : "")
      + '<span class="nt-row-title">' + esc(n.title)
      + (isNew(n.createdAt) ? '<i class="nt-new" aria-label="새 공지"></i>' : "")
      + "</span>"
      + '<span class="nt-row-meta">'
      + '<span class="nt-date">' + fmtDate(n.createdAt) + "</span>"
      + '<span class="nt-views">조회 ' + Number(n.viewCnt || 0).toLocaleString() + "</span>"
      + "</span>"
      + "</a>"
      + "</li>";
  }

  // 현재 페이지를 가운데 두고 최대 5개 번호를 노출
  function pagerHtml(d) {
    if (d.totalPages <= 1) { return ""; }
    var cur = d.page;
    var last = d.totalPages - 1;
    var start = Math.max(0, Math.min(cur - 2, last - 4));
    var end = Math.min(last, start + 4);
    var html = '<button type="button" class="nt-page-btn" data-page="' + (cur - 1) + '"'
      + (cur === 0 ? " disabled" : "") + ">이전</button>";
    for (var p = start; p <= end; p++) {
      html += '<button type="button" class="nt-page-btn' + (p === cur ? " is-active" : "")
        + '" data-page="' + p + '">' + (p + 1) + "</button>";
    }
    html += '<button type="button" class="nt-page-btn" data-page="' + (cur + 1) + '"'
      + (cur >= last ? " disabled" : "") + ">다음</button>";
    return html;
  }

  function load(page) {
    api("/api/notices?page=" + page + "&size=" + state.size).then(function (res) {
      var d = res && res.data;
      if (!d) { return; }
      // 성공했을 때만 페이지를 확정해, 실패한 페이지를 다시 누를 수 있게 둔다
      var moved = page !== state.page;
      state.page = page;
      var isEmpty = d.totalElements === 0;
      $("nt-list").innerHTML = d.content.map(rowHtml).join("");
      $("nt-empty").hidden = !isEmpty;
      $("nt-list").hidden = isEmpty;
      $("nt-pager").innerHTML = pagerHtml(d);
      if (moved) { window.scrollTo({ top: 0, behavior: "smooth" }); }
    });
  }

  $("nt-pager").addEventListener("click", function (e) {
    var btn = e.target.closest(".nt-page-btn");
    if (!btn || btn.disabled) { return; }
    var p = Number(btn.getAttribute("data-page"));
    if (isNaN(p) || p === state.page) { return; }
    load(p);
  });

  load(0);
})();
