package com.sangkwon.sangkwonplatform.global.common;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // 리플렉션으로 MethodParameter를 얻기 위한 더미 메서드
    @SuppressWarnings("unused")
    void dummy(String value) {
    }

    private MethodArgumentNotValidException validationError(String field, String message) throws Exception {
        MethodParameter parameter = new MethodParameter(getClass().getDeclaredMethod("dummy", String.class), 0);
        BindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", field, message));
        return new MethodArgumentNotValidException(parameter, binding);
    }

    @Test
    void 본문_검증_실패를_ApiResponse_400으로_감싼다() throws Exception {
        ResponseEntity<ApiResponse<Void>> res = handler.handleValidation(validationError("title", "제목은 필수입니다!"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().success()).isFalse();
        assertThat(res.getBody().code()).isEqualTo("BAD_REQUEST");
        assertThat(res.getBody().message()).isEqualTo("제목은 필수입니다!");
    }

    @Test
    void 검증_메시지가_없으면_일반_문구로_대체한다() throws Exception {
        MethodParameter parameter = new MethodParameter(getClass().getDeclaredMethod("dummy", String.class), 0);
        BindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "title", null, false, null, null, null));
        MethodArgumentNotValidException e = new MethodArgumentNotValidException(parameter, binding);

        ResponseEntity<ApiResponse<Void>> res = handler.handleValidation(e);

        assertThat(res.getBody().message()).isEqualTo("입력값이 올바르지 않습니다.");
    }

    @Test
    void 필수_파라미터_누락을_ApiResponse_400으로_감싸고_이름을_담는다() {
        MissingServletRequestParameterException e = new MissingServletRequestParameterException("loginId", "String");

        ResponseEntity<ApiResponse<Void>> res = handler.handleMissingParam(e);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().code()).isEqualTo("BAD_REQUEST");
        assertThat(res.getBody().message()).contains("loginId");
    }
}
