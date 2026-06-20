package com.paike.scheduler.service.vo;

import com.paike.scheduler.entity.TeachingTask;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 教学任务下发项（M-16 第8批：替换 TeachingTask Entity 上的 8 个
 * {@code @TableField(exist = false)} view 字段）。
 *
 * 字段（20 个，按 Entity 声明序）：前 12 为持久化列（含 @TableLogic deleted、@TableField createTime/updateTime），
 * 后 8 为 view 字段：courseName/teacherName/className/scheduledSlots 由 Java fillTaskRelations 填充，
 * courseType/teacherStatus/classStatus/studentCount 由 Mapper XML selectConflictCheckById SQL 别名填充。
 *
 * 普通 POJO、保留 null 序列化，不加 NON_NULL。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeachingTaskVo {

    private Long id;
    private Long semesterId;
    private Long courseId;
    private Long teacherId;
    private Long classId;
    private Integer weeklyHours;
    /** 周次类型：ALL全周、ODD单周、EVEN双周（V9 单双周支持） */
    private String weekType;
    /** 连续周段起始周（闭区间，默认1，V10 连续周段支持） */
    private Integer startWeek;
    /** 连续周段结束周（闭区间，默认20，V10 连续周段支持） */
    private Integer endWeek;
    private Integer needContinuous;
    private Integer status;
    private String remark;
    /** @TableLogic 软删除标记，恒 0。 */
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** fillTaskRelations / create/update 填充：课程名称。 */
    private String courseName;
    /** fillTaskRelations / selectConflictCheckById / create/update 填充：教师姓名。 */
    private String teacherName;
    /** fillTaskRelations / selectConflictCheckById / create/update 填充：班级名称。 */
    private String className;
    /** fillTaskRelations 填充：已排节次数。 */
    private Integer scheduledSlots;
    /** selectConflictCheckById 填充：课程类型。 */
    private String courseType;
    /** selectConflictCheckById 填充：教师状态。 */
    private Integer teacherStatus;
    /** selectConflictCheckById 填充：班级状态。 */
    private Integer classStatus;
    /** selectConflictCheckById 填充：班级学生数。 */
    private Integer studentCount;

    public static TeachingTaskVo fromEntity(TeachingTask entity) {
        TeachingTaskVo vo = new TeachingTaskVo();
        vo.setId(entity.getId());
        vo.setSemesterId(entity.getSemesterId());
        vo.setCourseId(entity.getCourseId());
        vo.setTeacherId(entity.getTeacherId());
        vo.setClassId(entity.getClassId());
        vo.setWeeklyHours(entity.getWeeklyHours());
        vo.setWeekType(entity.getWeekType());
        vo.setStartWeek(entity.getStartWeek());
        vo.setEndWeek(entity.getEndWeek());
        vo.setNeedContinuous(entity.getNeedContinuous());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setDeleted(entity.getDeleted());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
