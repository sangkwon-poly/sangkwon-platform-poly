(function () {
  "use strict";

  function $(id) { return document.getElementById(id); }

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

  /* ---------------------------------------------------------------
   * 로그인 상태에 따라 작성 폼 / 로그인 유도를 전환
   * ------------------------------------------------------------- */
  function init() {
    api("GET", "/api/members/me").then(function (r) {
      var me = r.ok && r.body && r.body.data;
      $("iq-login").hidden = !!me;
      $("iq-form").hidden = !me;
    });
  }

  function showMsg(text, ok) {
    var msg = $("iq-msg");
    msg.textContent = text;
    msg.classList.toggle("is-ok", !!ok);
    msg.classList.toggle("is-error", !ok);
  }

  /* ---------------------------------------------------------------
   * 문의 등록. 내역 조회는 마이페이지의 '내 문의 내역'에서.
   * ------------------------------------------------------------- */
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
      showMsg("문의가 등록되었습니다. 답변이 달리면 내 문의 내역에서 확인할 수 있어요.", true);
    });
  });

  init();
})();
