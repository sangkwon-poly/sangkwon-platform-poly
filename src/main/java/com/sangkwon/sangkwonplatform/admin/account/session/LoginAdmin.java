package com.sangkwon.sangkwonplatform.admin.account.session;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인한 관리자 세션({@link AdminSession})을 컨트롤러 파라미터로 주입한다.
 * 세션이 없으면 {@code LoginAdminArgumentResolver}가 401을 던진다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginAdmin {
}
