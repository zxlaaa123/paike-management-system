package com.paike.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.entity.Schedule;
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
}
