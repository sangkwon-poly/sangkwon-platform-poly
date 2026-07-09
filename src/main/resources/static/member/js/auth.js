/* =====================================================================
 * auth.js: 로그인/회원가입 페이지 동작
 * 서울공화국 상권분석 플랫폼 · 김민혁(member)
 *
 * 담당: 탭 전환, 실시간 검증(아이디·이메일 중복확인, 닉네임 규칙, 비번 확인),
 * 비밀번호 표시 토글, 제출(MemberAPI 호출).
 * 이 페이지는 인증 전 화면이라 앱 헤더를 렌더하지 않는다.
 * ===================================================================== */
(function () {
  'use strict';

  document.addEventListener('DOMContentLoaded', function () {
    var tabs = Array.prototype.slice.call(document.querySelectorAll('.tab[data-tab]'));
    var panelLogin = document.getElementById('panel-login');
    var panelSignup = document.getElementById('panel-signup');

    var ID_RE = /^[a-zA-Z0-9]{4,50}$/;
    var EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    var NICK_RE = /^[가-힣a-zA-Z0-9_-]{2,20}$/;

    /* ---------------------------------------------------------------
     * 로그인 후 복귀 경로 (login?redirect=... 지원), 없으면 찜 목록.
     * ------------------------------------------------------------- */
    function redirectTarget() {
      var params = new URLSearchParams(location.search);
      var back = params.get('redirect');
      if (back) {
        // 오픈 리다이렉트 방지: 같은 디렉터리의 영문/숫자/하이픈 페이지명만 허용.
        var safe = back.replace(/^\/+/, '');
        if (safe.indexOf(':') === -1 && /^[\w-]+(\.html)?([?#].*)?$/.test(safe)) {
          return safe;
        }
      }
      return 'favorites';
    }

    /* ---------------------------------------------------------------
     * 탭 전환
     * ------------------------------------------------------------- */
    function activateTab(key) {
      tabs.forEach(function (t) {
        var on = t.getAttribute('data-tab') === key;
        t.classList.toggle('is-active', on);
        t.setAttribute('aria-selected', on ? 'true' : 'false');
      });
      var isLogin = key === 'login';
      panelLogin.hidden = !isLogin;
      panelSignup.hidden = isLogin;
      clearErrors(isLogin ? panelLogin : panelSignup);
      var focusTarget = (isLogin ? panelLogin : panelSignup).querySelector('.input');
      if (focusTarget) focusTarget.focus();
    }

    tabs.forEach(function (t) {
      t.addEventListener('click', function () { activateTab(t.getAttribute('data-tab')); });
    });

    /* ---------------------------------------------------------------
     * 비밀번호 표시 토글 (아이콘은 aria-pressed 상태로 CSS가 전환)
     * ------------------------------------------------------------- */
    document.querySelectorAll('[data-toggle-pw]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var input = document.getElementById(btn.getAttribute('data-toggle-pw'));
        if (!input) return;
        var show = input.type === 'password';
        input.type = show ? 'text' : 'password';
        btn.setAttribute('aria-pressed', show ? 'true' : 'false');
        btn.setAttribute('aria-label', show ? '비밀번호 숨김' : '비밀번호 표시');
      });
    });

    /* ---------------------------------------------------------------
     * 필드 상태(에러 빨강 / 통과 초록 / 해제)
     * ------------------------------------------------------------- */
    function setField(inputId, message, state) {
      var input = document.getElementById(inputId);
      var errEl = document.getElementById(inputId + '-error');
      if (input) {
        input.classList.toggle('input--invalid', state === 'error');
        input.classList.toggle('input--ok', state === 'ok');
        if (state === 'error') input.setAttribute('aria-invalid', 'true');
        else input.removeAttribute('aria-invalid');
      }
      if (errEl) {
        errEl.textContent = message || '';
        errEl.classList.toggle('is-ok', state === 'ok');
        errEl.classList.remove('is-warn');
      }
    }
    function setError(inputId, message) { setField(inputId, message, 'error'); }
    function clearError(inputId) { setField(inputId, '', ''); }
    function clearErrors(form) {
      form.querySelectorAll('.input').forEach(function (i) { if (i.id) clearError(i.id); });
    }

    // 로그인 필드: 입력하면 에러 즉시 해제(실시간 검증 없음).
    panelLogin.querySelectorAll('.input').forEach(function (input) {
      input.addEventListener('input', function () { if (input.id) clearError(input.id); });
    });

    /* ---------------------------------------------------------------
     * 회원가입 실시간 검증 (디바운스 + 서버 중복확인)
     * ------------------------------------------------------------- */
    // 같은 값은 다시 조회하지 않도록 결과를 캐시(오류 결과 null은 캐시하지 않음)
    var availCache = { 'check-login-id': {}, 'check-email': {} };
    function availability(path, param, value) {
      var cache = availCache[path];
      if (cache && Object.prototype.hasOwnProperty.call(cache, value)) {
        return Promise.resolve(cache[value]);
      }
      return fetch('/api/members/' + path + '?' + param + '=' + encodeURIComponent(value),
          { headers: { Accept: 'application/json' } })
        .then(function (r) { return r.ok ? r.json() : null; })
        .then(function (b) {
          var a = (b && b.data) ? b.data.available : null;
          if (cache && a !== null) cache[value] = a;
          return a;
        })
        .catch(function () { return null; });
    }
    var sPw = document.getElementById('signup-pw');
    var sPw2 = document.getElementById('signup-pw2');
    var signupBtn = panelSignup.querySelector('[data-submit="signup"]');

    // 모든 필드가 통과해야 회원가입 버튼이 활성화된다.
    var valid = { id: false, email: false, nick: false, pw: false, pw2: false };
    function set(key, ok) {
      valid[key] = ok;
      signupBtn.disabled = !(valid.id && valid.email && valid.nick && valid.pw && valid.pw2);
    }

    // 입력 중에는 즉시 해당 항목을 무효로 두고(버튼 비활성), delay 후 실제 검사.
    function guard(input, key, delay, check) {
      var timer = null;
      input.addEventListener('input', function () {
        set(key, false);
        clearTimeout(timer);
        timer = setTimeout(function () { check(input.value.trim()); }, delay);
      });
    }

    guard(document.getElementById('signup-id'), 'id', 350, function (v) {
      if (!v) { clearError('signup-id'); return; }
      if (!ID_RE.test(v)) { setError('signup-id', '영문·숫자 4~50자로 입력해 주세요.'); return; }
      availability('check-login-id', 'loginId', v).then(function (a) {
        if (a === true) { setField('signup-id', '사용 가능한 아이디입니다.', 'ok'); set('id', true); }
        else if (a === false) setError('signup-id', '이미 사용 중인 아이디입니다.');
        else clearError('signup-id');
      });
    });

    guard(document.getElementById('signup-email'), 'email', 350, function (v) {
      if (!v) { clearError('signup-email'); return; }
      if (!EMAIL_RE.test(v)) { setError('signup-email', '올바른 이메일 형식이 아닙니다.'); return; }
      availability('check-email', 'email', v).then(function (a) {
        if (a === true) { setField('signup-email', '사용 가능한 이메일입니다.', 'ok'); set('email', true); }
        else if (a === false) setError('signup-email', '이미 가입된 이메일입니다.');
        else clearError('signup-email');
      });
    });

    guard(document.getElementById('signup-nick'), 'nick', 0, function (v) {
      if (!v) { clearError('signup-nick'); return; }
      if (!NICK_RE.test(v)) { setError('signup-nick', '한글·영문·숫자 2~20자로 입력해 주세요.'); return; }
      setField('signup-nick', '사용 가능한 닉네임입니다.', 'ok'); set('nick', true);
    });

    // 비밀번호 강도 미터: 길이 + 문자 종류로 약함/보통/강함
    var pwMeter = document.getElementById('signup-pw-meter');
    var pwMeterLabel = pwMeter.querySelector('.pw-meter__label');
    var STRENGTH_LABEL = ['비밀번호 안전도', '약함', '보통', '강함'];
    function pwStrength(v) {
      if (v.length < 8) return 0;
      var score = 0;
      if (/[a-z]/.test(v)) score++;
      if (/[A-Z]/.test(v)) score++;
      if (/\d/.test(v)) score++;
      if (/[^A-Za-z0-9]/.test(v)) score++;
      if (v.length >= 12) score++;
      if (score <= 2) return 1;
      if (score <= 3) return 2;
      return 3;
    }
    function renderStrength(v) {
      var lvl = pwStrength(v);
      pwMeter.setAttribute('data-level', String(lvl));
      pwMeterLabel.textContent = STRENGTH_LABEL[lvl];
    }

    function checkPw2() {
      var v = sPw2.value;
      if (!v) { clearError('signup-pw2'); set('pw2', false); return; }
      if (v === sPw.value && valid.pw) { setField('signup-pw2', '비밀번호가 일치합니다.', 'ok'); set('pw2', true); }
      else { setError('signup-pw2', '비밀번호가 일치하지 않습니다.'); set('pw2', false); }
    }
    sPw.addEventListener('input', function () {
      var v = sPw.value;
      if (!v) { clearError('signup-pw'); set('pw', false); }
      else if (v.length < 8) { setError('signup-pw', '비밀번호는 8자 이상이어야 합니다.'); set('pw', false); }
      else if (v.length > 72) { setError('signup-pw', '비밀번호는 72자 이하여야 합니다.'); set('pw', false); }
      else { clearError('signup-pw'); set('pw', true); }
      renderStrength(v);
      checkPw2();
    });
    sPw2.addEventListener('input', checkPw2);

    /* ---------------------------------------------------------------
     * CapsLock 경고 (비밀번호 입력칸, 포커스 중 + 빨간 에러가 없을 때만)
     * ------------------------------------------------------------- */
    function watchCaps(inputId) {
      var input = document.getElementById(inputId);
      var errEl = document.getElementById(inputId + '-error');
      if (!input || !errEl) return;
      function clearWarn() {
        if (errEl.classList.contains('is-warn')) { errEl.textContent = ''; errEl.className = 'field__error'; }
      }
      function update(e) {
        if (input.classList.contains('input--invalid')) return;
        var on = e.getModifierState && e.getModifierState('CapsLock');
        if (on && document.activeElement === input) {
          errEl.textContent = 'Caps Lock이 켜져 있습니다.';
          errEl.className = 'field__error is-warn';
        } else {
          clearWarn();
        }
      }
      input.addEventListener('keydown', update);
      input.addEventListener('keyup', update);
      input.addEventListener('blur', clearWarn);
    }
    watchCaps('login-pw');
    watchCaps('signup-pw');

    /* ---------------------------------------------------------------
     * 제출 중 버튼 로딩 상태
     * ------------------------------------------------------------- */
    function setLoading(btn, loading, labelWhenDone) {
      if (!btn) return;
      if (loading) {
        btn.classList.add('is-loading');
        btn.setAttribute('disabled', 'disabled');
        btn.setAttribute('aria-busy', 'true');
      } else {
        btn.classList.remove('is-loading');
        btn.removeAttribute('disabled');
        btn.removeAttribute('aria-busy');
        if (labelWhenDone) btn.textContent = labelWhenDone;
      }
    }

    /* ---------------------------------------------------------------
     * 로그인 제출
     * ------------------------------------------------------------- */
    panelLogin.addEventListener('submit', function (e) {
      e.preventDefault();
      clearErrors(panelLogin);

      var loginId = document.getElementById('login-id').value.trim();
      var password = document.getElementById('login-pw').value;
      var remember = document.getElementById('login-remember').checked;

      var ok = true;
      if (!loginId) { setError('login-id', '아이디를 입력해 주세요.'); ok = false; }
      if (!password) { setError('login-pw', '비밀번호를 입력해 주세요.'); ok = false; }
      if (!ok) return;

      var btn = panelLogin.querySelector('[data-submit="login"]');
      setLoading(btn, true);

      MemberAPI.login({ loginId: loginId, password: password, remember: remember })
        .then(function () {
          MemberUI.toast('로그인되었습니다.', 'ok');
          location.href = redirectTarget();
        })
        .catch(function (err) {
          setLoading(btn, false, '로그인');
          var code = err && err.code;
          // 아이디 존재 여부를 흘리지 않도록 실패는 하나의 안내로 통일
          if (code === 'M004') { setError('login-pw', '아이디 또는 비밀번호가 올바르지 않습니다.'); document.getElementById('login-pw').focus(); return; }
          // 정지/휴면/탈퇴/과다 시도 등은 토스트로 안내
          MemberUI.handleError(err, '로그인에 실패했습니다.');
        });
    });

    /* ---------------------------------------------------------------
     * 회원가입 제출 (최종 검증 + 서버 중복 확정)
     * ------------------------------------------------------------- */
    panelSignup.addEventListener('submit', function (e) {
      e.preventDefault();

      var loginId = document.getElementById('signup-id').value.trim();
      var email = document.getElementById('signup-email').value.trim();
      var nickname = document.getElementById('signup-nick').value.trim();
      var password = sPw.value;
      var password2 = sPw2.value;

      var ok = true;
      if (!ID_RE.test(loginId)) { setError('signup-id', '아이디는 영문·숫자 4~50자입니다.'); ok = false; }
      if (!EMAIL_RE.test(email)) { setError('signup-email', '올바른 이메일 형식이 아닙니다.'); ok = false; }
      if (!NICK_RE.test(nickname)) { setError('signup-nick', '닉네임은 한글·영문·숫자 2~20자입니다.'); ok = false; }
      if (password.length < 8) { setError('signup-pw', '비밀번호는 8자 이상이어야 합니다.'); ok = false; }
      else if (password.length > 72) { setError('signup-pw', '비밀번호는 72자 이하여야 합니다.'); ok = false; }
      if (password2 !== password) { setError('signup-pw2', '비밀번호가 일치하지 않습니다.'); ok = false; }
      if (!ok) return;

      var btn = panelSignup.querySelector('[data-submit="signup"]');
      setLoading(btn, true);

      MemberAPI.signup({ loginId: loginId, email: email, nickname: nickname, password: password })
        .then(function () {
          setLoading(btn, false, '회원가입');
          MemberUI.toast('회원가입이 완료되었습니다. 로그인해 주세요.', 'ok');
          activateTab('login');
          var idInput = document.getElementById('login-id');
          if (idInput) idInput.value = loginId;
          var pwInput = document.getElementById('login-pw');
          if (pwInput) pwInput.focus();
        })
        .catch(function (err) {
          setLoading(btn, false, '회원가입');
          // 서버가 중복을 확정하면 해당 필드에 인라인 안내 + 포커스.
          if (err && err.code === 'M002') { setError('signup-id', '이미 사용 중인 아이디입니다. 다른 아이디를 입력해 주세요.'); document.getElementById('signup-id').focus(); return; }
          if (err && err.code === 'M003') { setError('signup-email', '이미 가입된 이메일입니다. 다른 이메일을 입력해 주세요.'); document.getElementById('signup-email').focus(); return; }
          MemberUI.handleError(err, '회원가입에 실패했습니다.');
        });
    });

    /* ---------------------------------------------------------------
     * 비밀번호 찾기 (준비 중 안내)
     * ------------------------------------------------------------- */
    var findPwLink = document.querySelector('[data-action="find-pw"]');
    if (findPwLink) {
      findPwLink.addEventListener('click', function (e) {
        e.preventDefault();
        MemberUI.toast('비밀번호 찾기는 준비 중입니다.', 'ok');
      });
    }

    /* ---------------------------------------------------------------
     * 진입 시 초기 탭 결정 (login?tab=signup 지원)
     * ------------------------------------------------------------- */
    var initTab = new URLSearchParams(location.search).get('tab');
    activateTab(initTab === 'signup' ? 'signup' : 'login');
  });
})();
