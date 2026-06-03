package com.paike.scheduler.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登出响应（M-14 收敛：替换 AuthController.logout 原先的 Map 弱类型返回）。
 * 字段与历史 JSON 完全一致：success。前端不读 body，此处仅消除魔法字符串。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogoutResultVo {

    /** 登出是否成功，固定 true。 */
    private Boolean success;
}
