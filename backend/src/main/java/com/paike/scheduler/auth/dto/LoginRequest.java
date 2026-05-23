package com.paike.scheduler.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名最长 64 字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(max = 128, message = "密码最长 128 字符")
    private String password;
}
