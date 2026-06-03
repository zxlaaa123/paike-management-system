package com.paike.scheduler.service.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 方案未排任务下发项（M-16 第2批：替换 ScheduleUnassignedTask Entity 上的 3 个
 * {@code @TableField(exist = false)} view 字段 courseName/teacherName/className）。
 *
 * 字段（12 个，按 Entity 声明序）：id / planId / semesterId / teachingTaskId / reasonCode /
 * reasonMessage / suggestion / createdAt / deleted / courseName / teacherName / className。
 * 前 9 为持久化列（含 @TableLogic 软删除标记 deleted，当前被 Jackson 序列化为 0、须保留以维持
 * JSON 逐字段不变），后 3 为下发时计算的展示字段。
 *
 * 该 VO 既经 SchedulePlanController.getUnassignedTasks 下发前端，也被 V4ScheduleRiskService
 * 内部读取（detectUnscheduledTasks 组装风险项），故 toVo 须完整拷贝持久化列。
 *
 * 普通 POJO、保留 null 序列化（前端 courseName?/teacherName?/className? 可选），不加 NON_NULL。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleUnassignedTaskVo {

    private Long id;

    private Long planId;

    private Long semesterId;

    private Long teachingTaskId;

    private String reasonCode;

    private String reasonMessage;

    private String suggestion;

    private LocalDateTime createdAt;

    /** @TableLogic 软删除标记，恒 0（已删行被 MyBatis-Plus 过滤）；保留以维持历史 JSON 逐字段不变。 */
    private Integer deleted;

    /** 下发时填充：教学任务对应课程名。 */
    private String courseName;

    /** 下发时填充：教学任务对应教师名。 */
    private String teacherName;

    /** 下发时填充：教学任务对应班级名。 */
    private String className;
}
