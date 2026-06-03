package com.paike.scheduler.service.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 排课调整日志下发项（M-16 第4批：替换 ScheduleAdjustLog Entity 上的 5 个
 * {@code @TableField(exist = false)} view 字段 courseName/teacherName/className/oldClassroomName/newClassroomName）。
 *
 * 字段（24 个，按 Entity 声明序）：前 19 为持久化列
 * （id / planId / scheduleId / semesterId / teachingTaskId / oldClassroomId / oldWeekday / oldStartPeriod /
 * oldEndPeriod / newClassroomId / newWeekday / newStartPeriod / newEndPeriod / beforeScore / afterScore /
 * conflictFlag / adjustReason / createdAt / deleted），含 @TableLogic 软删除标记 deleted（恒 0、当前被 Jackson
 * 序列化，须保留以维持 JSON 逐字段不变）；后 5 为下发时由 fillAdjustRelations 计算的展示字段。
 *
 * 该 VO 由 listAdjustLogs 统一返回，下发到：调整日志列表端点、V4ScheduleSourceService 方案调整日志、
 * V5SimulationPlanDetailVo.adjustLogs 嵌套列表。
 *
 * 普通 POJO、保留 null 序列化，不加 NON_NULL。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleAdjustLogVo {

    private Long id;

    private Long planId;

    private Long scheduleId;

    private Long semesterId;

    private Long teachingTaskId;

    private Long oldClassroomId;

    private Integer oldWeekday;

    private Integer oldStartPeriod;

    private Integer oldEndPeriod;

    private Long newClassroomId;

    private Integer newWeekday;

    private Integer newStartPeriod;

    private Integer newEndPeriod;

    private BigDecimal beforeScore;

    private BigDecimal afterScore;

    private Integer conflictFlag;

    private String adjustReason;

    private LocalDateTime createdAt;

    /** @TableLogic 软删除标记，恒 0（已删行被 MyBatis-Plus 过滤）；保留以维持历史 JSON 逐字段不变。 */
    private Integer deleted;

    /** 下发时填充：课程名称。 */
    private String courseName;

    /** 下发时填充：教师姓名。 */
    private String teacherName;

    /** 下发时填充：班级名称。 */
    private String className;

    /** 下发时填充：调整前教室名称。 */
    private String oldClassroomName;

    /** 下发时填充：调整后教室名称。 */
    private String newClassroomName;
}
