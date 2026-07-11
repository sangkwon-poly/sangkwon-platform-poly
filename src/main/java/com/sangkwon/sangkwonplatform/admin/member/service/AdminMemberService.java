package com.sangkwon.sangkwonplatform.admin.member.service;

import com.sangkwon.sangkwonplatform.admin.member.dto.response.AdminMemberResponse;
import com.sangkwon.sangkwonplatform.admin.member.dto.response.MemberCountsResponse;
import com.sangkwon.sangkwonplatform.global.security.MemberSessionRegistry;
import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.entity.MemberStatus;
import com.sangkwon.sangkwonplatform.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminMemberService {

    private final MemberRepository memberRepository;
    private final MemberSessionRegistry memberSessionRegistry;

    @Transactional(readOnly = true)
    public Page<AdminMemberResponse> getMembers(String keyword, MemberStatus status, Pageable pageable) {
        return memberRepository.searchForAdmin(status, normalize(keyword), pageable)
                .map(AdminMemberResponse::from);
    }

    // 상태 필터 칩에 표시할 상태별 회원 수. group by 한 번으로 집계하고, 행이 없는 상태는 0으로 채운다.
    @Transactional(readOnly = true)
    public MemberCountsResponse getCounts() {
        Map<MemberStatus, Long> byStatus = new EnumMap<>(MemberStatus.class);
        for (MemberRepository.MemberStatusCount row : memberRepository.countGroupByStatus()) {
            byStatus.put(row.getStatus(), row.getCnt());
        }
        long active = byStatus.getOrDefault(MemberStatus.ACTIVE, 0L);
        long dormant = byStatus.getOrDefault(MemberStatus.DORMANT, 0L);
        long banned = byStatus.getOrDefault(MemberStatus.BANNED, 0L);
        long withdrawn = byStatus.getOrDefault(MemberStatus.WITHDRAWN, 0L);
        long total = active + dormant + banned + withdrawn;
        return new MemberCountsResponse(total, active, dormant, banned, withdrawn);
    }

    // 회원 상태 변경(정지/휴면/해제/강제탈퇴). 같은 상태로의 무의미한 변경은 막는다.
    // 감사 로그에 전이(from -> to)를 남길 수 있게 이전 상태를 함께 돌려준다.
    public StatusChange changeStatus(Long memberId, MemberStatus status) {
        Member member = find(memberId);
        MemberStatus from = member.getStatus();
        if (from == status) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 해당 상태입니다.");
        }
        member.changeStatus(status);
        // 로그인이 막히는 상태(정지/휴면/탈퇴)로 바뀌면 기존 세션을 즉시 만료시켜 계속 이용하는 것을 막는다
        if (status != MemberStatus.ACTIVE) {
            memberSessionRegistry.revokeAll(memberId);
        }
        return new StatusChange(from, AdminMemberResponse.from(member));
    }

    // 상태 변경 결과: 이전 상태(감사용)와 변경된 회원 정보.
    public record StatusChange(MemberStatus from, AdminMemberResponse member) {
    }

    // 구독 부여·연장(CS 보상, 환불 전 임시 처리). 만료 전이면 남은 기간에 이어붙여 손해가 없게 한다.
    // 결제 승인 경로(PaymentService.activateSubscription)와 같은 규칙.
    public PlanChange extendPlan(Long memberId, int months) {
        Member member = find(memberId);
        // 화면에서도 숨기지만 API 직접 호출까지 막아야 규칙이 성립한다
        if (member.getStatus() == MemberStatus.WITHDRAWN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "탈퇴한 회원에게는 구독을 부여할 수 없습니다.");
        }
        LocalDateTime before = member.getPlanUntil();
        LocalDateTime base = member.isPro() ? member.getPlanUntil() : LocalDateTime.now();
        member.activatePro(base.plusMonths(months));
        return new PlanChange(before, AdminMemberResponse.from(member));
    }

    // 구독 회수. 만료 정보 자체가 없으면 회수할 대상이 없는 요청이다.
    public PlanChange revokePlan(Long memberId) {
        Member member = find(memberId);
        if (member.getPlanUntil() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "회수할 구독이 없습니다.");
        }
        LocalDateTime before = member.getPlanUntil();
        member.revokePro();
        return new PlanChange(before, AdminMemberResponse.from(member));
    }

    // 구독 변경 결과: 이전 만료 시각(감사용)과 변경된 회원 정보.
    public record PlanChange(LocalDateTime beforeUntil, AdminMemberResponse member) {
    }

    private Member find(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
    }

    // 검색어를 소문자화하고 LIKE 와일드카드로 감싼다. 비어 있으면 null(=필터 없음).
    // 입력에 든 %, _ 는 리터럴로 취급하도록 이스케이프한다(쿼리는 escape '\' 사용).
    private String normalize(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.trim().toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
