/* =====================================================================
 * api.js — member 도메인 프론트 API 래퍼 + 공통 UI 헬퍼
 * 서울공화국 상권분석 플랫폼 · 김민혁(member)
 *
 * 노출: window.MemberAPI (엔드포인트 계약), window.MemberUI (UI 헬퍼)
 *
 * 백엔드 member API가 아직 미구현이므로 MOCK 모드로도 완전히 동작한다.
 * 실제 백엔드가 준비되면 아래 USE_MOCK 만 false 로 바꾸면 된다.
 * ===================================================================== */
(function () {
  'use strict';

  /* -------------------------------------------------------------------
   * 0. 토글 / 설정
   * ----------------------------------------------------------------- */

  // 백엔드 member API 준비되면 false 로 바꾼다.
  let USE_MOCK = true;

  // file:// 로 미리보기할 땐 서버가 없으므로 무조건 MOCK 강제.
  if (location.protocol === 'file:') {
    USE_MOCK = true;
  }

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
   * 2. 실제 네트워크 요청 (ApiResponse<T> 언랩)
   *    ApiResponse = { success, code, message, data }
   * ----------------------------------------------------------------- */
  async function request(method, path, body) {
    const headers = { 'Accept': 'application/json' };
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }

    // 세션 인증 — 브라우저가 JSESSIONID 쿠키를 자동 전송한다(아래 credentials:'include').
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

    // 래핑되지 않은 응답은 그대로 반환(방어적).
    return payload;
  }

  /* -------------------------------------------------------------------
   * 3. MOCK 인메모리 저장소
   *    회원/세션/찜/검색로그를 실제처럼 다룬다.
   * ----------------------------------------------------------------- */
  const MOCK_DELAY = 250; // 실제 네트워크 느낌의 지연(ms).
  const SESSION_KEY = 'MOCK_SESSION_MEMBER_ID';

  const mockDb = {
    seq: { member: 1, favorite: 1, search: 1 },
    membersById: new Map(),
    membersByLoginId: new Map(),
    favoritesByMember: new Map(), // memberId -> [favorite]
    searchLogsByMember: new Map(), // memberId -> [searchLog]
  };

  function nowIso() {
    return new Date().toISOString();
  }

  function seedMock() {
    // 시드 회원(로그인된 예시 유저).
    const seedMember = {
      memberId: mockDb.seq.member++,
      loginId: 'minhyuk',
      // mock 이라 평문 비교(실서버는 해시). 데모 비밀번호.
      password: 'test1234',
      email: 'minhyuk@seoul.kr',
      nickname: '김민혁',
      role: 'USER',
      status: 'ACTIVE',
      createdAt: '2026-07-01T09:12:00.000Z',
      lastLoginAt: '2026-07-06T08:40:00.000Z',
    };
    mockDb.membersById.set(seedMember.memberId, seedMember);
    mockDb.membersByLoginId.set(seedMember.loginId, seedMember);

    // 시드 찜(상권명 예시).
    const favs = [
      { trdarCd: '3110001', trdarNm: '역삼동', signguNm: '강남구' },
      { trdarCd: '3110002', trdarNm: '홍대입구역', signguNm: '마포구' },
      { trdarCd: '3110003', trdarNm: '성수 카페거리', signguNm: '성동구' },
      { trdarCd: '3110004', trdarNm: '연남동', signguNm: '마포구' },
    ].map(function (f, i) {
      return {
        favoriteId: mockDb.seq.favorite++,
        trdarCd: f.trdarCd,
        trdarNm: f.trdarNm,
        signguNm: f.signguNm,
        createdAt: new Date(Date.now() - (i + 1) * 86400000).toISOString(),
      };
    });
    mockDb.favoritesByMember.set(seedMember.memberId, favs);

    // 시드 검색 로그.
    const logs = [
      { keyword: '강남 카페 창업', trdarCd: '3110001' },
      { keyword: '홍대 유동인구', trdarCd: '3110002' },
      { keyword: '성수동 매출', trdarCd: '3110003' },
      { keyword: '음식점 폐업률', trdarCd: null },
    ].map(function (l, i) {
      return {
        searchId: mockDb.seq.search++,
        keyword: l.keyword,
        trdarCd: l.trdarCd,
        searchedAt: new Date(Date.now() - (i + 1) * 3600000).toISOString(),
      };
    });
    mockDb.searchLogsByMember.set(seedMember.memberId, logs);

    // 미리보기 편의를 위해 시드 유저를 기본 로그인 상태로 둔다.
    if (!getMockSessionId()) {
      setMockSession(seedMember.memberId);
    }
  }

  function getMockSessionId() {
    try {
      const raw = window.sessionStorage.getItem(SESSION_KEY);
      return raw ? Number(raw) : null;
    } catch (e) {
      return null;
    }
  }

  function setMockSession(memberId) {
    try {
      window.sessionStorage.setItem(SESSION_KEY, String(memberId));
    } catch (e) { /* 무시 */ }
  }

  function clearMockSession() {
    try {
      window.sessionStorage.removeItem(SESSION_KEY);
    } catch (e) { /* 무시 */ }
  }

  function delay(value) {
    return new Promise(function (resolve) {
      setTimeout(function () { resolve(value); }, MOCK_DELAY);
    });
  }

  function reject(code, message) {
    return delay().then(function () {
      throw new ApiError(code, message);
    });
  }

  function toMemberResponse(m) {
    return {
      memberId: m.memberId,
      loginId: m.loginId,
      email: m.email,
      nickname: m.nickname,
      role: m.role,
      status: m.status,
      createdAt: m.createdAt,
      lastLoginAt: m.lastLoginAt,
    };
  }

  function currentMockMember() {
    const id = getMockSessionId();
    if (!id) return null;
    return mockDb.membersById.get(id) || null;
  }

  // 시드 즉시 실행.
  seedMock();

  /* -------------------------------------------------------------------
   * 4. MOCK API 구현 (엔드포인트 계약과 동일한 시그니처)
   * ----------------------------------------------------------------- */
  const mockApi = {
    signup: function (payload) {
      const p = payload || {};
      if (!p.loginId || !p.email || !p.nickname || !p.password) {
        return reject('M000', '필수 항목을 모두 입력해 주세요.');
      }
      if (mockDb.membersByLoginId.has(p.loginId)) {
        return reject('M002', '이미 사용 중인 로그인 아이디입니다.');
      }
      for (const m of mockDb.membersById.values()) {
        if (m.email === p.email) {
          return reject('M003', '이미 사용 중인 이메일입니다.');
        }
      }
      const member = {
        memberId: mockDb.seq.member++,
        loginId: p.loginId,
        password: p.password,
        email: p.email,
        nickname: p.nickname,
        role: 'USER',
        status: 'ACTIVE',
        createdAt: nowIso(),
        lastLoginAt: null,
      };
      mockDb.membersById.set(member.memberId, member);
      mockDb.membersByLoginId.set(member.loginId, member);
      mockDb.favoritesByMember.set(member.memberId, []);
      mockDb.searchLogsByMember.set(member.memberId, []);
      return delay(toMemberResponse(member));
    },

    login: function (payload) {
      const p = payload || {};
      const member = mockDb.membersByLoginId.get(p.loginId);
      if (!member || member.password !== p.password) {
        return reject('M004', '아이디 또는 비밀번호가 올바르지 않습니다.');
      }
      if (member.status === 'WITHDRAWN') {
        return reject('M007', '탈퇴한 회원입니다.');
      }
      if (member.status === 'BANNED') {
        return reject('M008', '이용이 제한된 계정입니다.');
      }
      member.lastLoginAt = nowIso();
      setMockSession(member.memberId);
      return delay(toMemberResponse(member));
    },

    logout: function () {
      clearMockSession();
      return delay({ ok: true });
    },

    me: function () {
      const m = currentMockMember();
      if (!m) return reject('M005', '로그인이 필요합니다.');
      return delay(toMemberResponse(m));
    },

    updateMe: function (payload) {
      const m = currentMockMember();
      if (!m) return reject('M005', '로그인이 필요합니다.');
      const p = payload || {};
      if (p.email && p.email !== m.email) {
        for (const other of mockDb.membersById.values()) {
          if (other.memberId !== m.memberId && other.email === p.email) {
            return reject('M003', '이미 사용 중인 이메일입니다.');
          }
        }
        m.email = p.email;
      }
      if (p.nickname) {
        m.nickname = p.nickname;
      }
      return delay(toMemberResponse(m));
    },

    withdraw: function () {
      const m = currentMockMember();
      if (!m) return reject('M005', '로그인이 필요합니다.');
      m.status = 'WITHDRAWN';
      m.withdrawnAt = nowIso();
      clearMockSession();
      return delay({ ok: true });
    },

    favorites: function () {
      const m = currentMockMember();
      if (!m) return reject('M005', '로그인이 필요합니다.');
      const list = mockDb.favoritesByMember.get(m.memberId) || [];
      // 최신순.
      const sorted = list.slice().sort(function (a, b) {
        return new Date(b.createdAt) - new Date(a.createdAt);
      });
      return delay(sorted);
    },

    addFavorite: function (trdarCd) {
      const m = currentMockMember();
      if (!m) return reject('M005', '로그인이 필요합니다.');
      if (!trdarCd) return reject('M000', '상권 코드가 필요합니다.');
      const list = mockDb.favoritesByMember.get(m.memberId) || [];
      if (list.some(function (f) { return f.trdarCd === String(trdarCd); })) {
        return reject('M006', '이미 찜한 상권입니다.');
      }
      const fav = {
        favoriteId: mockDb.seq.favorite++,
        trdarCd: String(trdarCd),
        trdarNm: '상권 ' + trdarCd,
        signguNm: '서울시',
        createdAt: nowIso(),
      };
      list.push(fav);
      mockDb.favoritesByMember.set(m.memberId, list);
      return delay(fav);
    },

    removeFavorite: function (trdarCd) {
      const m = currentMockMember();
      if (!m) return reject('M005', '로그인이 필요합니다.');
      const list = mockDb.favoritesByMember.get(m.memberId) || [];
      const next = list.filter(function (f) {
        return f.trdarCd !== String(trdarCd);
      });
      mockDb.favoritesByMember.set(m.memberId, next);
      return delay({ ok: true });
    },

    searchLogs: function () {
      const m = currentMockMember();
      if (!m) return reject('M005', '로그인이 필요합니다.');
      const list = mockDb.searchLogsByMember.get(m.memberId) || [];
      const sorted = list.slice().sort(function (a, b) {
        return new Date(b.searchedAt) - new Date(a.searchedAt);
      });
      return delay(sorted);
    },

    logSearch: function (payload) {
      const p = payload || {};
      if (!p.keyword) return reject('M000', '검색어가 필요합니다.');
      const m = currentMockMember();
      // SEARCH_LOG.MEMBER_ID 는 nullable(비로그인 검색 허용).
      const memberId = m ? m.memberId : null;
      const log = {
        searchId: mockDb.seq.search++,
        keyword: p.keyword,
        trdarCd: p.trdarCd || null,
        searchedAt: nowIso(),
      };
      if (memberId != null) {
        const list = mockDb.searchLogsByMember.get(memberId) || [];
        list.push(log);
        mockDb.searchLogsByMember.set(memberId, list);
      }
      return delay(log);
    },
  };

  /* -------------------------------------------------------------------
   * 5. MemberAPI 공개 (mock ↔ 실제 스위칭)
   * ----------------------------------------------------------------- */
  const MemberAPI = {
    signup: function (data) {
      if (USE_MOCK) return mockApi.signup(data);
      return request('POST', '/api/members', data);
    },

    login: function (data) {
      if (USE_MOCK) return mockApi.login(data);
      return request('POST', '/api/auth/login', data);
    },

    logout: function () {
      if (USE_MOCK) return mockApi.logout();
      return request('POST', '/api/auth/logout');
    },

    me: function () {
      if (USE_MOCK) return mockApi.me();
      return request('GET', '/api/members/me');
    },

    updateMe: function (data) {
      if (USE_MOCK) return mockApi.updateMe(data);
      return request('PATCH', '/api/members/me', data);
    },

    withdraw: function () {
      if (USE_MOCK) return mockApi.withdraw();
      return request('DELETE', '/api/members/me');
    },

    favorites: function () {
      if (USE_MOCK) return mockApi.favorites();
      return request('GET', '/api/favorites');
    },

    addFavorite: function (trdarCd) {
      if (USE_MOCK) return mockApi.addFavorite(trdarCd);
      return request('POST', '/api/favorites', { trdarCd: trdarCd });
    },

    removeFavorite: function (trdarCd) {
      if (USE_MOCK) return mockApi.removeFavorite(trdarCd);
      return request('DELETE', '/api/favorites/' + encodeURIComponent(trdarCd));
    },

    searchLogs: function () {
      if (USE_MOCK) return mockApi.searchLogs();
      return request('GET', '/api/search-logs');
    },

    logSearch: function (data) {
      if (USE_MOCK) return mockApi.logSearch(data);
      return request('POST', '/api/search-logs', data);
    },

    // 진단용(페이지에서 mock 여부 확인 가능).
    _isMock: function () { return USE_MOCK; },
  };

  /* -------------------------------------------------------------------
   * 6. MemberUI — 공통 UI 헬퍼
   * ----------------------------------------------------------------- */

  // 토스트 컨테이너 확보.
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
     * 비로그인 시 login.html 로 리다이렉트하고 false 반환.
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
        location.replace('login.html?redirect=' + back);
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
        { key: 'map', label: '지도', href: '../map/index.html' },
        { key: 'favorites', label: '찜', href: 'favorites.html' },
        { key: 'mypage', label: '마이페이지', href: 'mypage.html' },
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
            '<a class="btn btn--ghost btn--sm" href="login.html">로그인</a>' +
          '</div>';
      }

      host.innerHTML =
        '<header class="app-header">' +
          '<div class="app-header__inner">' +
            '<a class="app-header__brand" href="../map/index.html">' +
              '<span class="app-header__logo" aria-hidden="true">서</span>' +
              '<span>서울상권</span>' +
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
            location.href = 'login.html';
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
   * 7. 전역 노출
   * ----------------------------------------------------------------- */
  window.MemberAPI = MemberAPI;
  window.MemberUI = MemberUI;
  window.ApiError = ApiError;
})();
