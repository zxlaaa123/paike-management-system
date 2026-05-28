package com.paike.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.entity.TeachingTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TeachingTaskMapper extends BaseMapper<TeachingTask> {

    /**
     * 分页查询教学任务（数据库层面过滤 + 分页）
     */
    List<TeachingTask> selectFilteredTasks(
        @Param("courseName") String courseName,
        @Param("teacherName") String teacherName,
        @Param("className") String className,
        @Param("status") Integer status,
        @Param("semesterId") Long semesterId,
        @Param("page") Page<TeachingTask> page
    );

    /**
     * 冲突检查详情：一次加载教学任务及硬约束所需的课程、教师、班级字段。
     */
    TeachingTask selectConflictCheckById(@Param("id") Long id);
}
