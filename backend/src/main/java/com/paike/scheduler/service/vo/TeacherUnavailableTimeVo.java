package com.paike.scheduler.service.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 教师禁排时间下发项（M-16 第3批：替换 TeacherUnavailableTime Entity 上的 5 个
 * {@code @TableField(exist = false)} view 字段 teacherName/department/timeSlotName/dayOfWeek/periodNo）。
 *
 * 字段（14 个，按 Entity 声明序）：id / teacherId / timeSlotId / reason / status / remark /
 * deleted / createTime / updateTime / teacherName / department / timeSlotName / dayOfWeek / periodNo。
 * 前 9 为持久化列（含 @TableLogic 软删除标记 deleted，恒 0、当前被 Jackson 序列化，须保留以维持
 * JSON 逐字段不变），后 5 为下发时计算的展示字段。
 *
 * 该 VO 由 list/create/update 三个端点统一返回（三者都经 fillRelationFields 填充 view 字段）。
 *
 * 普通 POJO、保留 null 序列化（前端 department?/dayOfWeek?/periodNo? 可选），不加 NON_NULL。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeacherUnavailableTimeVo {

    private Long id;

    private Long teacherId;

    private Long timeSlotId;

    private String reason;

    private Integer status;

    private String remark;

    /** @TableLogic 软删除标记，恒 0（已删行被 MyBatis-Plus 过滤）；保留以维持历史 JSON 逐字段不变。 */
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 下发时填充：教师姓名。 */
    private String teacherName;

    /** 下发时填充：教师所属部门。 */
    private String department;

    /** 下发时填充：时间段 label。 */
    private String timeSlotName;

    /** 下发时填充：星期几。 */
    private Integer dayOfWeek;

    /** 下发时填充：第几大节。 */
    private Integer periodNo;
}
