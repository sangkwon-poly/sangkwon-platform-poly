package com.sangkwon.sangkwonplatform.admin.account.entity;

import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminRole;
import com.sangkwon.sangkwonplatform.admin.account.entity.enums.AdminStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminUserTest {

    private AdminUser admin() {
        return AdminUser.create("admin", "hash", "관리자", AdminRole.OPERATOR);
    }

    @Test
    void 잠금_해제하면_로그인_실패_카운트를_초기화한다() {
        AdminUser admin = admin();
        admin.increaseFailedLoginCnt();
        admin.increaseFailedLoginCnt();
        admin.updateStatus(AdminStatus.LOCKED);

        admin.updateStatus(AdminStatus.ACTIVE);

        assertThat(admin.getStatus()).isEqualTo(AdminStatus.ACTIVE);
        assertThat(admin.getFailedLoginCnt()).isZero();
    }

    @Test
    void 잠글_때는_실패_카운트를_유지한다() {
        AdminUser admin = admin();
        admin.increaseFailedLoginCnt();
        admin.increaseFailedLoginCnt();

        admin.updateStatus(AdminStatus.LOCKED);

        assertThat(admin.getStatus()).isEqualTo(AdminStatus.LOCKED);
        assertThat(admin.getFailedLoginCnt()).isEqualTo(2);
    }

    @Test
    void 같은_또는_이전_OTP_스텝은_다시_소비할_수_없다() {
        AdminUser admin = admin();
        assertThat(admin.consumeOtpStep(100)).isTrue();   // 처음 사용
        assertThat(admin.consumeOtpStep(100)).isFalse();  // 같은 스텝 재사용 불가
        assertThat(admin.consumeOtpStep(99)).isFalse();   // 이전 스텝도 불가
        assertThat(admin.consumeOtpStep(101)).isTrue();   // 다음 스텝은 가능
    }
}
