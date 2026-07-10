package com.sangkwon.sangkwonplatform.admin.member.service;

import com.sangkwon.sangkwonplatform.admin.member.dto.response.AdminMemberResponse;
import com.sangkwon.sangkwonplatform.admin.member.dto.response.MemberCountsResponse;
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

import java.util.EnumMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminMemberService {

    private final MemberRepository memberRepository;

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
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
        MemberStatus from = member.getStatus();
        if (from == status) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 해당 상태입니다.");
        }
        member.changeStatus(status);
        return new StatusChange(from, AdminMemberResponse.from(member));
    }

    // 상태 변경 결과: 이전 상태(감사용)와 변경된 회원 정보.
    public record StatusChange(MemberStatus from, AdminMemberResponse member) {
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
