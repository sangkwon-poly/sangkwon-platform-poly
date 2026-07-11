(function () {
  'use strict';

  const region = document.getElementById('iq-region');
  const PAGE_SIZE = 8;

  const STATUS = {
    OPEN:     { label: '대기중',   badge: 'badge--wip' },
    ANSWERED: { label: '답변완료', badge: 'badge--ok' },
    CLOSED:   { label: '닫힘',     badge: 'badge--muted' },
  };

  let page = 0;
  const detailCache = {};

  document.addEventListener('DOMContentLoaded', init);

  async function init() {
    MemberUI.renderHeader('inquiries');
    const me = await MemberUI.requireAuth();
    if (!me) return;
    load(0);
  }

  async function load(p) {
    showLoading();
    try {
      const d = await MemberAPI.myInquiries(p, PAGE_SIZE);
      if (d.totalElements === 0) { renderEmpty(); return; }
      // 삭제 등으로 마지막 페이지가 비면 한 페이지 당겨서 다시 조회
      if (!d.content.length && d.page > 0) { load(d.page - 1); return; }
      page = d.page;
      render(d);
    } catch (err) {
      MemberUI.handleError(err, '문의 내역을 불러오지 못했습니다.');
      renderError();
    }
  }

  function render(d) {
    region.innerHTML = '<div class="iq-list">' + d.content.map(itemHtml).join('') + '</div>' + pagerHtml(d);
    bindRows();
    bindPager(d);
  }

  function itemHtml(q) {
    const esc = MemberUI.escapeHtml;
    const s = STATUS[q.status] || { label: q.status, badge: 'badge--muted' };
    // 아직 확인하지 않은 새 답변이면 NEW 배지로 알린다. 답변을 펼쳐 보면 서버에서 확인 처리된다.
    const isNew = q.unreadAnswer === true;
    return '' +
      '<div class="iq-row' + (isNew ? ' iq-row--new' : '') + '" data-id="' + esc(q.inquiryId) + '">' +
        '<button type="button" class="iq-row__head" aria-expanded="false">' +
          '<span class="badge ' + s.badge + '">' + esc(s.label) + '</span>' +
          (isNew ? '<span class="iq-new" aria-label="새 답변">NEW</span>' : '') +
          '<span class="iq-row__title">' + esc(q.title) + '</span>' +
          '<span class="iq-row__date text-muted">' + esc(MemberUI.formatDate(q.createdAt)) + '</span>' +
          '<span class="iq-row__arrow" aria-hidden="true"></span>' +
        '</button>' +
        '<div class="iq-row__body" hidden></div>' +
      '</div>';
  }

  function bindRows() {
    region.querySelectorAll('.iq-row').forEach(function (row) {
      const head = row.querySelector('.iq-row__head');
      head.addEventListener('click', function () { toggle(row); });
    });
  }

  function toggle(row) {
    const body = row.querySelector('.iq-row__body');
    const head = row.querySelector('.iq-row__head');
    const open = !body.hidden;
    if (open) {
      body.hidden = true;
      head.setAttribute('aria-expanded', 'false');
      row.classList.remove('is-open');
      return;
    }
    head.setAttribute('aria-expanded', 'true');
    row.classList.add('is-open');
    body.hidden = false;
    if (!body.dataset.loaded) {
      body.innerHTML = '<p class="iq-loading-inline">불러오는 중…</p>';
      const id = row.getAttribute('data-id');
      fetchDetail(id)
        .then(function (q) {
          body.dataset.loaded = '1';
          body.innerHTML = detailHtml(q);
          // 답변을 열람했으니 NEW 표시를 제거한다(서버에서도 확인 처리됨)
          row.classList.remove('iq-row--new');
          const badge = row.querySelector('.iq-new');
          if (badge) { badge.remove(); }
        })
        .catch(function (err) {
          MemberUI.handleError(err, '문의 내용을 불러오지 못했습니다.');
          body.innerHTML = '<p class="iq-loading-inline">내용을 불러오지 못했어요.</p>';
        });
    }
  }

  function fetchDetail(id) {
    if (detailCache[id]) return Promise.resolve(detailCache[id]);
    return MemberAPI.inquiry(id).then(function (q) { detailCache[id] = q; return q; });
  }

  function detailHtml(q) {
    const esc = MemberUI.escapeHtml;
    let html = '<div class="iq-q">' + esc(q.content) + '</div>';
    if (q.answer) {
      html +=
        '<div class="iq-answer">' +
          '<div class="iq-answer__head">여기콕 답변' +
            '<span class="iq-answer__meta">' + esc(q.adminName || '운영팀') +
            ' · ' + esc(MemberUI.formatDate(q.answeredAt)) + '</span></div>' +
          '<div class="iq-answer__body">' + esc(q.answer) + '</div>' +
        '</div>';
    } else {
      html += '<p class="iq-wait">아직 답변이 등록되지 않았어요. 확인 후 순차적으로 답변드릴게요.</p>';
    }
    return html;
  }

  function pagerHtml(d) {
    if (d.totalPages <= 1) return '';
    const prevDis = d.page === 0 ? ' disabled' : '';
    const nextDis = (d.page + 1) >= d.totalPages ? ' disabled' : '';
    return '' +
      '<div class="pager" style="display:flex;justify-content:center;align-items:center;gap:12px;margin-top:18px">' +
        '<button type="button" class="btn btn--ghost btn--sm" data-page="prev"' + prevDis + '>이전</button>' +
        '<span class="text-muted" style="font-size:13px">' + (d.page + 1) + ' / ' + d.totalPages + '</span>' +
        '<button type="button" class="btn btn--ghost btn--sm" data-page="next"' + nextDis + '>다음</button>' +
      '</div>';
  }

  function bindPager(d) {
    const prev = region.querySelector('[data-page="prev"]');
    const next = region.querySelector('[data-page="next"]');
    if (prev) prev.addEventListener('click', function () { if (d.page > 0) load(d.page - 1); });
    if (next) next.addEventListener('click', function () { if ((d.page + 1) < d.totalPages) load(d.page + 1); });
  }

  function showLoading() {
    region.innerHTML =
      '<div class="loading-block">' +
        '<span class="spinner spinner--lg" aria-hidden="true"></span>' +
        '<span>문의 내역을 불러오는 중입니다…</span>' +
      '</div>';
  }

  function renderEmpty() {
    region.innerHTML =
      '<div class="empty">' +
        emptyIcon() +
        '<div class="empty__title">아직 남긴 문의가 없어요</div>' +
        '<p class="empty__desc">궁금한 점이 생기면 언제든 문의를 남겨주세요.</p>' +
        '<a class="btn btn--primary" href="/inquiry">문의하기</a>' +
      '</div>';
  }

  function renderError() {
    region.innerHTML =
      '<div class="empty">' +
        emptyIcon() +
        '<div class="empty__title">문의 내역을 불러오지 못했어요</div>' +
        '<p class="empty__desc">잠시 후 다시 시도해 주세요.</p>' +
        '<button type="button" class="btn btn--ghost" data-action="retry">다시 시도</button>' +
      '</div>';
    const retry = region.querySelector('[data-action="retry"]');
    if (retry) retry.addEventListener('click', function () { load(page); });
  }

  function emptyIcon() {
    return '' +
      '<span class="empty__icon" aria-hidden="true">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
          'stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">' +
          '<path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>' +
        '</svg>' +
      '</span>';
  }
})();
