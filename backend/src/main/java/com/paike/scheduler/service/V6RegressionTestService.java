package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paike.scheduler.auth.AuthUserContext;
import com.paike.scheduler.common.enums.V5RegressionStatus;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.Schedule;
import com.paike.scheduler.entity.ScheduleRegressionTest;
import com.paike.scheduler.entity.Semester;
import com.paike.scheduler.entity.SysUser;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.ScheduleRegressionTestMapper;
import com.paike.scheduler.service.vo.V6RegressionRunResultVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class V6RegressionTestService {

    /** 自检套件标识：对正式课表跑的一致性扫描。 */
    private static final String SELFCHECK_SUITE = "FORMAL_SCHEDULE_SELFCHECK";
    private static final String SELFCHECK_STAGE = "V6_SELFCHECK";

    private final ScheduleRegressionTestMapper regressionTestMapper;
    private final ScheduleMapper scheduleMapper;
    private final SemesterService semesterService;

    public Page<ScheduleRegressionTest> list(
            String testStage,
            String testSuite,
            String status,
            Long semesterId,
            Long planId,
            int page,
            int size
    ) {
        LambdaQueryWrapper<ScheduleRegressionTest> wrapper = new LambdaQueryWrapper<>();
        if (hasText(testStage)) {
            wrapper.eq(ScheduleRegressionTest::getTestStage, testStage.trim());
        }
        if (hasText(testSuite)) {
            wrapper.eq(ScheduleRegressionTest::getTestSuite, testSuite.trim());
        }
        if (hasText(status)) {
            wrapper.eq(ScheduleRegressionTest::getStatus, status.trim().toUpperCase());
        }
        if (semesterId != null) {
            wrapper.eq(ScheduleRegressionTest::getSemesterId, semesterId);
        }
        if (planId != null) {
            wrapper.eq(ScheduleRegressionTest::getPlanId, planId);
        }
        wrapper.orderByDesc(ScheduleRegressionTest::getExecutedAt)
                .orderByDesc(ScheduleRegressionTest::getId);
        return regressionTestMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public ScheduleRegressionTest getById(Long id) {
        return regressionTestMapper.selectById(id);
    }

    /**
     * 执行回归自检：对指定学期（不传则取当前学期）的正式课表跑一组一致性扫描，
     * 每个扫描项写入一条 ScheduleRegressionTest 记录，并返回汇总。
     *
     * 重点覆盖 DB 唯一键无法拦截的语义冲突：同时段同资源的 ALL-vs-ODD/EVEN 周次重叠
     * （唯一键含 week_type 维度，不同 week_type 值不触发唯一约束，但语义上 ALL 与 ODD/EVEN 重叠）。
     */
    @Transactional(rollbackFor = Exception.class)
    public V6RegressionRunResultVo run(Long semesterId) {
        long start = System.currentTimeMillis();
        Semester semester = semesterId == null ? semesterService.getCurrentSemester() : semesterService.getById(semesterId);
        if (semester == null) {
            throw new BusinessException("未指定学期且无当前学期，无法执行自检");
        }
        Long resolvedSemesterId = semester.getId();

        List<Schedule> schedules = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getSemesterId, resolvedSemesterId));
        String executedBy = currentOperatorName();

        List<ScheduleRegressionTest> records = new ArrayList<>();
        records.add(scanResourceConflict(resolvedSemesterId, schedules, executedBy,
                "TEACHER_CONFLICT_SCAN", "教师同时段周次重叠扫描", Schedule::getTeacherId, "教师"));
        records.add(scanResourceConflict(resolvedSemesterId, schedules, executedBy,
                "CLASS_CONFLICT_SCAN", "班级同时段周次重叠扫描", Schedule::getClassId, "班级"));
        records.add(scanResourceConflict(resolvedSemesterId, schedules, executedBy,
                "CLASSROOM_CONFLICT_SCAN", "教室同时段周次重叠扫描", Schedule::getClassroomId, "教室"));
        records.add(scanDataIntegrity(resolvedSemesterId, schedules, executedBy));

        for (ScheduleRegressionTest record : records) {
            regressionTestMapper.insert(record);
        }

        long duration = System.currentTimeMillis() - start;
        int failed = (int) records.stream().filter(r -> V5RegressionStatus.FAIL.getCode().equals(r.getStatus())).count();
        int passed = records.size() - failed;

        V6RegressionRunResultVo result = new V6RegressionRunResultVo();
        result.setSemesterId(resolvedSemesterId);
        result.setTotal(records.size());
        result.setPassed(passed);
        result.setFailed(failed);
        result.setDurationMs(duration);
        result.setSummary(failed == 0
                ? "自检通过：" + records.size() + " 项扫描全部 PASS"
                : "自检发现问题：" + failed + " 项 FAIL / 共 " + records.size() + " 项");
        result.setRecords(records);
        return result;
    }

    /**
     * 扫描同一资源（教师/班级/教室）在同一物理时段（timeSlotId）下的周次重叠冲突。
     * 命中即视为该自检项 FAIL。
     */
    private ScheduleRegressionTest scanResourceConflict(Long semesterId, List<Schedule> schedules, String executedBy,
                                                        String testCase, String description,
                                                        Function<Schedule, Long> resourceIdGetter, String resourceLabel) {
        long start = System.currentTimeMillis();
        // 按 (timeSlotId, resourceId) 分组，组内若存在 weekType 重叠的两条记录即冲突
        Map<String, List<Schedule>> grouped = new LinkedHashMap<>();
        for (Schedule s : schedules) {
            Long resourceId = resourceIdGetter.apply(s);
            if (s.getTimeSlotId() == null || resourceId == null) {
                continue;
            }
            String key = s.getTimeSlotId() + "#" + resourceId;
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }

        List<String> conflicts = new ArrayList<>();
        for (List<Schedule> group : grouped.values()) {
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    Schedule a = group.get(i);
                    Schedule b = group.get(j);
                    if (WeekTypeSupport.overlap(a.getWeekType(), b.getWeekType())) {
                        conflicts.add(resourceLabel + "ID=" + resourceIdGetter.apply(a)
                                + " 时段ID=" + a.getTimeSlotId()
                                + " 周次重叠(" + WeekTypeSupport.normalize(a.getWeekType())
                                + "/" + WeekTypeSupport.normalize(b.getWeekType()) + ")"
                                + " scheduleId=" + a.getId() + "," + b.getId());
                    }
                }
            }
        }

        long duration = System.currentTimeMillis() - start;
        return buildRecord(semesterId, testCase, description, conflicts, duration, executedBy);
    }

    /** 扫描正式课表关键字段完整性（timeSlotId/classroomId/teachingTaskId 非空）。 */
    private ScheduleRegressionTest scanDataIntegrity(Long semesterId, List<Schedule> schedules, String executedBy) {
        long start = System.currentTimeMillis();
        List<String> problems = new ArrayList<>();
        for (Schedule s : schedules) {
            List<String> missing = new ArrayList<>();
            if (s.getTeachingTaskId() == null) missing.add("teachingTaskId");
            if (s.getTimeSlotId() == null) missing.add("timeSlotId");
            if (s.getClassroomId() == null) missing.add("classroomId");
            if (!missing.isEmpty()) {
                problems.add("scheduleId=" + s.getId() + " 缺少字段：" + String.join(",", missing));
            }
        }
        long duration = System.currentTimeMillis() - start;
        return buildRecord(semesterId, "DATA_INTEGRITY_SCAN", "正式课表关键字段完整性扫描",
                problems, duration, executedBy);
    }

    private ScheduleRegressionTest buildRecord(Long semesterId, String testCase, String description,
                                               List<String> problems, long durationMs, String executedBy) {
        ScheduleRegressionTest record = new ScheduleRegressionTest();
        record.setSemesterId(semesterId);
        record.setTestSuite(SELFCHECK_SUITE);
        record.setTestStage(SELFCHECK_STAGE);
        record.setTestCase(testCase);
        record.setDurationMs(durationMs);
        record.setExecutedBy(executedBy);
        record.setExecutedAt(LocalDateTime.now());
        if (problems.isEmpty()) {
            record.setStatus(V5RegressionStatus.PASS.getCode());
            record.setErrorMessage(null);
        } else {
            record.setStatus(V5RegressionStatus.FAIL.getCode());
            record.setErrorMessage(truncate(description + "：命中 " + problems.size() + " 项。"
                    + String.join("；", problems.subList(0, Math.min(problems.size(), 10)))));
        }
        return record;
    }

    private String currentOperatorName() {
        SysUser operator = AuthUserContext.get();
        if (operator == null) {
            return null;
        }
        return operator.getRealName() == null || operator.getRealName().isBlank()
                ? operator.getUsername()
                : operator.getRealName();
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

