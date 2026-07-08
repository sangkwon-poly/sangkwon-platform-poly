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
}
