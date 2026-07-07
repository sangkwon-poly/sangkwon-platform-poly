package com.sangkwon.sangkwonplatform.member.repository;

import com.sangkwon.sangkwonplatform.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);

    Optional<Member> findByLoginId(String loginId);
}
