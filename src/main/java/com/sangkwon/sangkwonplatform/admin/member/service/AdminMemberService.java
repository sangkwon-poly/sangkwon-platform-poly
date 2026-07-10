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

    // 상태 필터 칩에 표시할 상태별 회원 수
    @Transactional(readOnly = true)
    public MemberCountsResponse getCounts() {
        return new MemberCountsResponse(
                memberRepository.count(),
                memberRepository.countByStatus(MemberStatus.ACTIVE),
                memberRepository.countByStatus(MemberStatus.DORMANT),
                memberRepository.countByStatus(MemberStatus.BANNED),
                memberRepository.countByStatus(MemberStatus.WITHDRAWN));
    }

    // 회원 상태 변경(정지/휴면/해제/강제탈퇴). 같은 상태로의 무의미한 변경은 막는다.
    public AdminMemberResponse changeStatus(Long memberId, MemberStatus status) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
        if (member.getStatus() == status) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 해당 상태입니다.");
        }
        member.changeStatus(status);
        return AdminMemberResponse.from(member);
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
