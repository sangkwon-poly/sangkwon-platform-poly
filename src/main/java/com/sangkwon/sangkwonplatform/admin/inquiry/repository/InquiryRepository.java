package com.sangkwon.sangkwonplatform.admin.inquiry.repository;

import com.sangkwon.sangkwonplatform.admin.inquiry.entity.Inquiry;
import com.sangkwon.sangkwonplatform.admin.inquiry.entity.enums.InquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    // 관리자 목록: 상태 필터(선택) + 페이징. 회원·답변자를 fetch join으로 함께 조회해 N+1을 막는다(ToOne이라 페이징 안전).
    @Query(value = """
            select i from Inquiry i
            left join fetch i.member
            left join fetch i.admin
            where (:status is null or i.status = :status)
            """,
            countQuery = "select count(i) from Inquiry i where (:status is null or i.status = :status)")
    Page<Inquiry> searchForAdmin(@Param("status") InquiryStatus status, Pageable pageable);

    // 회원 본인 문의 목록: 최신순. 목록에는 답변자 정보를 노출하지 않아 fetch join이 필요 없다.
    Page<Inquiry> findByMemberMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);

    // 관리자 회원 상세: 특정 회원의 문의 이력(최신순)
    List<Inquiry> findByMemberMemberIdOrderByCreatedAtDesc(Long memberId);

    // 회원의 미확인 답변 수: 답변 완료됐지만 아직 열람하지 않은 문의(새 답변 알림 배지용)
    long countByMemberMemberIdAndStatusAndAnswerReadAtIsNull(Long memberId, InquiryStatus status);

    // 답변 확인 처리(멱등): 아직 미확인인 ANSWERED 답변에만 확인 시각을 남긴다.
    // 엔티티를 수정하지 않는 벌크 업데이트라, 동시 첫 열람에도 @Version 충돌(409) 없이 두 번째는 0행만 갱신한다.
    @Modifying
    @Query("""
            update Inquiry i set i.answerReadAt = :now
            where i.inquiryId = :id
              and i.status = com.sangkwon.sangkwonplatform.admin.inquiry.entity.enums.InquiryStatus.ANSWERED
              and i.answerReadAt is null
            """)
    int markAnswerReadIfUnread(@Param("id") Long id, @Param("now") LocalDateTime now);
}
