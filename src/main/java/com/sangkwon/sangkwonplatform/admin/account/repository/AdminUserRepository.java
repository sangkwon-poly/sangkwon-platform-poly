package com.sangkwon.sangkwonplatform.admin.account.repository;

import com.sangkwon.sangkwonplatform.admin.account.entity.AdminUser;
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
}
