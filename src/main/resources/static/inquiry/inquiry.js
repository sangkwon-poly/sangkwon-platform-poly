(function () {
  "use strict";

  var STATUS_LABEL = { OPEN: "대기중", ANSWERED: "답변완료", CLOSED: "닫힘" };
  var STATUS_BADGE = { OPEN: "badge-warn", ANSWERED: "badge-ok", CLOSED: "badge-muted" };

  var state = { page: 0, size: 5 };

  function $(id) { return document.getElementById(id); }

  function esc(s) {
    var d = document.createElement("div");
    d.textContent = (s == null) ? "" : String(s);
    return d.innerHTML.replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  }

  // 세션 쿠키가 필요한 회원 API 전용 헬퍼. ApiResponse 봉투째 돌려준다.
  function api(method, path, body) {
    var opt = {
      method: method,
      credentials: "include",
      headers: { Accept: "application/json" }
    };
    if (body !== undefined) {
      opt.headers["Content-Type"] = "application/json";
      opt.body = JSON.stringify(body);
    }
    return fetch(path, opt)
      .then(function (r) {
        return r.text().then(function (t) {
          var b = null;
          try { b = t ? JSON.parse(t) : null; } catch (e) { b = null; }
          return { ok: r.ok, status: r.status, body: b };
        });
      })
      .catch(function () { return { ok: false, status: 0, body: null }; });
  }

  function fmtDate(iso) {
    if (!iso) { return "-"; }
    var d = new Date(iso);
    if (isNaN(d.getTime())) { return "-"; }
    return d.getFullYear() + "." + String(d.getMonth() + 1).padStart(2, "0")
      + "." + String(d.getDate()).padStart(2, "0");
  }

  function fmtDateTime(iso) {
    if (!iso) { return "-"; }
    var d = new Date(iso);
    if (isNaN(d.getTime())) { return "-"; }
    return fmtDate(iso) + " " + String(d.getHours()).padStart(2, "0")
      + ":" + String(d.getMinutes()).padStart(2, "0");
  }

  /* ---------------------------------------------------------------
   * 로그인 상태에 따라 폼/로그인 유도/내역 표시를 전환
   * ------------------------------------------------------------- */
  function init() {
    api("GET", "/api/members/me").then(function (r) {
      var me = r.ok && r.body && r.body.data;
      $("iq-login").hidden = !!me;
      $("iq-form").hidden = !me;
      $("iq-history").hidden = !me;
      if (me) { loadHistory(0); }
    });
  }

  /* ---------------------------------------------------------------
   * 문의 등록
   * ------------------------------------------------------------- */
  function showMsg(text, ok) {
    var msg = $("iq-msg");
    msg.textContent = text;
    msg.classList.toggle("is-ok", !!ok);
    msg.classList.toggle("is-error", !ok);
  }

  $("iq-form").addEventListener("submit", function (e) {
    e.preventDefault();
    var title = $("iq-title-input").value.trim();
    var content = $("iq-content-input").value.trim();
    if (!title) { showMsg("제목을 입력해 주세요.", false); $("iq-title-input").focus(); return; }
    if (!content) { showMsg("문의 내용을 입력해 주세요.", false); $("iq-content-input").focus(); return; }

    var btn = $("iq-submit");
    btn.disabled = true;
    btn.textContent = "등록 중…";
    api("POST", "/api/inquiries", { title: title, content: content }).then(function (r) {
      btn.disabled = false;
      btn.textContent = "문의 등록";
      if (!r.ok) {
        if (r.status === 401) { init(); return; } // 세션 만료: 로그인 유도로 전환
        showMsg((r.body && r.body.message) || "등록에 실패했습니다. 잠시 후 다시 시도해 주세요.", false);
        return;
      }
      $("iq-title-input").value = "";
      $("iq-content-input").value = "";
      showMsg("문의가 등록되었습니다. 답변이 달리면 아래 내역에서 확인할 수 있어요.", true);
      loadHistory(0);
    });
  });

  /* ---------------------------------------------------------------
   * 내 문의 내역 (아코디언 + 페이징)
   * ------------------------------------------------------------- */
  function itemHtml(q) {
    var badge = '<span class="badge ' + (STATUS_BADGE[q.status] || "badge-muted") + '">'
      + esc(STATUS_LABEL[q.status] || q.status) + "</span>";
    return '<li class="iq-item" data-id="' + q.inquiryId + '">'
      + '<button type="button" class="iq-item-head" aria-expanded="false">'
      + badge
      + '<span class="iq-item-title">' + esc(q.title) + "</span>"
      + '<span class="iq-item-date">' + fmtDate(q.createdAt) + "</span>"
      + '<span class="iq-item-arrow" aria-hidden="true"></span>'
      + "</button>"
      + '<div class="iq-item-body" hidden></div>'
      + "</li>";
  }

  function pagerHtml(d) {
    if (d.totalPages <= 1) { return ""; }
    return '<button type="button" class="iq-page-btn" data-page="' + (d.page - 1) + '"'
      + (d.page === 0 ? " disabled" : "") + ">이전</button>"
      + '<span class="iq-page-now">' + (d.page + 1) + " / " + d.totalPages + "</span>"
      + '<button type="button" class="iq-page-btn" data-page="' + (d.page + 1) + '"'
      + ((d.page + 1) >= d.totalPages ? " disabled" : "") + ">다음</button>";
  }

  function loadHistory(page) {
    state.page = page;
    api("GET", "/api/inquiries/my?page=" + page + "&size=" + state.size).then(function (r) {
      if (r.status === 401) { init(); return; } // 세션 만료: 로그인 유도로 전환
      var d = r.ok && r.body && r.body.data;
      if (!d) { return; }
      var isEmpty = d.totalElements === 0;
      $("iq-count").textContent = isEmpty ? "" : Number(d.totalElements).toLocaleString() + "건";
      $("iq-list").innerHTML = d.content.map(itemHtml).join("");
      $("iq-empty").hidden = !isEmpty;
      $("iq-pager").innerHTML = pagerHtml(d);
    });
  }

  function detailHtml(q) {
    var html = '<div class="iq-q">' + esc(q.content) + "</div>";
    if (q.answer) {
      html += '<div class="iq-answer">'
        + '<div class="iq-answer-head">여기콕 답변'
        + '<span class="iq-answer-meta">' + esc(q.adminName || "운영팀")
        + " · " + fmtDateTime(q.answeredAt) + "</span></div>"
        + '<div class="iq-answer-body">' + esc(q.answer) + "</div>"
        + "</div>";
    } else {
      html += '<p class="iq-wait">아직 답변이 등록되지 않았어요. 확인 후 순차적으로 답변드릴게요.</p>';
    }
    return html;
  }

  $("iq-list").addEventListener("click", function (e) {
    var head = e.target.closest(".iq-item-head");
    if (!head) { return; }
    var item = head.closest(".iq-item");
    var body = item.querySelector(".iq-item-body");
    var open = !body.hidden;
    if (open) {
      body.hidden = true;
      head.setAttribute("aria-expanded", "false");
      item.classList.remove("is-open");
      return;
    }
    head.setAttribute("aria-expanded", "true");
    item.classList.add("is-open");
    body.hidden = false;
    if (!body.dataset.loaded) {
      body.innerHTML = '<p class="iq-loading">불러오는 중…</p>';
      api("GET", "/api/inquiries/" + item.getAttribute("data-id")).then(function (r) {
        var q = r.ok && r.body && r.body.data;
        if (!q) {
          body.innerHTML = '<p class="iq-loading">내용을 불러오지 못했습니다.</p>';
          return;
        }
        body.dataset.loaded = "1";
        body.innerHTML = detailHtml(q);
      });
    }
  });

  $("iq-pager").addEventListener("click", function (e) {
    var btn = e.target.closest(".iq-page-btn");
    if (!btn || btn.disabled) { return; }
    var p = Number(btn.getAttribute("data-page"));
    if (isNaN(p) || p < 0) { return; }
    loadHistory(p);
  });

  init();
})();
