(function () {
  "use strict";

  // 표시 가격. 결제 금액의 기준은 서버(PaymentService)이고 여기는 안내 표시만 담당한다.
  var PRICE = {
    YEARLY: {
      card: "₩39,000", cardSub: "연간 결제 시 · 월간 ₩49,000",
      amount: 468000, label: "연간",
      calc: "월 ₩39,000 × 12개월", save: "₩120,000 절약"
    },
    MONTHLY: {
      card: "₩49,000", cardSub: "월간 결제 · 연간 전환 시 2개월 무료",
      amount: 49000, label: "월간",
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
   * 결제 모달
   * ------------------------------------------------------------- */
  var backdrop = $("pm-backdrop");
  var payBtn = $("pm-pay");
  var errorEl = $("pm-error");
  var widgets = null;        // 토스 위젯 인스턴스 (첫 열기에 한 번만 렌더)
  var widgetReady = false;
  var paying = false;
  var lastFocus = null;

  function showError(msg) {
    errorEl.textContent = msg;
    errorEl.hidden = false;
  }

  function renderModalCycle() {
    var p = PRICE[cycle];
    $("pm-cycle-label").textContent = p.label;
    $("pm-amount").textContent = won(p.amount);
    $("pm-calc").innerHTML = p.calc + (p.save ? ' <span class="pm-save">' + p.save + "</span>" : "");
    payBtn.textContent = won(p.amount) + " 결제하기";
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
    backdrop.hidden = false;
    document.body.style.overflow = "hidden"; // 뒤 배경 스크롤 잠금
    $("pm-close").focus();
    if (!widgets) { initWidget(); }
  }

  function closeModal() {
    if (paying) { return; } // 결제창 여는 중에는 닫지 않는다
    backdrop.hidden = true;
    document.body.style.overflow = "";
    if (lastFocus && lastFocus.focus) { lastFocus.focus(); }
  }

  // 위젯은 공개 clientKey로 렌더하고, 주문은 결제 버튼을 누를 때 만든다
  function initWidget() {
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

  // 결제하기: 이 시점에 주문을 만들어 서버 확정 금액으로 결제창을 연다
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
   * 열기/닫기 바인딩
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

  renderCardCycle();
})();
