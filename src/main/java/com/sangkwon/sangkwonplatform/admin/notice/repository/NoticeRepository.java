package com.sangkwon.sangkwonplatform.admin.notice.repository;

import com.sangkwon.sangkwonplatform.admin.notice.entity.Notice;
import com.sangkwon.sangkwonplatform.admin.notice.entity.enums.NoticeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    // 관리자 목록: 상단고정(Y) 먼저, 그다음 최신순. is_pinned는 STRING 저장이라 DESC가 Y를 앞세운다.
    Page<Notice> findAllByOrderByIsPinnedDescCreatedAtDesc(Pageable pageable);

    // 공개 목록: 상태 필터(PUBLISHED) + 고정 우선·최신순. 작성자 표기용 admin을 fetch join으로 함께 조회해 N+1을 막는다.
    @Query(value = """
            select n from Notice n
            join fetch n.admin
            where n.status = :status
            order by n.isPinned desc, n.createdAt desc
            """,
            countQuery = "select count(n) from Notice n where n.status = :status")
    Page<Notice> findPublicList(@Param("status") NoticeStatus status, Pageable pageable);

    // 공개 상세 조회수: 동시 조회에서 증가분이 유실되지 않게 DB에서 원자적으로 올린다.
    // 발행 상태 조건을 함께 걸어 갱신 0건이면 없거나 미발행 공지로 판단한다.
    @Modifying(clearAutomatically = true)
    @Query("update Notice n set n.viewCnt = n.viewCnt + 1 where n.noticeId = :noticeId and n.status = :status")
    int increaseViewCnt(@Param("noticeId") Long noticeId, @Param("status") NoticeStatus status);
}
