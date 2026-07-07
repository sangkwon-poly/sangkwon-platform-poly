package com.sangkwon.sangkwonplatform.admin.adminUser.service;

import com.sangkwon.sangkwonplatform.admin.adminUser.dto.request.*;
import com.sangkwon.sangkwonplatform.admin.adminUser.dto.response.AdminListResponse;
import com.sangkwon.sangkwonplatform.admin.adminUser.dto.response.AdminLoginResponse;
import com.sangkwon.sangkwonplatform.admin.adminUser.entity.AdminUser;
import com.sangkwon.sangkwonplatform.admin.adminUser.entity.enums.AdminStatus;
import com.sangkwon.sangkwonplatform.admin.adminUser.repository.AdminUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    //관리자용 회원가입
    public void join(AdminJoinRequest request) throws IllegalAccessException {
        if (adminUserRepository.existsByLoginId(request.loginId())) {
            throw new IllegalAccessException("이미 사용 중인 로그인 ID입니다!");
        }

        String passwordHash = passwordEncoder.encode(request.password());

        AdminUser adminUser = AdminUser.create(
                request.loginId(),
                passwordHash,
                request.adminName(),
                request.role()
        );

        adminUserRepository.save(adminUser);
    }

    //관리자용 관리자 목록 확인
    @Transactional(readOnly = true)
    public List<AdminListResponse> getAdminList() {
        return adminUserRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(AdminListResponse::from)
                .toList();
    }

    public AdminLoginResponse login(AdminLoginRequest request) {
        AdminUser adminUser = adminUserRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 관리자입니다."));
        if (adminUser.getStatus() != AdminStatus.ACTIVE) {
            throw new IllegalArgumentException("활동 상태의 관리자 계정이 아닙니다!");
        }
        boolean matches = passwordEncoder.matches(
                request.password(),
                adminUser.getPasswordHash()
        );

        if (!matches) {
            adminUser.increaseFailedLoginCnt();
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다!");
        }

        adminUser.loginSuccess();

        return AdminLoginResponse.from(adminUser);

    }


    // 관리자 아이디로 관리자 찾기
    private AdminUser findAdminUser(Long adminId) {
        return adminUserRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("관리자를 찾을 수 없습니다"));
    }

    // 관리자용 회원정보 수정
    public void updateName(Long adminId, AdminNameUpdateRequest request) {
        AdminUser adminUser = findAdminUser(adminId);
        adminUser.updateName(request.adminName());

    }
    public void updatePassword(Long adminId, AdminPasswordUpdateRequest request) {
        AdminUser adminUser = findAdminUser(adminId);

        boolean matches = passwordEncoder.matches(
                request.currentPassword(),
                adminUser.getPasswordHash()
        );
        if (!matches) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다!");
        }
        String passwordHash = passwordEncoder.encode(request.newPassword());
        adminUser.updatePassword(passwordHash);
    }
    public void updateRole(Long adminId, AdminRoleUpdateRequest request){
        AdminUser adminUser = findAdminUser(adminId);
        adminUser.updateRole(request.role());
    }
    public void updateStatus(Long adminId, AdminStatusUpdateRequest request){
        AdminUser adminUser = findAdminUser(adminId);
        adminUser.updateStatus(request.status());
    }
}

