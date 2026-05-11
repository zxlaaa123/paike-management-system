package com.paike.scheduler.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserInfoVo {
    private Long id;
    private String username;
    private String realName;
}
