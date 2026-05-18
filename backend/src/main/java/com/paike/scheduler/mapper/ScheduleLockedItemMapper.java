package com.paike.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.paike.scheduler.entity.ScheduleLockedItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ScheduleLockedItemMapper extends BaseMapper<ScheduleLockedItem> {
}
