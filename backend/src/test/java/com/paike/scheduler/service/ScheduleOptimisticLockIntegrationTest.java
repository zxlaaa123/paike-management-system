package com.paike.scheduler.service;

import com.paike.scheduler.entity.Schedule;
import com.paike.scheduler.mapper.ScheduleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * schedule 表乐观锁端到端集成测试（V25 / P1 #4 乐观锁，首期仅 schedule 一张表）。
 *
 * <p>真库验证三件套协同生效：迁移加的 {@code version} 列、实体 {@code @Version} 字段、
 * {@code MybatisPlusConfig} 注册的 {@code OptimisticLockerInnerInterceptor}：
 * <ul>
 *   <li>插入记录默认 version=0（列 DEFAULT 0，插入时不显式赋值）</li>
 *   <li>两个会话读到同一 version；先提交者成功并把 version 自增到 1</li>
 *   <li>后提交者携带过期 version=0，{@code updateById} 返回 0 行（丢失更新被拦截）</li>
 *   <li>库内最终值是先提交者的写入，version=1</li>
 * </ul>
 *
 * <p>数据隔离遵循 CLAUDE.md：唯一 semester_id 后缀避免命中软删除安全唯一键，tearDown 物理清理。
 */
@SpringBootTest
class ScheduleOptimisticLockIntegrationTest {

    @Autowired
    private ScheduleMapper scheduleMapper;

    private Long scheduleId;

    @AfterEach
    void tearDown() {
        if (scheduleId != null) {
            scheduleMapper.deleteById(scheduleId);
        }
    }

    @Test
    void concurrentUpdate_secondWriterWithStaleVersionAffectsZeroRows() {
        long unique = System.currentTimeMillis() % 100_000_000L;
        Schedule inserted = newSchedule(unique);
        scheduleMapper.insert(inserted);
        scheduleId = inserted.getId();
        assertNotNull(scheduleId, "插入后应回填自增主键");

        // 两个会话各自读到同一行，version 相同（默认 0）。
        Schedule sessionA = scheduleMapper.selectById(scheduleId);
        Schedule sessionB = scheduleMapper.selectById(scheduleId);
        assertEquals(0, sessionA.getVersion(), "新插入记录 version 应为默认 0");
        assertEquals(sessionA.getVersion(), sessionB.getVersion(), "两个会话应读到相同 version");

        // 会话 A 先提交：命中 WHERE version=0，成功并把 version 自增到 1。
        sessionA.setClassroomId(unique + 1);
        sessionA.setUpdateTime(LocalDateTime.now());
        int firstRows = scheduleMapper.updateById(sessionA);
        assertEquals(1, firstRows, "先提交者应更新成功 1 行");

        // 会话 B 后提交：仍携带过期 version=0，乐观锁拦截，影响 0 行（丢失更新被阻止）。
        sessionB.setClassroomId(unique + 2);
        sessionB.setUpdateTime(LocalDateTime.now());
        int secondRows = scheduleMapper.updateById(sessionB);
        assertEquals(0, secondRows, "携带过期 version 的后提交者应影响 0 行");

        // 库内最终是 A 的写入，version 自增到 1。
        Schedule latest = scheduleMapper.selectById(scheduleId);
        assertEquals(1, latest.getVersion(), "成功更新后 version 应自增到 1");
        assertEquals(unique + 1, latest.getClassroomId(), "库内应保留先提交者的写入");
    }

    private Schedule newSchedule(long unique) {
        Schedule schedule = new Schedule();
        schedule.setSemesterId(unique);
        schedule.setTeachingTaskId(unique);
        schedule.setCourseId(unique);
        schedule.setTeacherId(unique);
        schedule.setClassId(unique);
        schedule.setTimeSlotId(unique);
        schedule.setClassroomId(unique);
        schedule.setWeekType("ALL");
        schedule.setStartWeek(1);
        schedule.setEndWeek(20);
        schedule.setSourceType("MANUAL");
        schedule.setCreateTime(LocalDateTime.now());
        schedule.setUpdateTime(LocalDateTime.now());
        return schedule;
    }
}
