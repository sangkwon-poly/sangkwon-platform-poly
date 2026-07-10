/* =====================================================================
 * mypage.js: 마이페이지(내 정보) 페이지 스크립트
 * 여기콕 상권분석 플랫폼 · 김민혁(member)
 *
 * 의존: js/api.js (window.MemberAPI, window.MemberUI)
 * 화면: 프로필 / 정보 수정 / 최근 검색 / 계정 관리(로그아웃·탈퇴)
 * ===================================================================== */
(function () {
  'use strict';

  var API = window.MemberAPI;
  var UI = window.MemberUI;

  // role 코드 -> 한국어 라벨 + 배지 스타일.
  var ROLE_LABEL = { USER: '일반 회원', PREMIUM: '프리미엄 회원' };
  // status 코드 -> 한국어 라벨.
  var STATUS_LABEL = {
    ACTIVE: '활성', DORMANT: '휴면', BANNED: '이용제한', WITHDRAWN: '탈퇴',
  };

  function esc(str) { return UI.escapeHtml(str); }
  function fmt(iso) { return UI.formatDate(iso); }

  function $(id) { return document.getElementById(id); }

  /* -----------------------------------------------------------------
   * 섹션1: 프로필 카드
   * --------------------------------------------------------------- */
  function renderProfile(me) {
    var initial = (me.nickname || me.loginId || '?').slice(0, 1);
    var roleLabel = ROLE_LABEL[me.role] || me.role || '-';
    var roleBadge = me.role === 'PREMIUM' ? 'badge--brand' : 'badge--muted';
    var statusLabel = STATUS_LABEL[me.status] || me.status || '-';
    var statusBadge = me.status === 'ACTIVE' ? 'badge--ok' : 'badge--muted';

    var html =
      '<div class="profile-head">' +
        '<span class="fav-item__avatar" aria-hidden="true">' + esc(initial) + '</span>' +
        '<div class="stack stack--tight profile-main">' +
          '<div class="row row--wrap stack--tight">' +
            '<strong class="profile-name">' + esc(me.nickname || '-') + '</strong>' +
            '<span class="badge ' + roleBadge + '">' + esc(roleLabel) + '</span>' +
            '<span class="badge ' + statusBadge + '">' + esc(statusLabel) + '</span>' +
          '</div>' +
          '<span class="text-muted text-mono">@' + esc(me.loginId) + '</span>' +
        '</div>' +
      '</div>' +
      '<div class="profile-kpis">' +
        kpi('이메일', me.email || '-') +
        kpi('가입일', fmt(me.createdAt)) +
        kpi('마지막 로그인', me.lastLoginAt ? fmt(me.lastLoginAt) : '기록 없음') +
      '</div>';

    $('profile-body').innerHTML = html;
  }

  // 라벨/값 kpi 블록(텍스트형). 값은 escape.
  function kpi(label, value) {
    return '<div class="kpi kpi--text">' +
      '<span class="kpi__label">' + esc(label) + '</span>' +
      '<span class="kpi__value">' + esc(value) + '</span>' +
      '</div>';
  }

  /* -----------------------------------------------------------------
   * 섹션2: 정보 수정 폼
   * --------------------------------------------------------------- */
  function fillEditForm(me) {
    $('edit-nickname').value = me.nickname || '';
    $('edit-email').value = me.email || '';
    $('edit-loginid').value = me.loginId || '';
  }

  function setFieldError(inputId, errorId, message) {
    var input = $(inputId);
    var errorEl = $(errorId);
    if (message) {
      input.classList.add('input--invalid');
      input.setAttribute('aria-invalid', 'true');
      errorEl.textContent = message;
    } else {
      input.classList.remove('input--invalid');
      input.removeAttribute('aria-invalid');
      errorEl.textContent = '';
    }
  }

  function validateEdit(nickname, email) {
    var ok = true;
    setFieldError('edit-nickname', 'edit-nickname-error', '');
    setFieldError('edit-email', 'edit-email-error', '');

    if (!nickname) {
      setFieldError('edit-nickname', 'edit-nickname-error', '닉네임을 입력해 주세요.');
      ok = false;
    } else if (nickname.length > 50) {
      setFieldError('edit-nickname', 'edit-nickname-error', '닉네임은 50자 이하로 입력해 주세요.');
      ok = false;
    }

    if (!email) {
      setFieldError('edit-email', 'edit-email-error', '이메일을 입력해 주세요.');
      ok = false;
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setFieldError('edit-email', 'edit-email-error', '올바른 이메일 형식이 아니에요.');
      ok = false;
    }
    return ok;
  }

  function bindEditForm() {
    var form = $('edit-form');
    var submitBtn = $('edit-submit');

    // 값을 고치는 즉시 해당 필드의 오류 표시를 지운다(제출 때까지 붙어 있지 않게).
    ['edit-nickname', 'edit-email'].forEach(function (fid) {
      $(fid).addEventListener('input', function () {
        setFieldError(fid, fid + '-error', '');
      });
    });

    form.addEventListener('submit', function (e) {
      e.preventDefault();
      var nickname = $('edit-nickname').value.trim();
      var email = $('edit-email').value.trim();

      if (!validateEdit(nickname, email)) return;

      submitBtn.classList.add('is-loading');
      submitBtn.disabled = true;

      API.updateMe({ nickname: nickname, email: email })
        .then(function (updated) {
          UI.toast('정보를 저장했어요.', 'ok');
          // 프로필 카드/헤더 최신화.
          renderProfile(updated);
          fillEditForm(updated);
          UI.renderHeader('mypage');
        })
        .catch(function (err) {
          // 이메일 중복(M003)은 필드에만 표시하고, 같은 내용을 토스트로 또 띄우지 않는다.
          if (err && err.code === 'M003') {
            setFieldError('edit-email', 'edit-email-error', err.message || '이미 사용 중인 이메일입니다.');
          } else {
            UI.handleError(err, '정보를 저장하지 못했어요.');
          }
        })
        .finally(function () {
          submitBtn.classList.remove('is-loading');
          submitBtn.disabled = false;
        });
    });
  }

  /* -----------------------------------------------------------------
   * 섹션3: 최근 검색
   * --------------------------------------------------------------- */
  var searchLogs = [];
  var searchPage = 0;
  var SEARCH_PAGE_SIZE = 5;

  function renderSearchLogs() {
    var body = $('search-body');
    if (!searchLogs.length) {
      body.innerHTML =
        '<div class="empty">' +
          '<span class="empty__icon" aria-hidden="true">' + searchIcon() + '</span>' +
          '<span class="empty__title">최근 검색 기록이 없어요</span>' +
          '<span class="empty__desc">지도에서 상권을 검색하면 여기에 기록이 남아요.</span>' +
        '</div>';
      return;
    }

    var totalPages = Math.ceil(searchLogs.length / SEARCH_PAGE_SIZE);
    if (searchPage > totalPages - 1) searchPage = totalPages - 1;
    var start = searchPage * SEARCH_PAGE_SIZE;

    var items = searchLogs.slice(start, start + SEARCH_PAGE_SIZE).map(function (log) {
      return '<div class="fav-item">' +
        '<span class="fav-item__avatar" aria-hidden="true">' + searchIcon() + '</span>' +
        '<div class="fav-item__body">' +
          '<span class="fav-item__name">' + esc(log.keyword) + '</span>' +
          '<span class="fav-item__meta">' + esc(fmt(log.searchedAt)) + '</span>' +
        '</div>' +
        '<div class="fav-item__actions">' +
          '<button type="button" class="btn btn--danger btn--sm" data-action="del-search" data-kw="' + esc(log.keyword) + '" ' +
            'aria-label="' + esc(log.keyword) + ' 삭제">삭제</button>' +
        '</div>' +
      '</div>';
    }).join('');

    body.innerHTML =
      '<div class="row row--between" style="margin-bottom:10px">' +
        '<span class="text-muted" style="font-size:13px">최근 검색 ' + searchLogs.length + '개</span>' +
        '<button type="button" class="btn btn--ghost btn--sm" data-action="clear-search">전체 삭제</button>' +
      '</div>' +
      '<div class="list">' + items + '</div>' +
      searchPager(totalPages);

    bindSearchEvents();
  }

  function searchPager(totalPages) {
    if (totalPages <= 1) return '';
    var prevDis = searchPage === 0 ? ' disabled' : '';
    var nextDis = searchPage >= totalPages - 1 ? ' disabled' : '';
    return '<div class="pager" style="display:flex;justify-content:center;align-items:center;gap:12px;margin-top:14px">' +
      '<button type="button" class="btn btn--ghost btn--sm" data-page="prev"' + prevDis + '>이전</button>' +
      '<span class="text-muted" style="font-size:13px">' + (searchPage + 1) + ' / ' + totalPages + '</span>' +
      '<button type="button" class="btn btn--ghost btn--sm" data-page="next"' + nextDis + '>다음</button>' +
    '</div>';
  }

  function bindSearchEvents() {
    var body = $('search-body');
    Array.prototype.forEach.call(body.querySelectorAll('[data-action="del-search"]'), function (btn) {
      btn.addEventListener('click', function () {
        onDeleteSearch(btn.getAttribute('data-kw'), btn);
      });
    });
    var clear = body.querySelector('[data-action="clear-search"]');
    if (clear) clear.addEventListener('click', onClearSearch);
    var prev = body.querySelector('[data-page="prev"]');
    if (prev) prev.addEventListener('click', function () {
      if (searchPage > 0) { searchPage--; renderSearchLogs(); }
    });
    var next = body.querySelector('[data-page="next"]');
    if (next) next.addEventListener('click', function () {
      var tp = Math.ceil(searchLogs.length / SEARCH_PAGE_SIZE);
      if (searchPage < tp - 1) { searchPage++; renderSearchLogs(); }
    });
  }

  function onDeleteSearch(keyword, btn) {
    btn.disabled = true;
    API.deleteSearchLog(keyword)
      .then(function () {
        searchLogs = searchLogs.filter(function (l) { return l.keyword !== keyword; });
        renderSearchLogs();
      })
      .catch(function (err) {
        UI.handleError(err, '삭제하지 못했어요.');
        btn.disabled = false;
      });
  }

  function onClearSearch() {
    if (!window.confirm('최근 검색 기록을 모두 지울까요?')) return;
    API.clearSearchLogs()
      .then(function () {
        searchLogs = [];
        renderSearchLogs();
        UI.toast('검색 기록을 지웠어요.', 'ok');
      })
      .catch(function (err) { UI.handleError(err, '삭제하지 못했어요.'); });
  }

  function searchIcon() {
    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" ' +
      'stroke-linecap="round" stroke-linejoin="round" width="20" height="20">' +
      '<circle cx="11" cy="11" r="7"></circle>' +
      '<line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>';
  }

  /* -----------------------------------------------------------------
   * 섹션4: 계정 관리 (로그아웃 / 탈퇴)
   * --------------------------------------------------------------- */
  function bindLogout() {
    var btn = $('logout-btn');
    btn.addEventListener('click', function () {
      btn.classList.add('is-loading');
      btn.disabled = true;
      API.logout()
        .catch(function (err) {
          // 로그아웃은 실패해도 로컬 정리 후 로그인 화면으로 보낸다.
          console.error('[mypage] 로그아웃 실패:', err);
        })
        .finally(function () {
          location.href = 'login';
        });
    });
  }

  function bindWithdraw() {
    var openBtn = $('withdraw-btn');
    var modal = $('withdraw-modal');
    var dialog = modal.querySelector('.modal');
    var cancelBtn = $('withdraw-cancel');
    var confirmBtn = $('withdraw-confirm');
    var pageEl = document.querySelector('.page');

    function openModal() {
      modal.hidden = false;
      // 배경을 비활성화해 포커스와 스크린리더가 모달 밖으로 새지 않게 한다.
      if (pageEl) pageEl.setAttribute('inert', '');
      // 되돌릴 수 없는 동작이라 기본 포커스는 확인이 아니라 취소에 둔다.
      cancelBtn.focus();
      document.addEventListener('keydown', onKeydown);
    }
    function closeModal() {
      modal.hidden = true;
      if (pageEl) pageEl.removeAttribute('inert');
      document.removeEventListener('keydown', onKeydown);
      openBtn.focus();
    }
    function onKeydown(e) {
      if (e.key === 'Escape') { closeModal(); return; }
      if (e.key !== 'Tab') return;
      // Tab 이동을 모달 안에 가둔다(활성 버튼만 대상).
      var items = dialog.querySelectorAll('button:not([disabled])');
      if (!items.length) return;
      var first = items[0];
      var last = items[items.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    }

    openBtn.addEventListener('click', openModal);
    cancelBtn.addEventListener('click', closeModal);
    // 배경 클릭 시 닫기(모달 내부 클릭은 무시).
    modal.addEventListener('click', function (e) {
      if (e.target === modal) closeModal();
    });

    confirmBtn.addEventListener('click', function () {
      confirmBtn.classList.add('is-loading');
      confirmBtn.disabled = true;
      cancelBtn.disabled = true;
      API.withdraw()
        .then(function () {
          // 탈퇴 완료 -> 로그인 화면으로.
          location.href = 'login';
        })
        .catch(function (err) {
          UI.handleError(err, '회원 탈퇴를 처리하지 못했어요.');
          confirmBtn.classList.remove('is-loading');
          confirmBtn.disabled = false;
          cancelBtn.disabled = false;
          closeModal();
        });
    });
  }

  /* -----------------------------------------------------------------
   * 최근 검색 로드(프로필 로드 성공 = 로그인 확정 후 별도 로드)
   * --------------------------------------------------------------- */
  function loadSearchLogs() {
    API.searchLogs()
      .then(function (list) {
        searchLogs = list || [];
        searchPage = 0;
        renderSearchLogs();
      })
      .catch(function (err) {
        UI.handleError(err, '검색 기록을 불러오지 못했어요.');
        $('search-body').innerHTML =
          '<div class="empty">' +
            '<span class="empty__title">검색 기록을 불러올 수 없어요</span>' +
            '<span class="empty__desc">잠시 후 다시 시도해 주세요.</span>' +
          '</div>';
      });
  }

  /* -----------------------------------------------------------------
   * 초기화
   * --------------------------------------------------------------- */
  async function init() {
    // 앱 헤더 렌더(비동기, 상태는 내부에서 조회).
    UI.renderHeader('mypage');

    // 인증 가드: 비로그인 시 login.html 로 리다이렉트하고 me 반환.
    var me = await UI.requireAuth();
    if (!me) return; // 리다이렉트됨.

    // 로그인 상태 이벤트 바인딩.
    bindEditForm();
    bindLogout();
    bindWithdraw();

    // 프로필/폼 채우기.
    renderProfile(me);
    fillEditForm(me);

    // 최근 검색 로드.
    loadSearchLogs();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
