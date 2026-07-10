(function () {
  'use strict';

  const region = document.getElementById('fav-region');
  const countChip = document.getElementById('fav-count');

  // 카드 한 칸 높이(.fav-item + .list gap) 대략치. 화면 높이에 맞춰 페이지당 개수를 잡는다.
  const CARD_H = 96;
  const MIN_PER_PAGE = 3;

  let all = [];
  let page = 0;
  let perPage = 10;
  let resizeTimer = null;
  let renderToken = 0;
  const districtCache = {};

  document.addEventListener('DOMContentLoaded', init);
  window.addEventListener('resize', onResize);

  async function init() {
    MemberUI.renderHeader('favorites');
    const me = await MemberUI.requireAuth();
    if (!me) return;
    loadFavorites();
  }

  async function loadFavorites() {
    showLoading();
    try {
      all = (await MemberAPI.favorites()) || [];
      updateCount(all.length);
      if (!all.length) { renderEmpty(); return; }
      perPage = perPageForViewport();
      if (page > lastPage()) page = lastPage();
      await renderPage();
    } catch (err) {
      MemberUI.handleError(err, '찜 목록을 불러오지 못했습니다.');
      renderError();
    }
  }

  function lastPage() {
    return Math.max(0, Math.ceil(all.length / perPage) - 1);
  }

  // region 위쪽(헤더·제목)을 뺀 나머지 높이에 카드가 몇 개 들어가는지
  function perPageForViewport() {
    const top = region.getBoundingClientRect().top;
    const avail = window.innerHeight - top - 96; // 96 = 페이저 + 여백
    return Math.max(MIN_PER_PAGE, Math.floor(avail / CARD_H));
  }

  async function renderPage() {
    const token = ++renderToken;
    const start = page * perPage;
    const enriched = await Promise.all(all.slice(start, start + perPage).map(withDistrict));
    if (token !== renderToken) return; // 더 최신 렌더가 있으면 버린다
    region.innerHTML = '<div class="list">' + enriched.map(favItemHtml).join('') + '</div>' + pagerHtml();
    bindRows();
    bindPager();
  }

  function onResize() {
    if (!all.length) return;
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(() => {
      const first = page * perPage; // 현재 페이지 첫 항목을 유지
      perPage = perPageForViewport();
      page = Math.min(Math.floor(first / perPage), lastPage());
      renderPage();
    }, 150);
  }

  // 상권명은 찜 응답에 없어 map districts API로 채운다. 보이는 페이지만 조회하고 캐시한다.
  async function withDistrict(fav) {
    if (!(fav.trdarCd in districtCache)) {
      const d = await districtName(fav.trdarCd);
      districtCache[fav.trdarCd] = d ? { trdarNm: d.trdarNm, signguNm: d.signguNm } : null;
    }
    const c = districtCache[fav.trdarCd];
    return c ? { ...fav, trdarNm: c.trdarNm, signguNm: c.signguNm } : fav;
  }

  function districtName(trdarCd) {
    return fetch('/api/districts/' + encodeURIComponent(trdarCd), { credentials: 'include' })
      .then((res) => res.json())
      .then((body) => (body && body.success ? body.data : null))
      .catch(() => null);
  }

  function bindRows() {
    region.querySelectorAll('.fav-item').forEach((card) => {
      const trdarCd = card.getAttribute('data-trdar');
      const goDetail = () => {
        location.href = '/map/trdar-detail?trdarCd=' + encodeURIComponent(trdarCd);
      };
      card.style.cursor = 'pointer';
      card.addEventListener('click', goDetail);
      card.addEventListener('keydown', (e) => {
        // 내부 찜 해제 버튼의 키 입력은 그대로 두고, 카드 자체에서만 Enter/Space로 이동한다.
        if (e.target !== card) return;
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          goDetail();
        }
      });
      const removeBtn = card.querySelector('[data-action="remove"]');
      removeBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        onRemove(trdarCd, removeBtn);
      });
    });
  }

  function pagerHtml() {
    const total = lastPage() + 1;
    if (total <= 1) return '';
    const prevDis = page === 0 ? ' disabled' : '';
    const nextDis = page >= lastPage() ? ' disabled' : '';
    return '' +
      '<div class="pager" style="display:flex;justify-content:center;align-items:center;gap:12px;margin-top:18px">' +
        '<button type="button" class="btn btn--ghost btn--sm" data-page="prev"' + prevDis + '>이전</button>' +
        '<span class="text-muted" style="font-size:13px">' + (page + 1) + ' / ' + total + '</span>' +
        '<button type="button" class="btn btn--ghost btn--sm" data-page="next"' + nextDis + '>다음</button>' +
      '</div>';
  }

  function bindPager() {
    const prev = region.querySelector('[data-page="prev"]');
    const next = region.querySelector('[data-page="next"]');
    if (prev) prev.addEventListener('click', () => { if (page > 0) { page--; renderPage(); } });
    if (next) next.addEventListener('click', () => { if (page < lastPage()) { page++; renderPage(); } });
  }

  function favItemHtml(fav) {
    const esc = MemberUI.escapeHtml;
    const name = fav.trdarNm || ('상권 ' + (fav.trdarCd || ''));
    const initial = fav.trdarNm ? fav.trdarNm.slice(0, 1) : (fav.signguNm ? fav.signguNm.slice(0, 1) : '찜');

    const meta = [];
    if (fav.signguNm) meta.push('<span>' + esc(fav.signguNm) + '</span>');
    if (fav.createdAt) meta.push('<span>' + esc(MemberUI.formatDate(fav.createdAt)) + ' 찜</span>');

    return '' +
      '<div class="fav-item" data-trdar="' + esc(fav.trdarCd) + '" ' +
        'role="link" tabindex="0" aria-label="' + esc(name) + ' 상권 상세 보기">' +
        '<span class="fav-item__avatar" aria-hidden="true">' + esc(initial) + '</span>' +
        '<div class="fav-item__body">' +
          '<span class="fav-item__name">' + esc(name) + '</span>' +
          '<span class="fav-item__meta">' + meta.join('') + '</span>' +
        '</div>' +
        '<div class="fav-item__actions">' +
          '<button type="button" class="btn btn--danger btn--sm" data-action="remove" ' +
            'aria-label="' + esc(name) + ' 찜 해제">찜 해제</button>' +
        '</div>' +
      '</div>';
  }

  async function onRemove(trdarCd, btn) {
    if (!trdarCd) return;
    var idx = all.findIndex((f) => String(f.trdarCd) === String(trdarCd));
    if (idx < 0) return;

    btn.classList.add('is-loading');
    btn.setAttribute('disabled', 'disabled');

    // 낙관적 갱신: 목록에서 먼저 지우고 바로 다시 그린다(전체 스피너로 돌아가지 않게).
    var removed = all[idx];
    all.splice(idx, 1);
    updateCount(all.length);
    if (!all.length) {
      renderEmpty();
    } else {
      if (page > lastPage()) page = lastPage();
      await renderPage();
    }

    try {
      await MemberAPI.removeFavorite(trdarCd);
      MemberUI.toast('찜을 해제했습니다.', 'ok');
    } catch (err) {
      // 실패하면 원래 자리로 되돌린다.
      all.splice(idx, 0, removed);
      updateCount(all.length);
      MemberUI.handleError(err, '찜 해제에 실패했습니다.');
      await renderPage();
    }
  }

  function updateCount(n) {
    if (!countChip) return;
    countChip.hidden = false;
    countChip.textContent = '찜 ' + n + '곳';
  }

  function showLoading() {
    region.innerHTML =
      '<div class="loading-block">' +
        '<span class="spinner spinner--lg" aria-hidden="true"></span>' +
        '<span>찜한 상권을 불러오는 중입니다…</span>' +
      '</div>';
  }

  function renderEmpty() {
    if (countChip) countChip.hidden = true;
    region.innerHTML =
      '<div class="empty">' +
        emptyIcon() +
        '<div class="empty__title">아직 찜한 상권이 없어요</div>' +
        '<p class="empty__desc">지도에서 상권을 열어 찜하면 여기에 모여요.</p>' +
        '<a class="btn btn--primary" href="/map">지도 보기</a>' +
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
    const retry = region.querySelector('[data-action="retry"]');
    if (retry) retry.addEventListener('click', loadFavorites);
  }

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
