package com.paike.scheduler.auth.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    @JsonIgnore
    private String token;
    private UserInfoVo userInfo;
}
