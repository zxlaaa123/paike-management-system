package com.paike.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.entity.Schedule;
import com.paike.scheduler.service.dto.ScheduleDailyConflictCounts;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ScheduleMapper extends BaseMapper<Schedule> {

    /**
     * 根据关联条件过滤排课记录（数据库层面过滤 + 分页）
     */
    Page<Schedule> selectFilteredSchedulePage(
        @Param("courseName") String courseName,
        @Param("teacherName") String teacherName,
        @Param("className") String className,
        @Param("roomName") String roomName,
        @Param("dayOfWeek") Integer dayOfWeek,
        @Param("semesterId") Long semesterId,
        @Param("page") Page<Schedule> page
    );

    /**
     * 批量统计同一天的教师/班级/同课程排课数量，一次查询替代三次 selectCount。
     * 返回字段：teacherDaily、classDaily、sameCourse。
     * <p>V9 单双周：weekType 非 null 时，仅统计与该 weekType 重叠的行（裁决 β 独立计数）：
     * 传入 ALL 统计全部；传入 ODD 统计 ALL+ODD；传入 EVEN 统计 ALL+EVEN。
     * ODD 与 EVEN 不互相计入（共享时段不挤占日上限）。
     */
    ScheduleDailyConflictCounts selectDailyConflictCounts(
        @Param("teacherId") Long teacherId,
        @Param("classId") Long classId,
        @Param("courseId") Long courseId,
        @Param("daySlotIds") List<Long> daySlotIds,
        @Param("semesterId") Long semesterId,
        @Param("planId") Long planId,
        @Param("excludeScheduleId") Long excludeScheduleId,
        @Param("weekType") String weekType
    );
}
