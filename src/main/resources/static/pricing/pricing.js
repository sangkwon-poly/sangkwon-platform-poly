(function () {
  "use strict";

  // 표시 가격. 결제 금액의 기준은 서버(PaymentService)이고 여기는 안내 표시만 담당한다.
  var PRICE = {
    YEARLY: { label: "₩39,000", sub: "연간 결제 시 · 월간 ₩49,000" },
    MONTHLY: { label: "₩49,000", sub: "월간 결제 · 연간 전환 시 2개월 무료" }
  };

  var cycle = "YEARLY";

  var priceEl = document.getElementById("pc-pro-price");
  var subEl = document.getElementById("pc-pro-sub");
  var ctaBtn = document.getElementById("pc-pro-cta");
  var toggleBtns = document.querySelectorAll(".pc-toggle-btn");

  function renderCycle() {
    priceEl.textContent = PRICE[cycle].label;
    subEl.textContent = PRICE[cycle].sub;
    toggleBtns.forEach(function (b) {
      b.classList.toggle("is-active", b.getAttribute("data-cycle") === cycle);
    });
  }

  toggleBtns.forEach(function (b) {
    b.addEventListener("click", function () {
      cycle = b.getAttribute("data-cycle");
      renderCycle();
    });
  });

  // 프로 시작: 로그인 상태면 결제로, 아니면 로그인 후 요금제로 복귀
  ctaBtn.addEventListener("click", function () {
    fetch("/api/members/me", { credentials: "include", headers: { Accept: "application/json" } })
      .then(function (r) { return r.ok ? r.json() : null; })
      .then(function (b) {
        if (b && b.data) {
          location.href = "/pricing/checkout?cycle=" + cycle;
        } else {
          location.href = "/member/login?redirect=/pricing";
        }
      })
      .catch(function () { location.href = "/member/login?redirect=/pricing"; });
  });

  renderCycle();
})();
