/* =====================================================================
 * csrf.js: CSRF 토큰 자동 첨부
 * 서버가 CookieCsrfTokenRepository로 내려준 XSRF-TOKEN 쿠키를 읽어,
 * 같은 출처의 상태변경 요청(POST/PUT/DELETE/PATCH)에 X-XSRF-TOKEN 헤더로 되돌려준다.
 * 각 화면의 fetch 호출부를 고치지 않아도 되도록 window.fetch를 한 번 감싼다.
 * 관리자 API(/api/admin/**)는 서버에서 CSRF 예외이고, 외부 도메인(토스 등) 호출은
 * 같은 출처가 아니라 헤더를 붙이지 않는다.
 * ===================================================================== */
(function () {
  'use strict';
  if (window.__csrfPatched) { return; }
  window.__csrfPatched = true;

  var UNSAFE = { POST: 1, PUT: 1, DELETE: 1, PATCH: 1 };
  var HEADER = 'X-XSRF-TOKEN';

  function readToken() {
    var m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    return m ? decodeURIComponent(m[1]) : null;
  }

  // 상대경로이거나 현재 출처와 같은 절대 URL만 같은 출처로 본다.
  function isSameOrigin(url) {
    try {
      return new URL(url, window.location.href).origin === window.location.origin;
    } catch (e) {
      return true; // 파싱 실패는 상대경로로 간주
    }
  }

  var origFetch = window.fetch;
  window.fetch = function (input, init) {
    init = init || {};
    var isReq = (typeof input !== 'string') && input && typeof input === 'object';
    var method = (init.method || (isReq ? input.method : null) || 'GET').toUpperCase();
    var url = (typeof input === 'string') ? input : (isReq ? input.url : '');

    if (UNSAFE[method] && isSameOrigin(url)) {
      var token = readToken();
      if (token) {
        var headers = new Headers(init.headers || (isReq ? input.headers : null) || {});
        if (!headers.has(HEADER)) { headers.set(HEADER, token); }
        init = Object.assign({}, init, { headers: headers });
      }
    }
    return origFetch.call(this, input, init);
  };
})();
