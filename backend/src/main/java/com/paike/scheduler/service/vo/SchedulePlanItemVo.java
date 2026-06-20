package com.paike.scheduler.service.vo;

import com.paike.scheduler.entity.SchedulePlanItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 排课方案明细下发项（M-16 第6批：替换 SchedulePlanItem Entity 上的 5 个
 * {@code @TableField(exist = false)} view 字段 courseName/teacherName/className/roomName/timeLabel）。
 *
 * 字段（24 个，按 Entity 声明序）：前 19 为持久化列
 * （id / planId / semesterId / teachingTaskId / teacherId / classId / courseId / classroomId /
 * weekday / startPeriod / endPeriod / weekType / score / conflictFlag / conflictReason / sourceType /
 * createdAt / updatedAt / deleted），含 @TableLogic 软删除标记 deleted（恒 0），
 * {@code @TableField("created_at")}/{@code @TableField("updated_at")} 列名映射；
 * 后 5 为 fillItemRelations 填充的展示字段。
 *
 * 该 VO 由 getPlanItems → fillItemRelations 统一填充后，下发到：方案明细列表端点、
 * V4ScheduleLockService 锁定项 VO 嵌套、V5SimulationPlanDetailVo.items 嵌套列表、
 * V5SimulationService compare 子系统（loadCompareItems → indexByTeachingTaskId →
 * buildItemChanges / buildLoadChanges / buildClassroomUtilizationChanges 等）。
 *
 * 普通 POJO、保留 null 序列化，不加 NON_NULL。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchedulePlanItemVo {

    private Long id;

    private Long planId;

    private Long semesterId;

    private Long teachingTaskId;

    private Long teacherId;

    private Long classId;

    private Long courseId;

    private Long classroomId;

    private Integer weekday;

    private Integer startPeriod;

    private Integer endPeriod;

    private String weekType;

    /** 连续周段起始周（闭区间，默认1，V10 连续周段支持） */
    private Integer startWeek;

    /** 连续周段结束周（闭区间，默认20，V10 连续周段支持） */
    private Integer endWeek;

    private BigDecimal score;

    private Integer conflictFlag;

    private String conflictReason;

    private String sourceType;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** @TableLogic 软删除标记，恒 0（已删行被 MyBatis-Plus 过滤）；保留以维持历史 JSON 逐字段不变。 */
    private Integer deleted;

    /** 下发时填充：课程名称。 */
    private String courseName;

    /** 下发时填充：教师姓名。 */
    private String teacherName;

    /** 下发时填充：班级名称。 */
    private String className;

    /** 下发时填充：教室名称。 */
    private String roomName;

    /** 下发时填充：周次+节次 label，如「周1 第1-2节」。 */
    private String timeLabel;

    /** 从 Entity 逐字段拷贝 19 个持久化列（view 字段留空，由 fillItemRelations 填充）。 */
    public static SchedulePlanItemVo fromEntity(SchedulePlanItem entity) {
        SchedulePlanItemVo vo = new SchedulePlanItemVo();
        vo.setId(entity.getId());
        vo.setPlanId(entity.getPlanId());
        vo.setSemesterId(entity.getSemesterId());
        vo.setTeachingTaskId(entity.getTeachingTaskId());
        vo.setTeacherId(entity.getTeacherId());
        vo.setClassId(entity.getClassId());
        vo.setCourseId(entity.getCourseId());
        vo.setClassroomId(entity.getClassroomId());
        vo.setWeekday(entity.getWeekday());
        vo.setStartPeriod(entity.getStartPeriod());
        vo.setEndPeriod(entity.getEndPeriod());
        vo.setWeekType(entity.getWeekType());
        vo.setStartWeek(entity.getStartWeek());
        vo.setEndWeek(entity.getEndWeek());
        vo.setScore(entity.getScore());
        vo.setConflictFlag(entity.getConflictFlag());
        vo.setConflictReason(entity.getConflictReason());
        vo.setSourceType(entity.getSourceType());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        vo.setDeleted(entity.getDeleted());
        return vo;
    }
}
