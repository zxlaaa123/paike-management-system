package com.paike.scheduler.service.vo;

import com.paike.scheduler.service.WeekTypeSupport;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * M-16 第9批（最后一批）：Schedule Entity 上 10 个 @TableField(exist=false) view 字段迁移到 VO。
 *
 * 24 字段集（14 持久化〔含 deleted 恒 0 + createTime/updateTime〕+ 10 view），
 * view 字段由 ScheduleService.fillRelations 从关联表批量查询填充。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleVo {

    // ======== 持久化字段（14 个）========
    private Long id;
    private Long semesterId;
    private Long teachingTaskId;
    private Long courseId;
    private Long teacherId;
    private Long classId;
    private Long timeSlotId;
    private String weekType;
    private Long classroomId;
    private String sourceType;
    private Long batchId;
    private Long planId;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // ======== VO 展示字段（10 个，原 @TableField(exist = false)）========
    private String courseName;
    private String teacherName;
    private String className;
    private String timeLabel;
    private Integer dayOfWeek;
    private Integer periodNo;
    private String roomName;
    private String building;
    private String sourceTypeName;
    private String batchNo;

    public static ScheduleVo fromEntity(com.paike.scheduler.entity.Schedule entity) {
        ScheduleVo vo = new ScheduleVo();
        vo.setId(entity.getId());
        vo.setSemesterId(entity.getSemesterId());
        vo.setTeachingTaskId(entity.getTeachingTaskId());
        vo.setCourseId(entity.getCourseId());
        vo.setTeacherId(entity.getTeacherId());
        vo.setClassId(entity.getClassId());
        vo.setTimeSlotId(entity.getTimeSlotId());
        vo.setWeekType(WeekTypeSupport.normalize(entity.getWeekType()));
        vo.setClassroomId(entity.getClassroomId());
        vo.setSourceType(entity.getSourceType());
        vo.setBatchId(entity.getBatchId());
        vo.setPlanId(entity.getPlanId());
        vo.setDeleted(entity.getDeleted());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
