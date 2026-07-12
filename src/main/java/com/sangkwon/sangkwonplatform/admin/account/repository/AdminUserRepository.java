package com.sangkwon.sangkwonplatform.admin.account.repository;

import com.sangkwon.sangkwonplatform.admin.account.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminRole;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    Optional<AdminUser> findByLoginId(String loginId);

    // 로그인 처리용. 행 잠금으로 동시 요청을 직렬화해 같은 OTP 코드의 동시 재사용(리플레이)을 막는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AdminUser a where a.loginId = :loginId")
    Optional<AdminUser> findByLoginIdForUpdate(@Param("loginId") String loginId);

    boolean existsByLoginId(String loginId);

    List<AdminUser> findAllByOrderByCreatedAtDesc();

    // 마지막 활성 최고관리자 보호용: 활성 SUPER_ADMIN 수
    long countByRoleAndStatus(AdminRole role, AdminStatus status);

    // 강등·비활성 동시 요청을 직렬화한다. 같은 활성 최고관리자 집합을 잠근 뒤 남은 수를 판단해야
    // 두 트랜잭션이 모두 이전 count를 보고 마지막 두 명을 동시에 없애는 상황을 막을 수 있다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from AdminUser a
            where a.role = :role and a.status = :status
            order by a.adminId
            """)
    List<AdminUser> findByRoleAndStatusForUpdate(@Param("role") AdminRole role,
                                                 @Param("status") AdminStatus status);
}
