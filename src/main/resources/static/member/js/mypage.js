/* =====================================================================
 * mypage.js — 마이페이지(내 정보) 페이지 스크립트
 * 서울공화국 상권분석 플랫폼 · 김민혁(member)
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
          UI.handleError(err, '정보를 저장하지 못했어요.');
          // 서버가 이메일 중복(M003)을 돌려주면 해당 필드에 표시.
          if (err && err.code === 'M003') {
            setFieldError('edit-email', 'edit-email-error', err.message || '이미 사용 중인 이메일입니다.');
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
  function renderSearchLogs(logs) {
    var body = $('search-body');
    if (!logs || logs.length === 0) {
      body.innerHTML =
        '<div class="empty">' +
          '<span class="empty__icon" aria-hidden="true">' + searchIcon() + '</span>' +
          '<span class="empty__title">최근 검색 기록이 없어요</span>' +
          '<span class="empty__desc">지도에서 상권을 검색하면 여기에 기록이 남아요.</span>' +
        '</div>';
      return;
    }

    var items = logs.map(function (log) {
      var metaParts = [fmt(log.searchedAt)];
      if (log.trdarCd) {
        metaParts.push('상권코드 ' + esc(log.trdarCd));
      }
      var meta = metaParts.map(function (p, i) {
        return i === 0 ? esc(p) : '<span aria-hidden="true">·</span> ' + p;
      }).join(' ');

      return '<div class="fav-item">' +
        '<span class="fav-item__avatar" aria-hidden="true">' + searchIcon() + '</span>' +
        '<div class="fav-item__body">' +
          '<span class="fav-item__name">' + esc(log.keyword) + '</span>' +
          '<span class="fav-item__meta">' + meta + '</span>' +
        '</div>' +
      '</div>';
    }).join('');

    body.innerHTML = '<div class="list">' + items + '</div>';
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
          location.href = 'login.html';
        });
    });
  }

  function bindWithdraw() {
    var openBtn = $('withdraw-btn');
    var modal = $('withdraw-modal');
    var cancelBtn = $('withdraw-cancel');
    var confirmBtn = $('withdraw-confirm');

    function openModal() {
      modal.hidden = false;
      confirmBtn.focus();
      document.addEventListener('keydown', onKeydown);
    }
    function closeModal() {
      modal.hidden = true;
      document.removeEventListener('keydown', onKeydown);
      openBtn.focus();
    }
    function onKeydown(e) {
      if (e.key === 'Escape') closeModal();
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
          location.href = 'login.html';
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
      .then(renderSearchLogs)
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
