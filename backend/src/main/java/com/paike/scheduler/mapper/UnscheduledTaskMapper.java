package com.paike.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.entity.UnscheduledTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UnscheduledTaskMapper extends BaseMapper<UnscheduledTask> {

    /**
     * 数据库层过滤并分页，同时返回列表展示需要的关联名称。
     */
    Page<UnscheduledTask> selectFilteredPage(
            @Param("batchId") Long batchId,
            @Param("courseName") String courseName,
            @Param("teacherName") String teacherName,
            @Param("className") String className,
            @Param("reasonType") String reasonType,
            @Param("page") Page<UnscheduledTask> page
    );
}
