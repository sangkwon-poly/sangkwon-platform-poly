package com.sangkwon.sangkwonplatform.member.controller;

import com.sangkwon.sangkwonplatform.admin.account.security.ClientIpResolver;
import com.sangkwon.sangkwonplatform.global.security.DbRateLimiter;
import com.sangkwon.sangkwonplatform.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 전역/회원 어드바이스가 같은 검증 예외를 겹쳐 잡을 때 처리 순서를 실제 배선으로 고정한다.
// 회원 엔드포인트의 본문 검증 실패는 MemberExceptionHandler(@Order)가 이겨 M400이어야 하고,
// 회원 어드바이스가 안 잡는 필수 파라미터 누락은 GlobalExceptionHandler로 흘러 BAD_REQUEST여야 한다.
@WebMvcTest(MemberController.class)
@AutoConfigureMockMvc(addFilters = false)
class MemberControllerValidationTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    MemberService memberService;

    @MockitoBean
    DbRateLimiter rateLimiter;

    @MockitoBean
    ClientIpResolver clientIpResolver;

    @Test
    void 본문_검증_실패는_회원_핸들러가_이겨_M400으로_나간다() throws Exception {
        String body = "{\"loginId\":\"\",\"email\":\"tester@example.com\",\"nickname\":\"tester\",\"password\":\"password123\"}";
        mvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("M400"));
    }

    @Test
    void BCrypt_한도를_넘는_UTF8_비밀번호는_인코딩_전에_거절한다() throws Exception {
        String body = """
                {"loginId":"tester01","email":"tester@example.com","nickname":"tester","password":"%s"}
                """.formatted("가".repeat(25));

        mvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("M400"));
        verifyNoInteractions(memberService);
    }

    @Test
    void 필수_파라미터_누락은_전역_핸들러가_BAD_REQUEST_봉투로_잡는다() throws Exception {
        mvc.perform(get("/api/members/check-login-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
