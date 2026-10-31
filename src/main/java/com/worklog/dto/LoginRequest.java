package com.worklog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求体。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "请输入账号")
    @Size(max = 50, message = "账号长度不能超过 50 个字符")
    private String username;

    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 32, message = "密码长度需为 6-32 位")
    private String password;
}
