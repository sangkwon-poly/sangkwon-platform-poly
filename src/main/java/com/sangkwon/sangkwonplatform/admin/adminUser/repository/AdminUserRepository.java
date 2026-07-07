package com.sangkwon.sangkwonplatform.admin.adminUser.repository;

import com.sangkwon.sangkwonplatform.admin.adminUser.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    Optional<AdminUser> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    List<AdminUser> findAllByOrderByCreatedAtDesc();
}
