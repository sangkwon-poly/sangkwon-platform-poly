package com.sangkwon.sangkwonplatform.admin.notice.service;

import com.sangkwon.sangkwonplatform.admin.account.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminRole;
import com.sangkwon.sangkwonplatform.admin.account.repository.AdminUserRepository;
import com.sangkwon.sangkwonplatform.admin.notice.dto.request.NoticeCreateRequest;
import com.sangkwon.sangkwonplatform.admin.notice.dto.request.NoticeUpdateRequest;
import com.sangkwon.sangkwonplatform.admin.notice.entity.Notice;
import com.sangkwon.sangkwonplatform.admin.notice.entity.enums.IsPinned;
import com.sangkwon.sangkwonplatform.admin.notice.entity.enums.NoticeStatus;
import com.sangkwon.sangkwonplatform.admin.notice.repository.NoticeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// NoticeService 단위 테스트. mock으로 생성·수정·상태변경·삭제 로직만 검증.
@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @Mock NoticeRepository noticeRepository;
    @Mock AdminUserRepository adminUserRepository;
    @InjectMocks NoticeService noticeService;

    private AdminUser admin() {
        return AdminUser.create("op1", "hash", "운영자", AdminRole.OPERATOR);
    }
    private HttpStatus statusOf(Throwable t) {
        return HttpStatus.valueOf(((ResponseStatusException) t).getStatusCode().value());
    }

    @Test
    @DisplayName("생성: 임시저장(DRAFT)으로 시작하고 작성자·상단고정을 담아 저장")
    void create_success() {
        AdminUser admin = admin();
        when(adminUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(noticeRepository.save(any(Notice.class))).thenAnswer(inv -> inv.getArgument(0));

        noticeService.create(1L, new NoticeCreateRequest("점검 안내", "본문", IsPinned.Y));

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeRepository).save(captor.capture());
        Notice saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("점검 안내");
        assertThat(saved.getStatus()).isEqualTo(NoticeStatus.DRAFT);
        assertThat(saved.getIsPinned()).isEqualTo(IsPinned.Y);
        assertThat(saved.getAdmin()).isSameAs(admin);
    }

    @Test
    @DisplayName("생성: 없는 관리자 → 404")
    void create_adminNotFound() {
        when(adminUserRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noticeService.create(9L, new NoticeCreateRequest("t", "c", null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("상태변경: DRAFT → PUBLISHED 반영")
    void changeStatus_publish() {
        Notice n = new Notice();
        n.setStatus(NoticeStatus.DRAFT);
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(n));

        noticeService.changeStatus(1L, NoticeStatus.PUBLISHED);

        assertThat(n.getStatus()).isEqualTo(NoticeStatus.PUBLISHED);
    }

    @Test
    @DisplayName("수정: 없는 공지 → 404")
    void update_notFound() {
        when(noticeRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noticeService.update(1L, new NoticeUpdateRequest("t", "c", null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("삭제: 존재하면 delete 호출")
    void delete_success() {
        Notice n = new Notice();
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(n));

        noticeService.delete(1L);

        verify(noticeRepository).delete(n);
    }
}
