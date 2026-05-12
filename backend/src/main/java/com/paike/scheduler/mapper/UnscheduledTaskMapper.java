package com.paike.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.paike.scheduler.entity.UnscheduledTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UnscheduledTaskMapper extends BaseMapper<UnscheduledTask> {
}
