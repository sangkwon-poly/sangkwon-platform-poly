package com.sangkwon.sangkwonplatform.admin.notice.service;

import com.sangkwon.sangkwonplatform.admin.account.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.account.repository.AdminUserRepository;
import com.sangkwon.sangkwonplatform.admin.notice.dto.request.NoticeCreateRequest;
import com.sangkwon.sangkwonplatform.admin.notice.dto.request.NoticeUpdateRequest;
import com.sangkwon.sangkwonplatform.admin.notice.dto.response.NoticeAdminDetailResponse;
import com.sangkwon.sangkwonplatform.admin.notice.dto.response.NoticeAdminListResponse;
import com.sangkwon.sangkwonplatform.admin.notice.dto.response.NoticeDetailResponse;
import com.sangkwon.sangkwonplatform.admin.notice.dto.response.NoticeSummaryResponse;
import com.sangkwon.sangkwonplatform.admin.notice.entity.Notice;
import com.sangkwon.sangkwonplatform.admin.notice.entity.enums.NoticeStatus;
import com.sangkwon.sangkwonplatform.admin.notice.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final AdminUserRepository adminUserRepository;

    @Transactional(readOnly = true)
    public Page<NoticeAdminListResponse> getAdminList(Pageable pageable) {
        return noticeRepository.findAllByOrderByIsPinnedDescCreatedAtDesc(pageable)
                .map(NoticeAdminListResponse::from);
    }

    @Transactional(readOnly = true)
    public NoticeAdminDetailResponse getAdminDetail(Long noticeId) {
        return NoticeAdminDetailResponse.from(find(noticeId));
    }

    @Transactional(readOnly = true)
    public Page<NoticeSummaryResponse> getPublicList(Pageable pageable) {
        return noticeRepository.findPublicList(NoticeStatus.PUBLISHED, pageable)
                .map(NoticeSummaryResponse::from);
    }

    // 공개 상세: 조회수는 동시 요청에서 증가분이 유실되지 않게 원자적 update로 올린다.
    // 갱신 0건이면 없거나 미발행 공지라 존재 여부를 숨기고 404로 답한다.
    public NoticeDetailResponse getPublicDetail(Long noticeId) {
        if (noticeRepository.increaseViewCnt(noticeId, NoticeStatus.PUBLISHED) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "공지를 찾을 수 없습니다.");
        }
        return NoticeDetailResponse.from(find(noticeId));
    }

    // 새 공지는 임시저장(DRAFT)으로 시작한다. 발행은 상태 변경으로.
    public Long create(Long adminId, NoticeCreateRequest req) {
        AdminUser admin = adminUserRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "관리자를 찾을 수 없습니다."));
        Notice notice = new Notice();
        notice.setAdmin(admin);
        notice.setTitle(req.title());
        notice.setContent(req.content());
        notice.setIsPinned(req.resolvedIspinned());
        notice.setStatus(NoticeStatus.DRAFT);
        return noticeRepository.save(notice).getNoticeId();
    }

    public void update(Long noticeId, NoticeUpdateRequest req) {
        Notice notice = find(noticeId);
        notice.setTitle(req.title());
        notice.setContent(req.content());
        notice.setIsPinned(req.resolvedIspinned());
    }

    public void changeStatus(Long noticeId, NoticeStatus status) {
        find(noticeId).setStatus(status);
    }

    public void delete(Long noticeId) {
        noticeRepository.delete(find(noticeId));
    }

    private Notice find(Long noticeId) {
        return noticeRepository.findById(noticeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공지를 찾을 수 없습니다."));
    }
}
