package com.paike.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.paike.scheduler.entity.Semester;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SemesterMapper extends BaseMapper<Semester> {

    /**
     * 以悲观行锁方式获取学期，用于串行化同学期的方案应用操作。
     * 必须在事务中调用，锁在事务结束时释放。
     */
    @Select("SELECT * FROM semester WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Semester selectByIdForUpdate(@Param("id") Long id);
}
