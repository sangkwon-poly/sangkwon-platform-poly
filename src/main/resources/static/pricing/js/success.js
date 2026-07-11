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

  // 승인 결과가 불확실한 경우(타임아웃 등). 결제됐을 수 있으므로 재시도를 막고 확인을 안내한다.
  function renderPending(msg) {
    root.innerHTML =
      '<div class="co-result-card">' +
        '<span class="co-result-icon co-result-icon-pending" aria-hidden="true">…</span>' +
        '<h1 class="co-result-title">결제 결과를 확인하고 있어요</h1>' +
        '<p class="co-result-sub">' + esc(msg || "중복 결제를 막기 위해 다시 시도하지 마시고, 잠시 후 마이페이지에서 확인해 주세요.") + "</p>" +
        '<div class="co-result-actions">' +
          '<a class="btn btn-primary" href="/member/mypage">마이페이지로</a>' +
          '<a class="btn btn-ghost" href="/">홈으로</a>' +
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
      // 승인 결과 불확실(M018): 실패로 오인시키지 않고 확인 안내를 띄운다
      if (res.body && res.body.code === "M018") {
        renderPending(res.body.message);
        return;
      }
      if (!res.ok || !res.body || !res.body.success) {
        renderFail(res.body && res.body.message);
        return;
      }
      renderSuccess(res.body.data);
    })
    .catch(function () { renderFail("승인 요청에 실패했습니다."); });
})();
