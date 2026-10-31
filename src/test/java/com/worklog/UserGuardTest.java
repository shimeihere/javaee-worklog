package com.worklog;

import com.worklog.common.exception.BusinessException;
import com.worklog.constant.RoleConstant;
import com.worklog.entity.SysUser;
import com.worklog.mapper.SysUserMapper;
import com.worklog.security.LoginUser;
import com.worklog.security.UserContext;
import com.worklog.service.SysUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 管理员保护验收测试。
 *
 * <p>场景说明：只要"不能禁用自己"生效，通过接口几乎不可能把管理员清零；
 * 本测试直接在 Service 层构造"操作人不是被禁用目标"的边界，验证兜底规则本身。</p>
 */
@SpringBootTest
@Transactional
@DisplayName("管理员账号保护")
class UserGuardTest {

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private SysUserMapper sysUserMapper;

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    @DisplayName("禁用系统里最后一个可用管理员 -> 400")
    void cannotDisableLastActiveAdmin() {
        // 影子管理员操作 admin(id=1)，此时库中可用管理员仅剩 admin 一人
        UserContext.set(new LoginUser(999L, "ghost-admin", "影子管理员", RoleConstant.ADMIN, 1L));

        assertThatThrownBy(() -> sysUserService.updateStatus(1L, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少一个可用的管理员");
    }

    @Test
    @DisplayName("还有其他可用管理员时允许禁用（不是最后一个）")
    void canDisableAdminWhenOthersRemain() {
        SysUser temp = new SysUser();
        temp.setUsername("temp_admin");
        temp.setPassword("$2a$10$gGL8go0CloXGDBuPQqwAuOKFTKuNEiB5//7A/oLYJmWZymFnFsqXm");
        temp.setRealName("临时管理员");
        temp.setRole(RoleConstant.ADMIN);
        temp.setDeptId(1L);
        temp.setStatus(0);
        sysUserMapper.insert(temp);

        UserContext.set(new LoginUser(1L, "admin", "张启明", RoleConstant.ADMIN, 1L));
        String message = sysUserService.updateStatus(temp.getId(), 1);

        assertThat(message).contains("账号已禁用");
    }
}
