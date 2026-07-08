package com.sangkwon.sangkwonplatform.admin.member.service;

import com.sangkwon.sangkwonplatform.admin.member.dto.response.AdminMemberResponse;
import com.sangkwon.sangkwonplatform.member.entity.Member;
import com.sangkwon.sangkwonplatform.member.entity.MemberStatus;
import com.sangkwon.sangkwonplatform.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

// AdminMemberService 단위 테스트. mock으로 상태 전이와 검색어 정규화만 검증.
@ExtendWith(MockitoExtension.class)
class AdminMemberServiceTest {

    @Mock MemberRepository memberRepository;
    @InjectMocks AdminMemberService adminMemberService;

    private Member member() {
        return Member.create("hong", "hashed", "hong@test.com", "홍길동");
    }

    private HttpStatus statusOf(Throwable t) {
        return HttpStatus.valueOf(((ResponseStatusException) t).getStatusCode().value());
    }

    @Test
    @DisplayName("상태변경: ACTIVE 회원을 BANNED로 → 상태 반영")
    void changeStatus_ban() {
        Member m = member();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(m));

        AdminMemberResponse res = adminMemberService.changeStatus(1L, MemberStatus.BANNED);

        assertThat(m.getStatus()).isEqualTo(MemberStatus.BANNED);
        assertThat(res.status()).isEqualTo(MemberStatus.BANNED);
    }

    @Test
    @DisplayName("상태변경: 강제탈퇴(WITHDRAWN)면 탈퇴시각도 채움")
    void changeStatus_withdraw() {
        Member m = member();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(m));

        adminMemberService.changeStatus(1L, MemberStatus.WITHDRAWN);

        assertThat(m.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(m.getWithdrawnAt()).isNotNull();
    }

    @Test
    @DisplayName("상태변경: 없는 회원 → 404")
    void changeStatus_notFound() {
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminMemberService.changeStatus(99L, MemberStatus.BANNED))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("상태변경: 같은 상태로 변경 → 400")
    void changeStatus_sameStatus() {
        Member m = member(); // ACTIVE
        when(memberRepository.findById(1L)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> adminMemberService.changeStatus(1L, MemberStatus.ACTIVE))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("목록: 빈 검색어는 null(필터 없음)로 정규화해 조회")
    void getMembers_blankKeyword() {
        Pageable pageable = PageRequest.of(0, 20);
        when(memberRepository.searchForAdmin(isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(member()), pageable, 1));

        Page<AdminMemberResponse> res = adminMemberService.getMembers("  ", null, pageable);

        assertThat(res.getContent()).hasSize(1);
        assertThat(res.getContent().get(0).loginId()).isEqualTo("hong");
    }

    @Test
    @DisplayName("목록: 검색어는 소문자·와일드카드로 정규화해 넘긴다")
    void getMembers_normalizeKeyword() {
        Pageable pageable = PageRequest.of(0, 20);
        when(memberRepository.searchForAdmin(eq(MemberStatus.ACTIVE), eq("%hong%"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(member()), pageable, 1));

        Page<AdminMemberResponse> res = adminMemberService.getMembers("HONG", MemberStatus.ACTIVE, pageable);

        assertThat(res.getContent()).hasSize(1);
    }
}
