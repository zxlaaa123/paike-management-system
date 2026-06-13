package com.paike.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.paike.scheduler.entity.SchedulePlanItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SchedulePlanItemMapper extends BaseMapper<SchedulePlanItem> {

    /** 批量插入方案明细，单条 SQL VALUES 列表，用于方案生成时一次性落库。 */
    int insertBatch(@Param("list") List<SchedulePlanItem> list);
}
