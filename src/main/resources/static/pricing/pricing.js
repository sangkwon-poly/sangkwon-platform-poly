(function () {
  "use strict";

  // 표시 가격. 결제 금액의 기준은 서버(PaymentService)이고 여기는 안내 표시만 담당한다.
  var PRICE = {
    YEARLY: {
      card: "₩20,000", cardSub: "연간 결제 시 · 월간 ₩24,000",
      amount: 240000, label: "연간",
      calc: "월 ₩20,000 × 12개월", save: "₩48,000 절약"
    },
    MONTHLY: {
      card: "₩24,000", cardSub: "월간 결제 · 연간 전환 시 2개월 무료",
      amount: 24000, label: "월간",
      calc: "매월 결제 · 언제든 해지", save: ""
    }
  };

  function $(id) { return document.getElementById(id); }
  function won(n) { return "₩" + Number(n).toLocaleString(); }

  var cycle = "YEARLY";

  /* ---------------------------------------------------------------
   * 요금제 카드: 월간/연간 토글
   * ------------------------------------------------------------- */
  var pageToggles = document.querySelectorAll(".pc-toggle-btn");

  function renderCardCycle() {
    $("pc-pro-price").textContent = PRICE[cycle].card;
    $("pc-pro-sub").textContent = PRICE[cycle].cardSub;
    pageToggles.forEach(function (b) {
      b.classList.toggle("is-active", b.getAttribute("data-cycle") === cycle);
    });
  }

  pageToggles.forEach(function (b) {
    b.addEventListener("click", function () {
      cycle = b.getAttribute("data-cycle");
      renderCardCycle();
    });
  });

  /* ---------------------------------------------------------------
   * 결제 모달: 1단계 플랜 확인 → 2단계 결제 수단
   * ------------------------------------------------------------- */
  var backdrop = $("pm-backdrop");
  var nextBtn = $("pm-next");
  var payBtn = $("pm-pay");
  var errorEl = $("pm-error");
  var widgets = null;        // 토스 위젯 인스턴스 (2단계 첫 진입에 한 번만 렌더)
  var widgetReady = false;
  var paying = false;
  var lastFocus = null;

  function showError(msg) {
    errorEl.textContent = msg;
    errorEl.hidden = false;
  }

  function showStep(n) {
    $("pm-step-plan").hidden = n !== 1;
    $("pm-step-pay").hidden = n !== 2;
  }

  function renderModalCycle() {
    var p = PRICE[cycle];
    $("pm-cycle-label").textContent = p.label;
    $("pm-amount").textContent = won(p.amount);
    $("pm-calc").innerHTML = p.calc + (p.save ? ' <span class="pm-save">' + p.save + "</span>" : "");
    nextBtn.textContent = won(p.amount) + " 결제하기";
    $("pm-pay-cycle").textContent = p.label;
    $("pm-pay-amount").textContent = won(p.amount);
    if (!paying) { payBtn.textContent = won(p.amount) + " 결제하기"; }
    document.querySelectorAll(".pm-cycle-btn").forEach(function (b) {
      b.classList.toggle("is-active", b.getAttribute("data-cycle") === cycle);
    });
    if (widgets && widgetReady) {
      widgets.setAmount({ currency: "KRW", value: p.amount }).catch(function () {});
    }
  }

  function openModal() {
    lastFocus = document.activeElement;
    errorEl.hidden = true;
    renderModalCycle();
    showStep(1); // 항상 플랜 확인부터. 결제 수단은 결제하기를 눌러야 뜬다
    backdrop.hidden = false;
    document.body.style.overflow = "hidden"; // 뒤 배경 스크롤 잠금
    $("pm-close").focus();
  }

  function closeModal() {
    backdrop.hidden = true;
    document.body.style.overflow = "";
    paying = false;
    renderModalCycle(); // 버튼 라벨 복원
    if (lastFocus && lastFocus.focus) { lastFocus.focus(); }
  }

  // 2단계 진입: 위젯은 여기서 처음 렌더하고, 이후엔 금액만 갱신한다
  function enterPayStep() {
    errorEl.hidden = true;
    showStep(2);
    if (widgets) {
      renderModalCycle();
      return;
    }
    payBtn.disabled = true;
    fetch("/api/payments/config", { headers: { Accept: "application/json" } })
      .then(function (r) { return r.json(); })
      .then(function (b) {
        if (!b || !b.success) { throw new Error((b && b.message) || "설정 없음"); }
        var tossPayments = TossPayments(b.data.clientKey);
        widgets = tossPayments.widgets({ customerKey: TossPayments.ANONYMOUS });
        return widgets.setAmount({ currency: "KRW", value: PRICE[cycle].amount })
          .then(function () {
            return Promise.all([
              widgets.renderPaymentMethods({ selector: "#payment-method", variantKey: "DEFAULT" }),
              widgets.renderAgreement({ selector: "#agreement", variantKey: "AGREEMENT" })
            ]);
          });
      })
      .then(function () {
        widgetReady = true;
        $("pm-loading").hidden = true;
        payBtn.disabled = false;
      })
      .catch(function (e) {
        widgets = null;
        $("pm-loading").hidden = true;
        showError((e && e.message) || "결제위젯을 불러오지 못했습니다. 새로고침 후 다시 시도해 주세요.");
      });
  }

  // 최종 결제: 이 시점에 주문을 만들어 서버 확정 금액으로 결제창을 연다
  payBtn.addEventListener("click", function () {
    if (!widgetReady || paying) { return; }
    paying = true;
    errorEl.hidden = true;
    payBtn.disabled = true;
    payBtn.textContent = "결제창 여는 중…";

    fetch("/api/payments/orders", {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ plan: "PRO", billingCycle: cycle })
    })
      .then(function (r) {
        if (r.status === 401) {
          location.href = "/member/login?redirect=/pricing";
          throw new Error("unauthenticated");
        }
        return r.json();
      })
      .then(function (b) {
        if (!b || !b.success) { throw new Error((b && b.message) || "주문을 생성하지 못했습니다."); }
        return widgets.requestPayment({
          orderId: b.data.orderId,
          orderName: b.data.orderName,
          successUrl: location.origin + "/pricing/success",
          failUrl: location.origin + "/pricing/fail"
        });
      })
      .catch(function (err) {
        paying = false;
        payBtn.disabled = false;
        renderModalCycle(); // 버튼 라벨 복원
        if (err && err.message === "unauthenticated") { return; }
        // 사용자가 결제창을 닫은 경우는 조용히 두고, 그 외에는 사유를 보여준다
        if (err && err.code !== "USER_CANCEL") {
          showError((err && err.message) || "결제 요청에 실패했습니다.");
        }
      });
  });

  /* ---------------------------------------------------------------
   * 열기/닫기/단계 이동 바인딩
   * ------------------------------------------------------------- */
  $("pc-pro-cta").addEventListener("click", function () {
    fetch("/api/members/me", { credentials: "include", headers: { Accept: "application/json" } })
      .then(function (r) { return r.ok ? r.json() : null; })
      .then(function (b) {
        if (b && b.data) { openModal(); }
        else { location.href = "/member/login?redirect=/pricing"; }
      })
      .catch(function () { location.href = "/member/login?redirect=/pricing"; });
  });

  $("pm-next").addEventListener("click", enterPayStep);
  $("pm-back").addEventListener("click", function () {
    errorEl.hidden = true;
    showStep(1);
  });

  document.querySelectorAll(".pm-cycle-btn").forEach(function (b) {
    b.addEventListener("click", function () {
      cycle = b.getAttribute("data-cycle");
      renderCardCycle();
      renderModalCycle();
    });
  });

  $("pm-close").addEventListener("click", closeModal);
  backdrop.addEventListener("click", function (e) {
    if (e.target === backdrop) { closeModal(); }
  });
  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape" && !backdrop.hidden) { closeModal(); }
  });

  // 결제 후 뒤로가기로 돌아오면 브라우저가 페이지를 캐시(bfcache)에서 복원해
  // 모달이 열린 채 위젯만 죽은 상태가 된다. 그때는 모달을 닫고 상태를 초기화한다.
  window.addEventListener("pageshow", function (e) {
    if (e.persisted && !backdrop.hidden) {
      widgets = null;
      widgetReady = false;
      $("pm-loading").hidden = false;
      $("payment-method").innerHTML = "";
      $("agreement").innerHTML = "";
      closeModal();
    }
  });

  // 이미 Pro인 회원에게는 신규 결제를 막고 현재 요금제임을 표시한다(연장은 어드민 부여로만 처리).
  fetch("/api/members/me", { credentials: "include", headers: { Accept: "application/json" } })
    .then(function (r) { return r.ok ? r.json() : null; })
    .then(function (b) {
      var me = b && b.data;
      if (!me || !me.pro) { return; }
      var cta = $("pc-pro-cta");
      cta.textContent = "현재 요금제";
      cta.disabled = true;
      cta.classList.add("pc-cta-current");
    })
    .catch(function () {});

  renderCardCycle();
})();
