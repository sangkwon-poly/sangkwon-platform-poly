package com.sangkwon.sangkwonplatform.member.repository;

import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.entity.MemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);

    Optional<Member> findByLoginId(String loginId);

    long countByCreatedAtGreaterThanEqual(LocalDateTime from);

    // 상태 필터 칩 카운트: 상태별 count 4번 대신 group by 한 번으로 집계한다. 없는 상태는 행이 안 나오므로 서비스에서 0으로 채운다.
    @Query("select m.status as status, count(m) as cnt from Member m group by m.status")
    List<MemberStatusCount> countGroupByStatus();

    interface MemberStatusCount {
        MemberStatus getStatus();

        long getCnt();
    }

    // 관리자 회원 목록: 상태 필터(선택)와 아이디/이메일/닉네임 부분검색(선택). kw는 서비스에서 소문자·%감싸기·이스케이프 처리해 넘긴다.
    @Query("""
            select m from Member m
            where (:status is null or m.status = :status)
              and (:kw is null
                   or lower(m.loginId) like :kw escape '\\'
                   or lower(m.email) like :kw escape '\\'
                   or lower(m.nickname) like :kw escape '\\')
            """)
    Page<Member> searchForAdmin(@Param("status") MemberStatus status,
                                @Param("kw") String kw,
                                Pageable pageable);
}
