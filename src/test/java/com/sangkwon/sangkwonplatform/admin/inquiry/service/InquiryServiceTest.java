package com.sangkwon.sangkwonplatform.admin.inquiry.service;

import com.sangkwon.sangkwonplatform.admin.account.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminRole;
import com.sangkwon.sangkwonplatform.admin.account.repository.AdminUserRepository;
import com.sangkwon.sangkwonplatform.admin.inquiry.dto.request.InquiryAnswerRequest;
import com.sangkwon.sangkwonplatform.admin.inquiry.dto.request.InquiryCreateRequest;
import com.sangkwon.sangkwonplatform.admin.inquiry.dto.response.InquiryUserAnswerResponse;
import com.sangkwon.sangkwonplatform.admin.inquiry.entity.Inquiry;
import com.sangkwon.sangkwonplatform.admin.inquiry.entity.enums.InquiryStatus;
import com.sangkwon.sangkwonplatform.admin.inquiry.repository.InquiryRepository;
import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// InquiryService 단위 테스트. mock으로 답변·닫기 전이와 회원 등록·조회 예외만 검증.
@ExtendWith(MockitoExtension.class)
class InquiryServiceTest {

    @Mock InquiryRepository inquiryRepository;
    @Mock AdminUserRepository adminUserRepository;
    @Mock MemberRepository memberRepository;
    @InjectMocks InquiryService inquiryService;

    private AdminUser admin() {
        return AdminUser.create("op1", "hash", "운영자", AdminRole.OPERATOR);
    }
    private Inquiry openInquiry() {
        Inquiry i = new Inquiry();
        i.setTitle("문의합니다");
        i.setContent("본문");
        return i; // status 기본 OPEN
    }
    private HttpStatus statusOf(Throwable t) {
        return HttpStatus.valueOf(((ResponseStatusException) t).getStatusCode().value());
    }

    @Test
    @DisplayName("답변: 답변·답변자·시각을 채우고 상태를 ANSWERED로")
    void answer_success() {
        Inquiry inquiry = openInquiry();
        AdminUser admin = admin();
        when(inquiryRepository.findById(1L)).thenReturn(Optional.of(inquiry));
        when(adminUserRepository.findById(7L)).thenReturn(Optional.of(admin));

        inquiryService.answer(7L, 1L, new InquiryAnswerRequest("답변드립니다"));

        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.ANSWERED);
        assertThat(inquiry.getAnswer()).isEqualTo("답변드립니다");
        assertThat(inquiry.getAdmin()).isSameAs(admin);
        assertThat(inquiry.getAnsweredAt()).isNotNull();
    }

    @Test
    @DisplayName("답변: 없는 문의 → 404")
    void answer_inquiryNotFound() {
        when(inquiryRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inquiryService.answer(7L, 9L, new InquiryAnswerRequest("a")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("답변: 없는 관리자 → 404")
    void answer_adminNotFound() {
        when(inquiryRepository.findById(1L)).thenReturn(Optional.of(openInquiry()));
        when(adminUserRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inquiryService.answer(9L, 1L, new InquiryAnswerRequest("a")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("닫기: 상태를 CLOSED로")
    void close_success() {
        Inquiry inquiry = openInquiry();
        when(inquiryRepository.findById(1L)).thenReturn(Optional.of(inquiry));

        inquiryService.close(1L);

        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.CLOSED);
    }

    @Test
    @DisplayName("회원 등록: 제목·내용·작성 회원을 담아 OPEN 상태로 저장")
    void create_success() {
        Member member = member(5L);
        when(memberRepository.findById(5L)).thenReturn(Optional.of(member));
        when(inquiryRepository.save(any(Inquiry.class))).thenAnswer(inv -> inv.getArgument(0));

        inquiryService.create(5L, new InquiryCreateRequest("문의합니다", "본문"));

        ArgumentCaptor<Inquiry> captor = ArgumentCaptor.forClass(Inquiry.class);
        verify(inquiryRepository).save(captor.capture());
        Inquiry saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("문의합니다");
        assertThat(saved.getStatus()).isEqualTo(InquiryStatus.OPEN);
        assertThat(saved.getMember()).isSameAs(member);
    }

    @Test
    @DisplayName("회원 등록: 비로그인(memberId null) → 401")
    void create_unauthenticated() {
        assertThatThrownBy(() -> inquiryService.create(null, new InquiryCreateRequest("t", "c")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("내 상세: 본인 문의면 답변 포함 반환")
    void getMyDetail_success() {
        Inquiry inquiry = openInquiry();
        inquiry.setMember(member(5L));
        inquiry.answerBy(admin(), "답변드립니다");
        when(inquiryRepository.findById(1L)).thenReturn(Optional.of(inquiry));

        InquiryUserAnswerResponse res = inquiryService.getMyDetail(5L, 1L);

        assertThat(res.title()).isEqualTo("문의합니다");
        assertThat(res.answer()).isEqualTo("답변드립니다");
        assertThat(res.status()).isEqualTo(InquiryStatus.ANSWERED);
    }

    @Test
    @DisplayName("내 상세: 다른 회원의 문의 → 404")
    void getMyDetail_notOwner() {
        Inquiry inquiry = openInquiry();
        inquiry.setMember(member(7L));
        when(inquiryRepository.findById(1L)).thenReturn(Optional.of(inquiry));

        assertThatThrownBy(() -> inquiryService.getMyDetail(5L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private Member member(Long id) {
        Member m = Member.create("user1", "hash", "user1@test.com", "회원");
        ReflectionTestUtils.setField(m, "memberId", id);
        return m;
    }
}
