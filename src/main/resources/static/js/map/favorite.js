(function () {
  'use strict';

  document.addEventListener('DOMContentLoaded', init);

  function init() {
    var trdarCd = new URLSearchParams(location.search).get('trdarCd');
    if (!trdarCd) return;

    api('GET', '/api/favorites')
      .then(function (list) {
        var faved = Array.isArray(list) && list.some(function (f) { return f.trdarCd === trdarCd; });
        addButton(trdarCd, faved);
      })
      .catch(function () { /* 비로그인 등 → 버튼 없음 */ });
  }

  function addButton(trdarCd, faved) {
    var report = document.querySelector('.detail-btn-primary');
    if (!report) return;

    var btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'detail-btn';
    btn.dataset.faved = faved ? 'true' : 'false';
    render(btn);
    report.parentNode.insertBefore(btn, report);

    btn.addEventListener('click', function () {
      var isFaved = btn.dataset.faved === 'true';
      btn.disabled = true;
      var req = isFaved
        ? api('DELETE', '/api/favorites/' + encodeURIComponent(trdarCd))
        : api('POST', '/api/favorites', { trdarCd: trdarCd });
      req
        .then(function () { setFaved(btn, !isFaved); })
        .catch(function (err) {
          if (err.code === 'M005') {
            location.href = '/member/login?redirect=' + encodeURIComponent(location.pathname + location.search);
          } else if (err.code === 'M006') {  // 이미 찜한 상권
            setFaved(btn, true);
          } else {
            alert(err.message || '처리에 실패했습니다.');
          }
        })
        .finally(function () { btn.disabled = false; });
    });
  }

  function setFaved(btn, faved) {
    btn.dataset.faved = faved ? 'true' : 'false';
    render(btn);
  }

  function render(btn) {
    btn.textContent = btn.dataset.faved === 'true' ? '♥ 찜 해제' : '♡ 찜하기';
  }

  function api(method, path, body) {
    return fetch(path, {
      method: method,
      headers: body ? { 'Content-Type': 'application/json' } : {},
      credentials: 'include',
      body: body ? JSON.stringify(body) : undefined,
    }).then(function (res) {
      return res.text().then(function (text) {
        var payload = text ? JSON.parse(text) : null;
        if (!res.ok || (payload && payload.success === false)) {
          var e = new Error((payload && payload.message) || '요청 실패');
          e.code = payload && payload.code;
          throw e;
        }
        return payload ? payload.data : null;
      });
    });
  }
})();
