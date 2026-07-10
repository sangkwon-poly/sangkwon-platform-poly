(function () {
  "use strict";

  function esc(s) {
    var d = document.createElement("div");
    d.textContent = (s == null) ? "" : String(s);
    return d.innerHTML.replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  }

  function param(name) {
    var m = new RegExp("[?&]" + name + "=([^&]*)").exec(location.search);
    if (!m) { return ""; }
    // 잘못된 퍼센트 인코딩이면 빈 값으로 돌려 notFound 경로를 태운다
    try {
      return decodeURIComponent(m[1].replace(/\+/g, " "));
    } catch (e) {
      return "";
    }
  }

  function fmtDateTime(iso) {
    if (!iso) { return "-"; }
    var d = new Date(iso);
    if (isNaN(d.getTime())) { return "-"; }
    var y = d.getFullYear();
    var mo = String(d.getMonth() + 1).padStart(2, "0");
    var da = String(d.getDate()).padStart(2, "0");
    var hh = String(d.getHours()).padStart(2, "0");
    var mi = String(d.getMinutes()).padStart(2, "0");
    return y + "." + mo + "." + da + " " + hh + ":" + mi;
  }

  var root = document.getElementById("ntd-root");

  function notFound() {
    root.innerHTML =
      '<div class="ntd-missing">' +
        '<p class="ntd-missing-title">공지를 찾을 수 없습니다.</p>' +
        '<a class="btn btn-primary btn-sm" href="/notice">목록으로 돌아가기</a>' +
      "</div>";
  }

  function render(n) {
    document.title = n.title + " · 여기콕";
    var sep = '<span class="ntd-meta-sep" aria-hidden="true"></span>';
    root.innerHTML =
      (n.isPinned === "Y" ? '<div class="ntd-flags"><span class="nt-flag">고정</span></div>' : "") +
      '<h1 class="ntd-title">' + esc(n.title) + "</h1>" +
      '<div class="ntd-meta">' +
        '<span>' + esc(n.adminName || "여기콕") + "</span>" + sep +
        '<span>' + fmtDateTime(n.createdAt) + "</span>" + sep +
        '<span>조회 ' + Number(n.viewCnt || 0).toLocaleString() + "</span>" +
      "</div>" +
      '<div class="ntd-body">' + esc(n.content) + "</div>" +
      '<div class="ntd-foot"><a class="btn btn-ghost btn-sm" href="/notice">목록으로</a></div>';
  }

  var id = param("id");
  if (!id || !/^\d+$/.test(id)) { notFound(); return; }

  fetch("/api/notices/" + encodeURIComponent(id), { headers: { Accept: "application/json" } })
    .then(function (r) { return r.ok ? r.json() : null; })
    .then(function (b) {
      var n = b && b.data;
      if (!n) { notFound(); return; }
      render(n);
    })
    .catch(notFound);
})();
