/* =====================================================================
 * auth.js — 로그인/회원가입 페이지 동작
 * 서울공화국 상권분석 플랫폼 · 김민혁(member)
 *
 * 담당: 탭 전환, 클라이언트 검증, 비밀번호 표시 토글, 제출(MemberAPI 호출).
 * 이 페이지는 인증 전 화면이라 앱 헤더를 렌더하지 않는다.
 * ===================================================================== */
(function () {
  'use strict';

  document.addEventListener('DOMContentLoaded', function () {
    var tabs = Array.prototype.slice.call(document.querySelectorAll('.tab[data-tab]'));
    var panelLogin = document.getElementById('panel-login');
    var panelSignup = document.getElementById('panel-signup');

    /* ---------------------------------------------------------------
     * 로그인 후 복귀 경로 (login.html?redirect=... 지원)
     * 없으면 찜 목록으로 이동.
     * ------------------------------------------------------------- */
    function redirectTarget() {
      var params = new URLSearchParams(location.search);
      var back = params.get('redirect');
      if (back) {
        // 오픈 리다이렉트 방지: 화이트리스트 방식.
        // 선행 슬래시를 제거한 뒤, 같은 디렉터리의 영문/숫자/하이픈 .html
        // 파일명(+선택적 ?query/#hash)만 허용한다. 콜론(:)이 있으면 거부.
        var safe = back.replace(/^\/+/, '');
        if (safe.indexOf(':') === -1 && /^[\w-]+\.html([?#].*)?$/.test(safe)) {
          return safe;
        }
      }
      return 'favorites.html';
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
      // 전환 시 이전 에러/유효성 표시 정리.
      clearErrors(isLogin ? panelLogin : panelSignup);
      // 첫 입력에 포커스.
      var focusTarget = (isLogin ? panelLogin : panelSignup).querySelector('.input');
      if (focusTarget) focusTarget.focus();
    }

    tabs.forEach(function (t) {
      t.addEventListener('click', function () {
        activateTab(t.getAttribute('data-tab'));
      });
    });

    /* ---------------------------------------------------------------
     * 비밀번호 표시 토글
     * ------------------------------------------------------------- */
    document.querySelectorAll('[data-toggle-pw]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var input = document.getElementById(btn.getAttribute('data-toggle-pw'));
        if (!input) return;
        var show = input.type === 'password';
        input.type = show ? 'text' : 'password';
        btn.textContent = show ? '숨김' : '표시';
        btn.setAttribute('aria-pressed', show ? 'true' : 'false');
        btn.setAttribute('aria-label', show ? '비밀번호 숨김' : '비밀번호 표시');
      });
    });

    /* ---------------------------------------------------------------
     * 검증 헬퍼 (에러는 .field__error 에 표시 + aria-invalid)
     * ------------------------------------------------------------- */
    function setError(inputId, message) {
      var input = document.getElementById(inputId);
      var errEl = document.getElementById(inputId + '-error');
      if (input) {
        input.classList.add('input--invalid');
        input.setAttribute('aria-invalid', 'true');
      }
      if (errEl) errEl.textContent = message || '';
    }

    function clearError(inputId) {
      var input = document.getElementById(inputId);
      var errEl = document.getElementById(inputId + '-error');
      if (input) {
        input.classList.remove('input--invalid');
        input.removeAttribute('aria-invalid');
      }
      if (errEl) errEl.textContent = '';
    }

    function clearErrors(form) {
      form.querySelectorAll('.input').forEach(function (i) {
        i.classList.remove('input--invalid');
        i.removeAttribute('aria-invalid');
      });
      form.querySelectorAll('.field__error').forEach(function (e) {
        e.textContent = '';
      });
    }

    // 입력을 고치면 해당 필드 에러를 즉시 해제.
    [panelLogin, panelSignup].forEach(function (form) {
      form.querySelectorAll('.input').forEach(function (input) {
        input.addEventListener('input', function () {
          if (input.id) clearError(input.id);
        });
      });
    });

    var EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

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
      if (!loginId) { setError('login-id', '아이디 또는 이메일을 입력해 주세요.'); ok = false; }
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
          // 자격 증명 오류는 필드 근처에도 표시.
          if (err && (err.code === 'M004')) {
            setError('login-pw', '아이디 또는 비밀번호가 올바르지 않습니다.');
          }
          MemberUI.handleError(err, '로그인에 실패했습니다.');
        });
    });

    /* ---------------------------------------------------------------
     * 회원가입 제출
     * ------------------------------------------------------------- */
    panelSignup.addEventListener('submit', function (e) {
      e.preventDefault();
      clearErrors(panelSignup);

      var loginId = document.getElementById('signup-id').value.trim();
      var email = document.getElementById('signup-email').value.trim();
      var nickname = document.getElementById('signup-nick').value.trim();
      var password = document.getElementById('signup-pw').value;
      var password2 = document.getElementById('signup-pw2').value;

      var ok = true;
      if (!loginId) {
        setError('signup-id', '로그인 아이디를 입력해 주세요.'); ok = false;
      } else if (loginId.length < 4) {
        setError('signup-id', '아이디는 4자 이상이어야 합니다.'); ok = false;
      }

      if (!email) {
        setError('signup-email', '이메일을 입력해 주세요.'); ok = false;
      } else if (!EMAIL_RE.test(email)) {
        setError('signup-email', '올바른 이메일 형식이 아닙니다.'); ok = false;
      }

      if (!nickname) {
        setError('signup-nick', '닉네임을 입력해 주세요.'); ok = false;
      }

      if (!password) {
        setError('signup-pw', '비밀번호를 입력해 주세요.'); ok = false;
      } else if (password.length < 8) {
        setError('signup-pw', '비밀번호는 8자 이상이어야 합니다.'); ok = false;
      }

      if (!password2) {
        setError('signup-pw2', '비밀번호 확인을 입력해 주세요.'); ok = false;
      } else if (password && password2 !== password) {
        setError('signup-pw2', '비밀번호가 일치하지 않습니다.'); ok = false;
      }

      if (!ok) return;

      var btn = panelSignup.querySelector('[data-submit="signup"]');
      setLoading(btn, true);

      MemberAPI.signup({
        loginId: loginId,
        email: email,
        nickname: nickname,
        password: password,
      })
        .then(function () {
          setLoading(btn, false, '회원가입');
          MemberUI.toast('회원가입이 완료되었습니다. 로그인해 주세요.', 'ok');
          // 로그인 탭으로 전환하고 아이디를 채워 준다.
          activateTab('login');
          var idInput = document.getElementById('login-id');
          if (idInput) { idInput.value = loginId; }
          var pwInput = document.getElementById('login-pw');
          if (pwInput) { pwInput.focus(); }
        })
        .catch(function (err) {
          setLoading(btn, false, '회원가입');
          // 중복 아이디/이메일은 해당 필드에 표시.
          if (err && err.code === 'M002') {
            setError('signup-id', '이미 사용 중인 로그인 아이디입니다.');
          } else if (err && err.code === 'M003') {
            setError('signup-email', '이미 사용 중인 이메일입니다.');
          }
          MemberUI.handleError(err, '회원가입에 실패했습니다.');
        });
    });

    /* ---------------------------------------------------------------
     * 비밀번호 찾기 (동작 준비중 안내)
     * ------------------------------------------------------------- */
    var findPwLink = document.querySelector('[data-action="find-pw"]');
    if (findPwLink) {
      findPwLink.addEventListener('click', function (e) {
        e.preventDefault();
        MemberUI.toast('비밀번호 찾기는 준비 중입니다.', 'ok');
      });
    }

    /* ---------------------------------------------------------------
     * 진입 시 초기 탭 결정 (login.html?tab=signup 지원)
     * ------------------------------------------------------------- */
    var initTab = new URLSearchParams(location.search).get('tab');
    activateTab(initTab === 'signup' ? 'signup' : 'login');
  });
})();
