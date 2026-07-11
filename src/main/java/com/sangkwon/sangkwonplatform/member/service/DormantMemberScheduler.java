package com.sangkwon.sangkwonplatform.member.service;

import com.sangkwon.sangkwonplatform.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

// 회원 휴면(DORMANT) 자동 전환. DORMANT 상태·로그인 차단·어드민 카운트는 있는데 이 상태를 부여하는 주체가
// 없어 죽은 기능이었다. 장기 미접속 회원을 주기적으로 휴면 처리해 되살린다. 재활성화는 관리자 상태 변경(ACTIVE).
@Slf4j
@Component
@RequiredArgsConstructor
public class DormantMemberScheduler {

    private final MemberRepository memberRepository;

    // 마지막 접속(없으면 가입) 후 이 일수를 넘기면 휴면 처리한다. 운영 정책이라 설정값으로 둔다.
    @Value("${member.dormant.inactive-days:365}")
    private long inactiveDays;

    // 매일 새벽 4시 30분(KST). 정리는 베스트에포트라 실패해도 다음 주기에 다시 시도한다.
    @Scheduled(cron = "0 30 4 * * *")
    public void transitionInactiveToDormant() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(inactiveDays);
            int count = memberRepository.markInactiveMembersDormant(cutoff);
            if (count > 0) {
                log.info("장기 미접속 회원 {}명을 휴면(DORMANT)으로 전환(임계 {}일)", count, inactiveDays);
            }
        } catch (Exception e) {
            log.warn("휴면 전환 실패(다음 주기에 재시도): {}", e.getMessage());
        }
    }
}
