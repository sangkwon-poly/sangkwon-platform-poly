/* =====================================================================
 * api.js: member 도메인 프론트 API 래퍼 + 공통 UI 헬퍼
 * 서울공화국 상권분석 플랫폼 · 김민혁(member)
 *
 * 노출: window.MemberAPI (엔드포인트 계약), window.MemberUI (UI 헬퍼)
 * 세션 인증: 브라우저가 JSESSIONID 쿠키를 자동 전송한다(credentials:'include').
 * ===================================================================== */
(function () {
  'use strict';

  /* -------------------------------------------------------------------
   * 1. 공통 에러 타입
   * ----------------------------------------------------------------- */
  class ApiError extends Error {
    constructor(code, message) {
      super(message || code || '요청을 처리하지 못했습니다.');
      this.name = 'ApiError';
      this.code = code || 'UNKNOWN';
    }
  }

  /* -------------------------------------------------------------------
   * 2. 네트워크 요청 (ApiResponse<T> 언랩)
   *    ApiResponse = { success, code, message, data }
   * ----------------------------------------------------------------- */
  async function request(method, path, body) {
    const headers = { 'Accept': 'application/json' };
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }

    let res;
    try {
      res = await fetch(path, {
        method,
        headers,
        credentials: 'include',
        body: body !== undefined ? JSON.stringify(body) : undefined,
      });
    } catch (networkErr) {
      console.error('[MemberAPI] 네트워크 오류:', networkErr);
      throw new ApiError('NETWORK', '서버에 연결하지 못했습니다.');
    }

    // 본문 파싱(빈 응답 허용).
    let payload = null;
    const text = await res.text();
    if (text) {
      try {
        payload = JSON.parse(text);
      } catch (parseErr) {
        console.error('[MemberAPI] 응답 파싱 실패:', parseErr, text);
        throw new ApiError('BAD_RESPONSE', '서버 응답 형식이 올바르지 않습니다.');
      }
    }

    // HTTP 에러 처리(가능하면 ApiResponse 의 code/message 사용).
    if (!res.ok) {
      const code = (payload && payload.code) || 'HTTP_' + res.status;
      const message = (payload && payload.message) || '요청이 실패했습니다.';
      throw new ApiError(code, message);
    }

    // ApiResponse 래핑 언랩.
    if (payload && typeof payload === 'object' && 'success' in payload) {
      if (!payload.success) {
        throw new ApiError(payload.code, payload.message);
      }
      return payload.data;
    }
    return payload;
  }

  /* -------------------------------------------------------------------
   * 3. MemberAPI: 엔드포인트 계약
   * ----------------------------------------------------------------- */
  const MemberAPI = {
    signup: function (data) { return request('POST', '/api/members', data); },
    login: function (data) { return request('POST', '/api/auth/login', data); },
    logout: function () { return request('POST', '/api/auth/logout'); },
    me: function () { return request('GET', '/api/members/me'); },
    updateMe: function (data) { return request('PATCH', '/api/members/me', data); },
    withdraw: function () { return request('DELETE', '/api/members/me'); },
    favorites: function () { return request('GET', '/api/favorites'); },
    addFavorite: function (trdarCd) { return request('POST', '/api/favorites', { trdarCd: trdarCd }); },
    removeFavorite: function (trdarCd) { return request('DELETE', '/api/favorites/' + encodeURIComponent(trdarCd)); },
    searchLogs: function () { return request('GET', '/api/search-logs'); },
    logSearch: function (data) { return request('POST', '/api/search-logs', data); },
  };

  /* -------------------------------------------------------------------
   * 4. MemberUI: 공통 UI 헬퍼
   * ----------------------------------------------------------------- */
  function ensureToastWrap() {
    let wrap = document.querySelector('.toast-wrap');
    if (!wrap) {
      wrap = document.createElement('div');
      wrap.className = 'toast-wrap';
      wrap.setAttribute('aria-live', 'polite');
      wrap.setAttribute('role', 'status');
      document.body.appendChild(wrap);
    }
    return wrap;
  }

  function escapeHtml(str) {
    return String(str == null ? '' : str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  const MemberUI = {
    /**
     * 우상단 토스트 알림.
     * @param {string} msg
     * @param {'ok'|'error'} [type='ok']
     */
    toast: function (msg, type) {
      const kind = type === 'error' ? 'error' : 'ok';
      const wrap = ensureToastWrap();
      const el = document.createElement('div');
      el.className = 'toast toast--' + kind;
      const mark = kind === 'ok' ? '✓' : '!';
      el.innerHTML =
        '<span class="toast__icon" aria-hidden="true">' + mark + '</span>' +
        '<span class="toast__msg">' + escapeHtml(msg) + '</span>';
      wrap.appendChild(el);

      const remove = function () {
        el.classList.add('is-leaving');
        setTimeout(function () {
          if (el.parentNode) el.parentNode.removeChild(el);
        }, 220);
      };
      setTimeout(remove, kind === 'error' ? 4200 : 3000);
      return el;
    },

    /**
     * 비로그인 시 로그인으로 리다이렉트하고 false 반환.
     * 로그인 상태면 회원 정보를 반환(truthy).
     */
    requireAuth: async function () {
      try {
        const me = await MemberAPI.me();
        return me;
      } catch (err) {
        // 현재 페이지를 redirect 파라미터로 남겨 로그인 후 복귀.
        const back = encodeURIComponent(
          location.pathname.split('/').pop() + location.search
        );
        location.replace('login?redirect=' + back);
        return false;
      }
    },

    /**
     * 앱 헤더를 렌더한다. me() 로 로그인 상태를 조회.
     * @param {string} [activeKey] 'map' | 'favorites' | 'mypage'
     * @param {string|HTMLElement} [mount='#app-header']
     */
    renderHeader: async function (activeKey, mount) {
      const host = typeof mount === 'string'
        ? document.querySelector(mount)
        : (mount || document.querySelector('#app-header'));
      if (!host) return;

      const links = [
        { key: 'map', label: '지도', href: '/map' },
        { key: 'favorites', label: '찜', href: 'favorites' },
        { key: 'mypage', label: '마이페이지', href: 'mypage' },
      ];

      let me = null;
      try {
        me = await MemberAPI.me();
      } catch (e) {
        me = null;
      }

      const navHtml = links.map(function (l) {
        const active = l.key === activeKey ? ' is-active' : '';
        return '<a class="app-nav-link' + active + '" href="' +
          escapeHtml(l.href) + '">' + escapeHtml(l.label) + '</a>';
      }).join('');

      let userHtml;
      if (me) {
        const initial = (me.nickname || me.loginId || '?').slice(0, 1);
        userHtml =
          '<div class="app-header__user">' +
            '<span class="app-header__avatar" aria-hidden="true">' +
              escapeHtml(initial) + '</span>' +
            '<span class="app-header__nick">' +
              escapeHtml(me.nickname || me.loginId) + '</span>' +
            '<button type="button" class="btn btn--ghost btn--sm" ' +
              'data-action="logout">로그아웃</button>' +
          '</div>';
      } else {
        userHtml =
          '<div class="app-header__user">' +
            '<a class="btn btn--ghost btn--sm" href="login">로그인</a>' +
          '</div>';
      }

      host.innerHTML =
        '<header class="app-header">' +
          '<div class="app-header__inner">' +
            '<a class="app-header__brand" href="/">' +
              '<span class="app-header__logo" aria-hidden="true">서</span>' +
              '<span>서울공화국</span>' +
            '</a>' +
            '<nav class="app-header__nav" aria-label="주요 메뉴">' +
              navHtml +
            '</nav>' +
            userHtml +
          '</div>' +
        '</header>';

      // 로그아웃 바인딩.
      const logoutBtn = host.querySelector('[data-action="logout"]');
      if (logoutBtn) {
        logoutBtn.addEventListener('click', async function () {
          try {
            await MemberAPI.logout();
            MemberUI.toast('로그아웃되었습니다.', 'ok');
          } catch (e) {
            console.error('[MemberUI] 로그아웃 실패:', e);
          } finally {
            location.href = 'login';
          }
        });
      }
    },

    /**
     * 공통 에러 처리기: 콘솔 + 토스트.
     * @param {any} err
     * @param {string} [fallback]
     */
    handleError: function (err, fallback) {
      const code = err && err.code ? err.code : 'UNKNOWN';
      const message = (err && err.message) || fallback || '오류가 발생했습니다.';
      console.error('[MemberUI] 오류(' + code + '):', err);
      MemberUI.toast(message, 'error');
    },

    // 유틸: 날짜를 사람이 읽기 좋은 한국어로.
    formatDate: function (iso) {
      if (!iso) return '-';
      const d = new Date(iso);
      if (isNaN(d.getTime())) return '-';
      const y = d.getFullYear();
      const mo = String(d.getMonth() + 1).padStart(2, '0');
      const da = String(d.getDate()).padStart(2, '0');
      const hh = String(d.getHours()).padStart(2, '0');
      const mi = String(d.getMinutes()).padStart(2, '0');
      return y + '.' + mo + '.' + da + ' ' + hh + ':' + mi;
    },

    escapeHtml: escapeHtml,
  };

  /* -------------------------------------------------------------------
   * 5. 전역 노출
   * ----------------------------------------------------------------- */
  window.MemberAPI = MemberAPI;
  window.MemberUI = MemberUI;
  window.ApiError = ApiError;
})();
