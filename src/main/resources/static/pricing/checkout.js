(function () {
  "use strict";

  function $(id) { return document.getElementById(id); }

  function param(name) {
    var m = new RegExp("[?&]" + name + "=([^&]*)").exec(location.search);
    if (!m) { return ""; }
    try { return decodeURIComponent(m[1]); } catch (e) { return ""; }
  }

  function showError(msg) {
    var box = $("co-error");
    box.textContent = msg;
    box.hidden = false;
  }

  var cycle = param("cycle") === "MONTHLY" ? "MONTHLY" : "YEARLY";
  $("co-cycle").textContent = cycle === "YEARLY" ? "연간 (2개월 무료)" : "월간";

  // 1) 서버에 주문 생성: 금액은 서버가 확정한다
  fetch("/api/payments/orders", {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({ plan: "PRO", billingCycle: cycle })
  })
    .then(function (r) {
      if (r.status === 401) {
        location.replace("/member/login?redirect=/pricing");
        throw new Error("unauthenticated");
      }
      return r.json();
    })
    .then(function (b) {
      if (!b || !b.success) {
        showError((b && b.message) || "주문을 생성하지 못했습니다.");
        throw new Error("order failed");
      }
      return b.data;
    })
    .then(initWidget)
    .catch(function (e) {
      if (e && e.message === "unauthenticated") { return; }
      if (!$("co-error").textContent) { showError("결제를 준비하지 못했습니다. 잠시 후 다시 시도해 주세요."); }
    });

  // 2) 토스 결제위젯 렌더 후 결제 요청
  function initWidget(order) {
    $("co-amount").textContent = "₩" + Number(order.amount).toLocaleString();

    var tossPayments = TossPayments(order.clientKey);
    var widgets = tossPayments.widgets({ customerKey: TossPayments.ANONYMOUS });

    Promise.resolve()
      .then(function () { return widgets.setAmount({ currency: "KRW", value: order.amount }); })
      .then(function () {
        return Promise.all([
          widgets.renderPaymentMethods({ selector: "#payment-method", variantKey: "DEFAULT" }),
          widgets.renderAgreement({ selector: "#agreement", variantKey: "AGREEMENT" })
        ]);
      })
      .then(function () {
        var btn = $("co-pay-btn");
        btn.disabled = false;
        btn.addEventListener("click", function () {
          btn.disabled = true;
          widgets.requestPayment({
            orderId: order.orderId,
            orderName: order.orderName,
            successUrl: location.origin + "/pricing/success",
            failUrl: location.origin + "/pricing/fail"
          }).catch(function (err) {
            btn.disabled = false;
            // 사용자가 결제창을 닫은 경우는 조용히 두고, 그 외에는 사유를 보여준다
            if (err && err.code !== "USER_CANCEL") {
              showError((err && err.message) || "결제 요청에 실패했습니다.");
            }
          });
        });
      })
      .catch(function () {
        showError("결제위젯을 불러오지 못했습니다. 새로고침 후 다시 시도해 주세요.");
      });
  }
})();
