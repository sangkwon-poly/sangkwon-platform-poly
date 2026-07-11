(function () {
  "use strict";

  var root = document.getElementById("co-result");

  function esc(s) {
    var d = document.createElement("div");
    d.textContent = (s == null) ? "" : String(s);
    return d.innerHTML.replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  }

  function param(name) {
    var m = new RegExp("[?&]" + name + "=([^&]*)").exec(location.search);
    if (!m) { return ""; }
    try { return decodeURIComponent(m[1]); } catch (e) { return ""; }
  }

  function fmtDateTime(iso) {
    if (!iso) { return "-"; }
    var d = new Date(iso);
    if (isNaN(d.getTime())) { return "-"; }
    return d.getFullYear() + "." + String(d.getMonth() + 1).padStart(2, "0")
      + "." + String(d.getDate()).padStart(2, "0")
      + " " + String(d.getHours()).padStart(2, "0") + ":" + String(d.getMinutes()).padStart(2, "0");
  }

  function renderFail(msg) {
    root.innerHTML =
      '<div class="co-result-card">' +
        '<span class="co-result-icon co-result-icon-fail" aria-hidden="true">!</span>' +
        '<h1 class="co-result-title">결제를 완료하지 못했어요</h1>' +
        '<p class="co-result-sub">' + esc(msg || "잠시 후 다시 시도해 주세요.") + "</p>" +
        '<div class="co-result-actions">' +
          '<a class="btn btn-primary" href="/pricing">요금제로 돌아가기</a>' +
        "</div>" +
      "</div>";
  }

  function renderSuccess(p) {
    var cycleLabel = p.billingCycle === "YEARLY" ? "연간" : "월간";
    root.innerHTML =
      '<div class="co-result-card">' +
        '<span class="co-result-icon" aria-hidden="true">✓</span>' +
        '<h1 class="co-result-title">결제가 완료되었습니다</h1>' +
        '<p class="co-result-sub">여기콕 Pro를 시작할 준비가 끝났어요.</p>' +
        '<dl class="co-rows co-result-rows">' +
          '<div class="co-row"><dt>주문명</dt><dd>' + esc(p.orderName) + "</dd></div>" +
          '<div class="co-row"><dt>결제 주기</dt><dd>' + cycleLabel + "</dd></div>" +
          '<div class="co-row"><dt>결제 금액</dt><dd>₩' + Number(p.amount).toLocaleString() + "</dd></div>" +
          '<div class="co-row"><dt>승인 시각</dt><dd>' + fmtDateTime(p.approvedAt) + "</dd></div>" +
        "</dl>" +
        '<div class="co-result-actions">' +
          '<a class="btn btn-primary" href="/map">지도 보러가기</a>' +
          '<a class="btn btn-ghost" href="/">홈으로</a>' +
        "</div>" +
      "</div>";
  }

  var paymentKey = param("paymentKey");
  var orderId = param("orderId");
  var amount = Number(param("amount"));

  if (!paymentKey || !orderId || !amount) { renderFail("결제 정보가 올바르지 않습니다."); return; }

  // 서버 승인: 주문 금액 대조 후 토스 승인 API를 호출한다
  fetch("/api/payments/confirm", {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({ paymentKey: paymentKey, orderId: orderId, amount: amount })
  })
    .then(function (r) { return r.json().then(function (b) { return { ok: r.ok, body: b }; }); })
    .then(function (res) {
      if (!res.ok || !res.body || !res.body.success) {
        renderFail(res.body && res.body.message);
        return;
      }
      renderSuccess(res.body.data);
    })
    .catch(function () { renderFail("승인 요청에 실패했습니다."); });
})();
