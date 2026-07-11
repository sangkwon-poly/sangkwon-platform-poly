package com.sangkwon.sangkwonplatform.member.service;

import com.sangkwon.sangkwonplatform.member.dto.request.PaymentConfirmRequest;
import com.sangkwon.sangkwonplatform.member.dto.request.PaymentOrderCreateRequest;
import com.sangkwon.sangkwonplatform.member.dto.response.PaymentConfirmResponse;
import com.sangkwon.sangkwonplatform.member.dto.response.PaymentOrderResponse;
import com.sangkwon.sangkwonplatform.member.entity.BillingCycle;
import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.entity.PaymentOrder;
import com.sangkwon.sangkwonplatform.member.entity.PaymentStatus;
import com.sangkwon.sangkwonplatform.member.exception.BusinessException;
import com.sangkwon.sangkwonplatform.member.exception.ErrorCode;
import com.sangkwon.sangkwonplatform.member.repository.MemberRepository;
import com.sangkwon.sangkwonplatform.member.repository.PaymentOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 결제 HTTP 호출 이전의 방어 분기(비로그인/미설정/금액 산정/금액 대조/멱등)를 검증한다. 이 경로들은 토스(RestClient)를 건드리지 않는다.
class PaymentServiceTest {

    private final PaymentOrderRepository paymentOrderRepository = mock(PaymentOrderRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final RestClient restClient = mock(RestClient.class);

    private PaymentService service(String clientKey, String secretKey) {
        return new PaymentService(paymentOrderRepository, memberRepository, restClient, clientKey, secretKey);
    }

    private static ErrorCode codeOf(Throwable t) {
        return ((BusinessException) t).getErrorCode();
    }

    @Test
    void 위젯_설정은_공개_clientKey만_돌려준다() {
        assertThat(service("ck", "sk").config().clientKey()).isEqualTo("ck");
    }

    @Test
    void 위젯_설정도_키가_없으면_M016을_던진다() {
        assertThatThrownBy(() -> service("", "").config())
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.PAYMENT_NOT_CONFIGURED));
    }

    @Test
    void 비로그인이면_주문_생성에서_M005를_던진다() {
        assertThatThrownBy(() -> service("ck", "sk").createOrder(null, new PaymentOrderCreateRequest("PRO", "YEARLY")))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.UNAUTHENTICATED));
        verifyNoInteractions(restClient);
    }

    @Test
    void 결제_키가_없으면_M016을_던진다() {
        assertThatThrownBy(() -> service("", "").createOrder(1L, new PaymentOrderCreateRequest("PRO", "YEARLY")))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.PAYMENT_NOT_CONFIGURED));
    }

    @Test
    void 주문_생성은_서버가_주기별_금액을_확정한다() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(Member.create("user", "hash", "user@test.com", "회원")));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentOrderResponse yearly = service("ck", "sk").createOrder(1L, new PaymentOrderCreateRequest("PRO", "YEARLY"));
        PaymentOrderResponse monthly = service("ck", "sk").createOrder(1L, new PaymentOrderCreateRequest("PRO", "MONTHLY"));

        assertThat(yearly.amount()).isEqualTo(240_000L);
        assertThat(yearly.orderName()).isEqualTo("여기콕 Pro 연간");
        assertThat(monthly.amount()).isEqualTo(24_000L);
        assertThat(yearly.clientKey()).isEqualTo("ck");
        verify(paymentOrderRepository, org.mockito.Mockito.times(2)).save(any(PaymentOrder.class));
    }

    @Test
    void 지원하지_않는_플랜이나_주기는_M400을_던진다() {
        assertThatThrownBy(() -> service("ck", "sk").createOrder(1L, new PaymentOrderCreateRequest("FREE", "YEARLY")))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> service("ck", "sk").createOrder(1L, new PaymentOrderCreateRequest("PRO", "WEEKLY")))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    void 이미_Pro인_회원은_주문_생성에서_M017을_던지고_토스를_호출하지_않는다() {
        Member pro = Member.create("user", "hash", "user@test.com", "회원");
        pro.activatePro(LocalDateTime.now().plusMonths(1));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(pro));

        assertThatThrownBy(() -> service("ck", "sk").createOrder(1L, new PaymentOrderCreateRequest("PRO", "YEARLY")))
                .isInstanceOf(BusinessException.class)
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.ALREADY_PRO));
        verifyNoInteractions(restClient);
    }

    @Test
    void 승인_시_주문이_없으면_M013을_던진다() {
        when(paymentOrderRepository.findByOrderIdAndMemberId("no-such", 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service("ck", "sk").confirm(1L, new PaymentConfirmRequest("pk", "no-such", 1000L)))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.PAYMENT_ORDER_NOT_FOUND));
        verifyNoInteractions(restClient);
    }

    @Test
    void 승인_시_금액이_다르면_M014를_던지고_토스를_호출하지_않는다() {
        PaymentOrder order = PaymentOrder.create("o1", 1L, "PRO", BillingCycle.YEARLY, 240_000L, "여기콕 Pro 연간");
        when(paymentOrderRepository.findByOrderIdAndMemberId("o1", 1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service("ck", "sk").confirm(1L, new PaymentConfirmRequest("pk", "o1", 999L)))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH));
        verifyNoInteractions(restClient);
    }

    @Test
    void 이미_승인된_주문은_토스_재호출_없이_멱등하게_응답한다() {
        PaymentOrder order = PaymentOrder.create("o1", 1L, "PRO", BillingCycle.YEARLY, 240_000L, "여기콕 Pro 연간");
        order.paid("pk-1", LocalDateTime.now());
        when(paymentOrderRepository.findByOrderIdAndMemberId("o1", 1L)).thenReturn(Optional.of(order));

        PaymentConfirmResponse res = service("ck", "sk").confirm(1L, new PaymentConfirmRequest("pk-1", "o1", 240_000L));

        assertThat(res.status()).isEqualTo(PaymentStatus.PAID);
        verifyNoInteractions(restClient);
    }

    @Test
    void 승인에_성공하면_주문을_PAID로_바꾸고_회원을_Pro로_전환한다() {
        PaymentOrder order = PaymentOrder.create("o1", 1L, "PRO", BillingCycle.YEARLY, 240_000L, "여기콕 Pro 연간");
        when(paymentOrderRepository.findByOrderIdAndMemberId("o1", 1L)).thenReturn(Optional.of(order));
        Member member = Member.create("user", "hash", "user@test.com", "회원");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        stubTossConfirm("{\"approvedAt\":\"2026-07-11T00:00:00+09:00\"}");

        PaymentConfirmResponse res = service("ck", "sk").confirm(1L, new PaymentConfirmRequest("pk-1", "o1", 240_000L));

        assertThat(res.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(member.isPro()).isTrue();
        // 연간 결제라 만료가 대략 1년 뒤여야 한다(최소 11개월 이후로 넉넉히 확인)
        assertThat(member.getPlanUntil()).isAfter(LocalDateTime.now().plusMonths(11));
        verify(memberRepository).save(member);
    }

    // 토스 승인 응답을 흉내 낸다. 승인 성공 경로(주문 PAID + 구독 활성화)를 검증하기 위한 최소 스텁.
    private void stubTossConfirm(String json) {
        JsonNode node = new ObjectMapper().readTree(json);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec respSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), any(String[].class))).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(respSpec);
        when(respSpec.body(JsonNode.class)).thenReturn(node);
    }
}
