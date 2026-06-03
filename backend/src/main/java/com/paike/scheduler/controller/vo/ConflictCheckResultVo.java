package com.paike.scheduler.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 排课冲突检测响应（M-14 收敛：替换 ScheduleController.checkConflict 原先的 Map 弱类型返回）。
 * 字段与历史 JSON 完全一致：hasConflict / message。
 * 前端 ScheduleView.vue 读取 hasConflict 与 message；无冲突时 message 为空串 ""（非 null）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConflictCheckResultVo {

    /** 是否存在冲突。 */
    private Boolean hasConflict;

    /** 冲突说明；无冲突时为空串 ""。 */
    private String message;
}
