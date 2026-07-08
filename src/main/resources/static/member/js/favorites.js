/* =====================================================================
 * favorites.js: 내 찜 상권 페이지 로직
 * 서울공화국 상권분석 플랫폼 · 김민혁(member)
 *
 * 담당: 앱 헤더 렌더 / 로그인 가드 / 찜 목록 로드·렌더 / 찜 해제.
 * MemberAPI · MemberUI(api.js 제공)만 사용한다. 새 CSS 클래스는 만들지 않는다.
 * ===================================================================== */
(function () {
  'use strict';

  // 지도 페이지 경로. 실제 map 도메인 자리표시자.
  // TODO: map(이상혁) 도메인 확정 시 조정. 현재는 03-map 참고. ../map/index.html.
  var MAP_HREF = '../map/index.html';

  var region = document.getElementById('fav-region');
  var countChip = document.getElementById('fav-count');

  document.addEventListener('DOMContentLoaded', init);

  async function init() {
    // 헤더 렌더(찜 탭 활성).
    MemberUI.renderHeader('favorites');

    // 비로그인 시 login.html 로 유도하고 중단.
    var me = await MemberUI.requireAuth();
    if (!me) return;

    loadFavorites();
  }

  /* 찜 목록을 불러와 렌더한다. */
  async function loadFavorites() {
    showLoading();
    try {
      var list = await MemberAPI.favorites();
      renderList(list || []);
    } catch (err) {
      MemberUI.handleError(err, '찜 목록을 불러오지 못했습니다.');
      renderError();
    }
  }

  function showLoading() {
    region.innerHTML =
      '<div class="loading-block">' +
        '<span class="spinner spinner--lg" aria-hidden="true"></span>' +
        '<span>찜한 상권을 불러오는 중입니다…</span>' +
      '</div>';
  }

  function renderError() {
    if (countChip) countChip.hidden = true;
    region.innerHTML =
      '<div class="empty">' +
        emptyIcon() +
        '<div class="empty__title">목록을 불러오지 못했어요</div>' +
        '<p class="empty__desc">잠시 후 다시 시도해 주세요.</p>' +
        '<button type="button" class="btn btn--ghost" data-action="retry">다시 시도</button>' +
      '</div>';
    var retry = region.querySelector('[data-action="retry"]');
    if (retry) retry.addEventListener('click', loadFavorites);
  }

  /* 목록 렌더: 비었으면 빈 상태, 아니면 찜 카드 리스트. */
  function renderList(list) {
    updateCount(list.length);

    if (!list.length) {
      renderEmpty();
      return;
    }

    var itemsHtml = list.map(favItemHtml).join('');
    region.innerHTML = '<div class="list">' + itemsHtml + '</div>';

    // 찜 해제 버튼 바인딩.
    var buttons = region.querySelectorAll('[data-action="remove"]');
    Array.prototype.forEach.call(buttons, function (btn) {
      btn.addEventListener('click', function () {
        onRemove(btn.getAttribute('data-trdar'), btn);
      });
    });
  }

  function updateCount(n) {
    if (!countChip) return;
    countChip.hidden = false;
    countChip.textContent = '찜 ' + n + '곳';
  }

  /* 아바타 이니셜: 상권명 > 자치구 > 상권코드 마지막 숫자 순. */
  function initialFor(fav) {
    if (fav.trdarNm) return String(fav.trdarNm).slice(0, 1);
    if (fav.signguNm) return String(fav.signguNm).slice(0, 1);
    if (fav.trdarCd) {
      var digits = String(fav.trdarCd).replace(/\D/g, '');
      if (digits) return digits.slice(-1);
    }
    return '?';
  }

  /* 찜 카드 한 개의 HTML. */
  function favItemHtml(fav) {
    var esc = MemberUI.escapeHtml;
    var name = fav.trdarNm || ('상권 ' + (fav.trdarCd || ''));
    // 이니셜: 상권명 첫 글자 > 자치구 첫 글자 > 상권코드 마지막 숫자 > '?'.
    // (fallback name 이 '상권 XXXX'라 항상 '상'이 되던 문제 개선)
    var initial = initialFor(fav);

    // 메타: 자치구 + 찜한 날짜 + 상권코드.
    var metaParts = [];
    if (fav.signguNm) {
      metaParts.push('<span>' + esc(fav.signguNm) + '</span>');
    }
    if (fav.createdAt) {
      metaParts.push('<span>' + esc(MemberUI.formatDate(fav.createdAt)) + ' 찜</span>');
    }
    if (fav.trdarCd) {
      metaParts.push('<span class="text-mono">' + esc(fav.trdarCd) + '</span>');
    }

    return '' +
      '<div class="fav-item">' +
        '<span class="fav-item__avatar" aria-hidden="true">' + esc(initial) + '</span>' +
        '<div class="fav-item__body">' +
          '<span class="fav-item__name">' + esc(name) + '</span>' +
          '<span class="fav-item__meta">' + metaParts.join('') + '</span>' +
        '</div>' +
        '<div class="fav-item__actions">' +
          '<button type="button" class="btn btn--danger btn--sm" ' +
            'data-action="remove" data-trdar="' + esc(fav.trdarCd) + '" ' +
            'aria-label="' + esc(name) + ' 찜 해제">찜 해제</button>' +
        '</div>' +
      '</div>';
  }

  /* 찜 해제 처리: API 호출 → 목록 갱신 → 토스트. */
  async function onRemove(trdarCd, btn) {
    if (!trdarCd) return;
    btn.classList.add('is-loading');
    btn.setAttribute('disabled', 'disabled');
    try {
      await MemberAPI.removeFavorite(trdarCd);
      MemberUI.toast('찜을 해제했습니다.', 'ok');
      // 최신 상태로 다시 로드(개수/빈 상태 동기화).
      await loadFavorites();
    } catch (err) {
      MemberUI.handleError(err, '찜 해제에 실패했습니다.');
      btn.classList.remove('is-loading');
      btn.removeAttribute('disabled');
    }
  }

  /* 빈 상태: 지도로 가서 상권을 찜하라는 안내. */
  function renderEmpty() {
    region.innerHTML =
      '<div class="empty">' +
        emptyIcon() +
        '<div class="empty__title">아직 찜한 상권이 없어요</div>' +
        '<p class="empty__desc">지도에서 관심 있는 상권을 찾아 하트를 누르면 여기에 모여요.</p>' +
        '<a class="btn btn--primary" href="' + MemberUI.escapeHtml(MAP_HREF) + '">지도 보기</a>' +
      '</div>';
  }

  /* 빈 상태/에러 공용 아이콘(인라인 SVG, 외부 아이콘폰트 금지). */
  function emptyIcon() {
    return '' +
      '<span class="empty__icon" aria-hidden="true">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
          'stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">' +
          '<path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.6l-1-1a5.5 5.5 0 0 0-7.8 7.8' +
          'l1 1L12 21l7.8-7.6 1-1a5.5 5.5 0 0 0 0-7.8z"/>' +
        '</svg>' +
      '</span>';
  }
})();
