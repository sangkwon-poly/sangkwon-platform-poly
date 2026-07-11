package com.sangkwon.sangkwonplatform.member.repository;

import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.entity.MemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    // 휴면 자동 전환: 임계 시각 이전에 마지막 접속(없으면 가입)한 ACTIVE 회원을 DORMANT로 일괄 전환한다.
    // 로그인 차단 로직(MemberService)과 어드민 카운트는 이미 DORMANT를 다루므로 부여 주체만 채운다.
    @Transactional
    @Modifying
    @Query("""
            update Member m set m.status = com.sangkwon.sangkwonplatform.member.entity.MemberStatus.DORMANT
            where m.status = com.sangkwon.sangkwonplatform.member.entity.MemberStatus.ACTIVE
              and coalesce(m.lastLoginAt, m.createdAt) < :cutoff
            """)
    int markInactiveMembersDormant(@Param("cutoff") LocalDateTime cutoff);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);

    Optional<Member> findByLoginId(String loginId);

    long countByCreatedAtGreaterThanEqual(LocalDateTime from);

    // 유효 Pro 구독자 수. Member.isPro()와 같은 기준(만료 시각이 아직 안 지남).
    long countByPlanUntilAfter(LocalDateTime at);

    // 결제 목록의 회원 검색용: 검색어에 걸리는 회원 ID만 뽑아 주문 조회의 IN 조건으로 쓴다.
    // kw 규칙은 searchForAdmin과 동일. Pageable로 상한을 걸어 IN 절이 무한정 커지지 않게 한다.
    @Query("""
            select m.memberId from Member m
            where lower(m.loginId) like :kw escape '\\'
               or lower(m.email) like :kw escape '\\'
               or lower(m.nickname) like :kw escape '\\'
            """)
    List<Long> findIdsByKeyword(@Param("kw") String kw, Pageable pageable);

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
