package com.sangkwon.sangkwonplatform.admin.inquiry.service;

import com.sangkwon.sangkwonplatform.admin.account.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminRole;
import com.sangkwon.sangkwonplatform.admin.account.repository.AdminUserRepository;
import com.sangkwon.sangkwonplatform.admin.inquiry.dto.request.InquiryAnswerRequest;
import com.sangkwon.sangkwonplatform.admin.inquiry.entity.Inquiry;
import com.sangkwon.sangkwonplatform.admin.inquiry.entity.enums.InquiryStatus;
import com.sangkwon.sangkwonplatform.admin.inquiry.repository.InquiryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// InquiryService 단위 테스트. mock으로 답변·닫기 전이와 예외만 검증.
@ExtendWith(MockitoExtension.class)
class InquiryServiceTest {

    @Mock InquiryRepository inquiryRepository;
    @Mock AdminUserRepository adminUserRepository;
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
}
