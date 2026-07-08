package com.sangkwon.sangkwonplatform.admin.notice.repository;

import com.sangkwon.sangkwonplatform.admin.notice.entity.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    // 관리자 목록: 상단고정(Y) 먼저, 그다음 최신순. is_pinned는 STRING 저장이라 DESC가 Y를 앞세운다.
    Page<Notice> findAllByOrderByIsPinnedDescCreatedAtDesc(Pageable pageable);
}
