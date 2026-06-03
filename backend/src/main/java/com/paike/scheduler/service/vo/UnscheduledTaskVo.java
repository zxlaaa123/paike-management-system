package com.paike.scheduler.service.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 未排任务下发项（M-16 第5批：替换 UnscheduledTask Entity 上的 4 个
 * {@code @TableField(exist = false)} view 字段 courseName/teacherName/className/batchNo）。
 *
 * 本批为 alias 模式首批（view 字段由 Mapper XML SQL 别名填充，非 Java fillRelations），
 * XML 的 resultType 改为本 VO，MyBatis 直接映射别名到 VO 字段，无需 toVo 转换。
 *
 * 字段（17 个，按 Entity 声明序）：前 13 为持久化列（无 @TableLogic，无 deleted），
 * 后 4 为 SQL 别名填充的展示字段。
 *
 * 普通 POJO、保留 null 序列化，不加 NON_NULL。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnscheduledTaskVo {

    private Long id;

    private Long batchId;

    private Long semesterId;

    private Long taskId;

    private Long courseId;

    private Long teacherId;

    private Long classId;

    private Integer requiredSlots;

    private Integer scheduledSlots;

    private Integer remainingSlots;

    private String reasonType;

    private String reasonMessage;

    private LocalDateTime createTime;

    /** SQL 别名填充：课程名称。 */
    private String courseName;

    /** SQL 别名填充：教师姓名。 */
    private String teacherName;

    /** SQL 别名填充：班级名称。 */
    private String className;

    /** SQL 别名填充：批次号。 */
    private String batchNo;
}
