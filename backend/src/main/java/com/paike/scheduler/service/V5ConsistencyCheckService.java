package com.paike.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paike.scheduler.common.enums.CourseType;
import com.paike.scheduler.common.enums.RoomType;
import com.paike.scheduler.common.enums.V5ConsistencyStatus;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.ClassInfo;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.entity.Course;
import com.paike.scheduler.entity.Schedule;
import com.paike.scheduler.entity.ScheduleAdjustLog;
import com.paike.scheduler.entity.ScheduleConsistencyCheck;
import com.paike.scheduler.entity.ScheduleLockedItem;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.ScheduleRepairTask;
import com.paike.scheduler.entity.Teacher;
import com.paike.scheduler.entity.TimeSlot;
import com.paike.scheduler.mapper.ClassInfoMapper;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.CourseMapper;
import com.paike.scheduler.mapper.ScheduleAdjustLogMapper;
import com.paike.scheduler.mapper.ScheduleConsistencyCheckMapper;
import com.paike.scheduler.mapper.ScheduleLockedItemMapper;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.ScheduleRepairTaskMapper;
import com.paike.scheduler.mapper.TeacherMapper;
import com.paike.scheduler.mapper.TimeSlotMapper;
import com.paike.scheduler.service.vo.V5ConsistencyCheckReportVo;
import com.paike.scheduler.service.vo.V5ConsistencyIssueVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class V5ConsistencyCheckService {

    public static final String SEVERITY_BLOCKING = "BLOCKING";
    public static final String SEVERITY_WARNING = "WARNING";
    public static final String SEVERITY_INFO = "INFO";

    private static final String CHECK_TYPE = "V5_SIMULATION_CONSISTENCY";

    private final ScheduleRepairTaskMapper repairTaskMapper;
    private final SchedulePlanMapper planMapper;
    private final SchedulePlanItemMapper planItemMapper;
    private final ScheduleMapper scheduleMapper;
    private final ScheduleLockedItemMapper lockedItemMapper;
    private final ScheduleAdjustLogMapper adjustLogMapper;
    private final ScheduleConsistencyCheckMapper consistencyCheckMapper;
    private final TeacherMapper teacherMapper;
    private final ClassInfoMapper classInfoMapper;
    private final ClassroomMapper classroomMapper;
    private final CourseMapper courseMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final TeacherUnavailableTimeService unavailableTimeService;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public V5ConsistencyCheckReportVo check(Long taskId, Long planId, boolean persist) {
        ScheduleRepairTask task = repairTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("修复任务不存在");
        }
        SchedulePlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException("试算方案不存在");
        }
        if (!Objects.equals(plan.getRepairTaskId(), task.getId())) {
            throw new BusinessException("试算方案不属于当前修复任务");
        }

        SchedulePlan source = plan.getSourcePlanId() == null ? null : planMapper.selectById(plan.getSourcePlanId());
        List<SchedulePlanItem> simulationItems = planItemMapper.selectList(new LambdaQueryWrapper<SchedulePlanItem>()
                .eq(SchedulePlanItem::getPlanId, plan.getId()));
        List<SchedulePlanItem> sourceItems = source == null ? List.of() : planItemMapper.selectList(new LambdaQueryWrapper<SchedulePlanItem>()
                .eq(SchedulePlanItem::getPlanId, source.getId()));

        Map<Long, Classroom> roomCache = new LinkedHashMap<>();
        Map<Long, Teacher> teacherCache = new LinkedHashMap<>();
        Map<Long, ClassInfo> classInfoCache = new LinkedHashMap<>();
        Map<Long, Course> courseCache = new LinkedHashMap<>();
        Map<String, TimeSlot> slotCache = new LinkedHashMap<>();
        loadTimeSlots(slotCache);

        List<V5ConsistencyIssueVo> issues = new ArrayList<>();
        checkSemesterLineage(task, plan, source, issues);
        checkSourceLineage(task, plan, source, issues);
        checkSimulationStatus(plan, issues);
        checkLockedItemsPreserved(plan, source, sourceItems, simulationItems, issues);
        checkScopeBoundary(task, plan, sourceItems, simulationItems, issues);
        checkItemDataCompleteness(plan, simulationItems, issues);
        checkDuplicateTeachingTask(plan, simulationItems, issues);
        checkHardConflicts(plan, simulationItems, issues, roomCache, teacherCache, classInfoCache);
        checkTeacherUnavailable(plan, simulationItems, issues, slotCache, teacherCache);
        checkClassroomCapacityAndType(plan, simulationItems, issues, roomCache, classInfoCache, courseCache);
        checkNoFormalOverwrite(plan, issues);

        int blocking = (int) issues.stream().filter(i -> SEVERITY_BLOCKING.equals(i.getSeverity())).count();
        int warning = (int) issues.stream().filter(i -> SEVERITY_WARNING.equals(i.getSeverity())).count();
        int info = (int) issues.stream().filter(i -> SEVERITY_INFO.equals(i.getSeverity())).count();
        String status = blocking > 0 ? V5ConsistencyStatus.FAIL.getCode()
                : (warning > 0 ? V5ConsistencyStatus.WARN.getCode() : V5ConsistencyStatus.PASS.getCode());
        boolean passed = blocking == 0;

        V5ConsistencyCheckReportVo report = new V5ConsistencyCheckReportVo();
        report.setTaskId(task.getId());
        report.setPlanId(plan.getId());
        report.setSourcePlanId(plan.getSourcePlanId());
        report.setSemesterId(plan.getSemesterId());
        report.setStatus(status);
        report.setPassed(passed);
        report.setBlockingIssueCount(blocking);
        report.setWarningIssueCount(warning);
        report.setInfoIssueCount(info);
        report.setIssues(issues);
        report.setSummary(buildSummary(blocking, warning, info));
        report.setRecommendation(buildRecommendation(passed, blocking, warning));
        report.setCheckedAt(LocalDateTime.now());

        if (persist) {
            ScheduleConsistencyCheck entity = new ScheduleConsistencyCheck();
            entity.setSemesterId(plan.getSemesterId());
            entity.setPlanId(plan.getId());
            entity.setSourcePlanId(plan.getSourcePlanId());
            entity.setScheduleId(task.getSourceScheduleId());
            entity.setCheckType(CHECK_TYPE);
            entity.setCheckScope("PLAN");
            entity.setStatus(status);
            entity.setIssueCount(issues.size());
            entity.setBlockingIssueCount(blocking);
            entity.setResultSummary(report.getSummary());
            entity.setDetailJson(writeJson(report));
            entity.setCheckedAt(report.getCheckedAt());
            consistencyCheckMapper.insert(entity);
            report.setReportId(entity.getId());
        }
        return report;
    }

    public V5ConsistencyCheckReportVo latest(Long taskId, Long planId) {
        ScheduleRepairTask task = repairTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("修复任务不存在");
        }
        SchedulePlan plan = planMapper.selectById(planId);
        if (plan == null || !Objects.equals(plan.getRepairTaskId(), task.getId())) {
            throw new BusinessException("试算方案不属于当前修复任务");
        }
        ScheduleConsistencyCheck entity = consistencyCheckMapper.selectOne(new LambdaQueryWrapper<ScheduleConsistencyCheck>()
                .eq(ScheduleConsistencyCheck::getPlanId, plan.getId())
                .eq(ScheduleConsistencyCheck::getCheckType, CHECK_TYPE)
                .orderByDesc(ScheduleConsistencyCheck::getCheckedAt)
                .orderByDesc(ScheduleConsistencyCheck::getId)
                .last("LIMIT 1"));
        if (entity == null) {
            return null;
        }
        V5ConsistencyCheckReportVo report = readReport(entity.getDetailJson());
        if (report == null) {
            report = new V5ConsistencyCheckReportVo();
            report.setIssues(List.of());
        }
        report.setReportId(entity.getId());
        report.setTaskId(task.getId());
        report.setPlanId(plan.getId());
        report.setSourcePlanId(entity.getSourcePlanId());
        report.setSemesterId(entity.getSemesterId());
        report.setStatus(entity.getStatus());
        report.setPassed(V5ConsistencyStatus.PASS.getCode().equals(entity.getStatus())
                || V5ConsistencyStatus.WARN.getCode().equals(entity.getStatus()));
        report.setBlockingIssueCount(entity.getBlockingIssueCount());
        report.setSummary(entity.getResultSummary());
        report.setCheckedAt(entity.getCheckedAt());
        return report;
    }

    /** apply 前的强制 gate：执行检查并落库，存在 BLOCKING 时抛业务异常 */
    @Transactional(rollbackFor = Exception.class)
    public V5ConsistencyCheckReportVo ensurePassBeforeApply(Long taskId, Long planId) {
        V5ConsistencyCheckReportVo report = check(taskId, planId, true);
        if (Boolean.FALSE.equals(report.getPassed())) {
            String detail = report.getIssues() == null || report.getIssues().isEmpty()
                    ? report.getRecommendation()
                    : report.getIssues().stream()
                            .filter(i -> SEVERITY_BLOCKING.equals(i.getSeverity()))
                            .map(i -> i.getName() + "：" + i.getMessage())
                            .collect(Collectors.joining("；"));
            throw new BusinessException("试算方案未通过一致性校验，不能应用。" + detail);
        }
        return report;
    }

    // ============== 12 条检查 ==============

    private void checkSemesterLineage(ScheduleRepairTask task, SchedulePlan plan, SchedulePlan source, List<V5ConsistencyIssueVo> issues) {
        if (source != null && !Objects.equals(plan.getSemesterId(), source.getSemesterId())) {
            issues.add(issue("SEMESTER_MISMATCH", SEVERITY_BLOCKING, "LINEAGE", "试算方案与原方案学期一致",
                    "试算方案学期(" + plan.getSemesterId() + ")与原方案学期(" + source.getSemesterId() + ")不一致",
                    "确认试算方案是否串学期，若是请放弃并重新生成"));
        }
        if (task.getSemesterId() != null && !Objects.equals(task.getSemesterId(), plan.getSemesterId())) {
            issues.add(issue("SEMESTER_TASK_MISMATCH", SEVERITY_BLOCKING, "LINEAGE", "试算方案与修复任务学期一致",
                    "试算方案学期(" + plan.getSemesterId() + ")与修复任务学期(" + task.getSemesterId() + ")不一致",
                    "请放弃方案并重新通过该任务生成"));
        }
    }

    private void checkSourceLineage(ScheduleRepairTask task, SchedulePlan plan, SchedulePlan source, List<V5ConsistencyIssueVo> issues) {
        if (plan.getRepairTaskId() == null) {
            issues.add(issue("MISSING_REPAIR_TASK_REF", SEVERITY_BLOCKING, "LINEAGE", "试算方案需绑定修复任务",
                    "试算方案缺少 repairTaskId", "请通过修复任务流程重新生成试算方案"));
        } else if (!Objects.equals(plan.getRepairTaskId(), task.getId())) {
            issues.add(issue("REPAIR_TASK_MISMATCH", SEVERITY_BLOCKING, "LINEAGE", "试算方案归属",
                    "试算方案 repairTaskId(" + plan.getRepairTaskId() + ") 与传入任务 ID(" + task.getId() + ") 不一致",
                    "请使用正确的修复任务详情页打开该试算方案"));
        }
        if (plan.getSourcePlanId() == null) {
            issues.add(issue("MISSING_SOURCE_PLAN", SEVERITY_BLOCKING, "LINEAGE", "试算方案需引用原方案",
                    "试算方案缺少 sourcePlanId", "请放弃方案并基于原方案重新生成"));
        } else if (source == null) {
            issues.add(issue("SOURCE_PLAN_NOT_FOUND", SEVERITY_BLOCKING, "LINEAGE", "原方案存在性",
                    "原方案 ID=" + plan.getSourcePlanId() + " 不存在", "请检查原方案是否被删除"));
        }
        if (task.getSourceScheduleId() != null && plan.getSourceScheduleId() != null
                && !Objects.equals(task.getSourceScheduleId(), plan.getSourceScheduleId())) {
            issues.add(issue("SOURCE_SCHEDULE_MISMATCH", SEVERITY_WARNING, "LINEAGE", "正式课表来源",
                    "试算方案 sourceScheduleId(" + plan.getSourceScheduleId() + ") 与任务 sourceScheduleId("
                            + task.getSourceScheduleId() + ") 不一致",
                    "确认是否串了正式课表来源"));
        }
    }

    private void checkSimulationStatus(SchedulePlan plan, List<V5ConsistencyIssueVo> issues) {
        if (!"SIMULATION".equals(plan.getStatus()) && !"CONFIRMED".equals(plan.getStatus())) {
            issues.add(issue("INVALID_SIMULATION_STATUS", SEVERITY_BLOCKING, "PROCESS", "方案需处于试算状态",
                    "当前方案状态为 " + plan.getStatus() + "，非 SIMULATION/CONFIRMED",
                    "只能基于试算方案进行一致性校验"));
        }
        if (!"SIMULATION".equals(plan.getPlanMode())) {
            issues.add(issue("INVALID_PLAN_MODE", SEVERITY_WARNING, "PROCESS", "方案模式",
                    "planMode=" + plan.getPlanMode() + "，非 SIMULATION",
                    "确认该方案是否为试算方案"));
        }
    }

    private void checkLockedItemsPreserved(SchedulePlan plan, SchedulePlan source,
                                           List<SchedulePlanItem> sourceItems, List<SchedulePlanItem> simulationItems,
                                           List<V5ConsistencyIssueVo> issues) {
        if (source == null) return;
        List<ScheduleLockedItem> sourceLocks = lockedItemMapper.selectList(new LambdaQueryWrapper<ScheduleLockedItem>()
                .eq(ScheduleLockedItem::getPlanId, source.getId())
                .eq(ScheduleLockedItem::getActiveFlag, 1)
                .isNotNull(ScheduleLockedItem::getPlanItemId));
        if (sourceLocks.isEmpty()) return;
        Map<Long, SchedulePlanItem> sourceItemById = indexById(sourceItems);
        Map<Long, SchedulePlanItem> simulationByTask = indexByTeachingTaskId(simulationItems);
        for (ScheduleLockedItem lock : sourceLocks) {
            SchedulePlanItem before = sourceItemById.get(lock.getPlanItemId());
            if (before == null || before.getTeachingTaskId() == null) continue;
            SchedulePlanItem after = simulationByTask.get(before.getTeachingTaskId());
            if (after == null) {
                V5ConsistencyIssueVo vo = issue("LOCKED_ITEM_MISSING", SEVERITY_BLOCKING, "SCOPE", "锁定课程缺失",
                        "锁定课程 teachingTaskId=" + before.getTeachingTaskId() + " 在试算方案中未找到",
                        "请放弃方案并重新生成，确保锁定课程被完整复制");
                fillItemContext(vo, before, null);
                issues.add(vo);
                continue;
            }
            if (placementChanged(before, after)) {
                V5ConsistencyIssueVo vo = issue("LOCKED_ITEM_MOVED", SEVERITY_BLOCKING, "SCOPE", "锁定课程未被保护",
                        "锁定课程发生移动：原周" + before.getWeekday() + " 第" + before.getStartPeriod() + "-" + before.getEndPeriod()
                                + " -> 新周" + after.getWeekday() + " 第" + after.getStartPeriod() + "-" + after.getEndPeriod(),
                        "请放弃方案并重新执行修复，保留锁定课程");
                fillItemContext(vo, after, null);
                issues.add(vo);
            }
        }
    }

    private void checkScopeBoundary(ScheduleRepairTask task, SchedulePlan plan,
                                    List<SchedulePlanItem> sourceItems, List<SchedulePlanItem> simulationItems,
                                    List<V5ConsistencyIssueVo> issues) {
        Set<Long> scopeTeachingTaskIds = resolveScopeTeachingTaskIds(task, sourceItems);
        if (scopeTeachingTaskIds.isEmpty()) return;
        Map<Long, SchedulePlanItem> sourceByTask = indexByTeachingTaskId(sourceItems);
        List<ScheduleAdjustLog> logs = adjustLogMapper.selectList(new LambdaQueryWrapper<ScheduleAdjustLog>()
                .eq(ScheduleAdjustLog::getPlanId, plan.getId()));
        Set<Long> loggedTaskIds = logs.stream()
                .map(ScheduleAdjustLog::getTeachingTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (SchedulePlanItem after : simulationItems) {
            if (after.getTeachingTaskId() == null) continue;
            SchedulePlanItem before = sourceByTask.get(after.getTeachingTaskId());
            if (before == null) continue;
            if (!placementChanged(before, after)) continue;
            if (scopeTeachingTaskIds.contains(after.getTeachingTaskId())) continue;
            if (loggedTaskIds.contains(after.getTeachingTaskId())) continue;
            V5ConsistencyIssueVo vo = issue("OUT_OF_SCOPE_CHANGE", SEVERITY_WARNING, "SCOPE", "课程变动超出修复范围",
                    "teachingTaskId=" + after.getTeachingTaskId() + " 不在任务范围内，但发生了移动",
                    "请检查局部重排范围设置，或在任务范围中明确包含该课程");
            fillItemContext(vo, after, null);
            issues.add(vo);
        }
    }

    private void checkItemDataCompleteness(SchedulePlan plan, List<SchedulePlanItem> items, List<V5ConsistencyIssueVo> issues) {
        for (SchedulePlanItem item : items) {
            List<String> missing = new ArrayList<>();
            if (item.getTeachingTaskId() == null) missing.add("teachingTaskId");
            if (item.getClassId() == null) missing.add("classId");
            if (item.getTeacherId() == null) missing.add("teacherId");
            if (item.getCourseId() == null) missing.add("courseId");
            if (item.getClassroomId() == null) missing.add("classroomId");
            if (item.getWeekday() == null) missing.add("weekday");
            if (item.getStartPeriod() == null) missing.add("startPeriod");
            if (item.getEndPeriod() == null) missing.add("endPeriod");
            if (missing.isEmpty()) continue;
            V5ConsistencyIssueVo vo = issue("ITEM_FIELD_MISSING", SEVERITY_BLOCKING, "DATA", "课程明细字段缺失",
                    "planItemId=" + item.getId() + " 缺少字段：" + String.join(", ", missing),
                    "请检查试算方案明细生成逻辑");
            fillItemContext(vo, item, null);
            issues.add(vo);
        }
    }

    private void checkDuplicateTeachingTask(SchedulePlan plan, List<SchedulePlanItem> items, List<V5ConsistencyIssueVo> issues) {
        Map<Long, List<SchedulePlanItem>> grouped = new LinkedHashMap<>();
        for (SchedulePlanItem item : items) {
            if (item.getTeachingTaskId() == null) continue;
            grouped.computeIfAbsent(item.getTeachingTaskId(), k -> new ArrayList<>()).add(item);
        }
        for (Map.Entry<Long, List<SchedulePlanItem>> entry : grouped.entrySet()) {
            if (entry.getValue().size() <= 1) continue;
            V5ConsistencyIssueVo vo = issue("DUPLICATE_TEACHING_TASK", SEVERITY_BLOCKING, "DATA", "同一教学任务重复排入",
                    "teachingTaskId=" + entry.getKey() + " 在试算方案中出现 " + entry.getValue().size() + " 条记录",
                    "请检查复制和移动逻辑，确保每个教学任务只对应一条明细");
            fillItemContext(vo, entry.getValue().get(0), null);
            issues.add(vo);
        }
    }

    private void checkHardConflicts(SchedulePlan plan, List<SchedulePlanItem> items, List<V5ConsistencyIssueVo> issues,
                                    Map<Long, Classroom> roomCache, Map<Long, Teacher> teacherCache,
                                    Map<Long, ClassInfo> classInfoCache) {
        for (int i = 0; i < items.size(); i++) {
            SchedulePlanItem a = items.get(i);
            if (a.getWeekday() == null || a.getStartPeriod() == null || a.getEndPeriod() == null) continue;
            for (int j = i + 1; j < items.size(); j++) {
                SchedulePlanItem b = items.get(j);
                if (!Objects.equals(a.getWeekday(), b.getWeekday())) continue;
                if (!overlap(a.getStartPeriod(), a.getEndPeriod(), b.getStartPeriod(), b.getEndPeriod())) continue;
                if (Objects.equals(a.getTeacherId(), b.getTeacherId()) && a.getTeacherId() != null) {
                    Teacher t = loadTeacher(a.getTeacherId(), teacherCache);
                    V5ConsistencyIssueVo vo = issue("TEACHER_HARD_CONFLICT", SEVERITY_BLOCKING, "CONFLICT", "教师时间冲突",
                            "教师 " + safeName(t == null ? null : t.getName()) + " 在周" + a.getWeekday()
                                    + " 第" + a.getStartPeriod() + "-" + a.getEndPeriod() + "节存在重叠课程",
                            "调整其中一节课的时间");
                    fillItemContext(vo, a, roomCache);
                    vo.setTeacherName(safeName(t == null ? null : t.getName()));
                    issues.add(vo);
                }
                if (Objects.equals(a.getClassId(), b.getClassId()) && a.getClassId() != null) {
                    ClassInfo c = loadClass(a.getClassId(), classInfoCache);
                    V5ConsistencyIssueVo vo = issue("CLASS_HARD_CONFLICT", SEVERITY_BLOCKING, "CONFLICT", "班级时间冲突",
                            "班级 " + safeName(c == null ? null : c.getClassName()) + " 在周" + a.getWeekday()
                                    + " 第" + a.getStartPeriod() + "-" + a.getEndPeriod() + "节存在重叠课程",
                            "调整其中一节课的时间");
                    fillItemContext(vo, a, roomCache);
                    vo.setClassName(safeName(c == null ? null : c.getClassName()));
                    issues.add(vo);
                }
                if (Objects.equals(a.getClassroomId(), b.getClassroomId()) && a.getClassroomId() != null) {
                    Classroom r = loadRoom(a.getClassroomId(), roomCache);
                    V5ConsistencyIssueVo vo = issue("CLASSROOM_HARD_CONFLICT", SEVERITY_BLOCKING, "CONFLICT", "教室时间冲突",
                            "教室 " + safeName(r == null ? null : r.getRoomName()) + " 在周" + a.getWeekday()
                                    + " 第" + a.getStartPeriod() + "-" + a.getEndPeriod() + "节被多次占用",
                            "调整其中一节课的教室或时间");
                    fillItemContext(vo, a, roomCache);
                    issues.add(vo);
                }
            }
        }
    }

    private void checkTeacherUnavailable(SchedulePlan plan, List<SchedulePlanItem> items, List<V5ConsistencyIssueVo> issues,
                                         Map<String, TimeSlot> slotCache, Map<Long, Teacher> teacherCache) {
        for (SchedulePlanItem item : items) {
            if (item.getTeacherId() == null || item.getWeekday() == null || item.getStartPeriod() == null
                    || item.getEndPeriod() == null) continue;
            if (item.getStartPeriod() % 2 == 0 || item.getEndPeriod() - item.getStartPeriod() != 1) continue;
            int periodNo = (item.getStartPeriod() + 1) / 2;
            TimeSlot slot = slotCache.get(item.getWeekday() + "_" + periodNo);
            if (slot == null) continue;
            if (!unavailableTimeService.isUnavailable(item.getTeacherId(), slot.getId())) continue;
            Teacher t = loadTeacher(item.getTeacherId(), teacherCache);
            V5ConsistencyIssueVo vo = issue("TEACHER_UNAVAILABLE_HIT", SEVERITY_BLOCKING, "RULE", "教师禁排命中",
                    "教师 " + safeName(t == null ? null : t.getName()) + " 在周" + item.getWeekday()
                            + " 第" + item.getStartPeriod() + "-" + item.getEndPeriod() + "节命中禁排时段",
                    "调整该课程时间或更新禁排设置");
            fillItemContext(vo, item, null);
            vo.setTeacherName(safeName(t == null ? null : t.getName()));
            issues.add(vo);
        }
    }

    private void checkClassroomCapacityAndType(SchedulePlan plan, List<SchedulePlanItem> items, List<V5ConsistencyIssueVo> issues,
                                               Map<Long, Classroom> roomCache, Map<Long, ClassInfo> classInfoCache,
                                               Map<Long, Course> courseCache) {
        for (SchedulePlanItem item : items) {
            if (item.getClassroomId() == null) continue;
            Classroom room = loadRoom(item.getClassroomId(), roomCache);
            if (room == null) {
                V5ConsistencyIssueVo vo = issue("CLASSROOM_NOT_FOUND", SEVERITY_BLOCKING, "DATA", "教室不存在",
                        "classroomId=" + item.getClassroomId() + " 在教室表中不存在或已删除",
                        "请重新选择教室");
                fillItemContext(vo, item, null);
                issues.add(vo);
                continue;
            }
            ClassInfo classInfo = item.getClassId() == null ? null : loadClass(item.getClassId(), classInfoCache);
            if (classInfo != null && classInfo.getStudentCount() != null && room.getCapacity() != null
                    && classInfo.getStudentCount() > room.getCapacity()) {
                V5ConsistencyIssueVo vo = issue("CLASSROOM_CAPACITY_OVERFLOW", SEVERITY_BLOCKING, "RULE", "教室容量不足",
                        "班级 " + safeName(classInfo.getClassName()) + "(" + classInfo.getStudentCount()
                                + ")超过教室 " + safeName(room.getRoomName()) + " 容量(" + room.getCapacity() + ")",
                        "改派更大容量的教室");
                fillItemContext(vo, item, roomCache);
                vo.setClassName(safeName(classInfo.getClassName()));
                issues.add(vo);
            }
            Course course = item.getCourseId() == null ? null : loadCourse(item.getCourseId(), courseCache);
            if (course != null && isRoomTypeMismatch(course, room)) {
                V5ConsistencyIssueVo vo = issue("CLASSROOM_TYPE_MISMATCH", SEVERITY_BLOCKING, "RULE", "教室类型不匹配",
                        "课程类型 " + course.getCourseType() + " 与教室类型 " + room.getRoomType() + " 不匹配",
                        "实验课需放在实验室，机房课需放在机房");
                fillItemContext(vo, item, roomCache);
                issues.add(vo);
            }
        }
    }

    private void checkNoFormalOverwrite(SchedulePlan plan, List<V5ConsistencyIssueVo> issues) {
        long formalCount = scheduleMapper.selectCount(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getPlanId, plan.getId())
                .eq(Schedule::getDeleted, 0));
        if (formalCount > 0) {
            issues.add(issue("FORMAL_SCHEDULE_OVERWRITE", SEVERITY_BLOCKING, "PROCESS", "试算方案已直接写入正式课表",
                    "planId=" + plan.getId() + " 已存在 " + formalCount + " 条正式课表记录",
                    "试算方案必须经过确认+应用流程才能写入正式课表"));
        }
    }

    // ============== 工具方法 ==============

    private Set<Long> resolveScopeTeachingTaskIds(ScheduleRepairTask task, List<SchedulePlanItem> sourceItems) {
        Set<Long> scopePlanItemIds = new LinkedHashSet<>(readLongList(task.getScopePlanItemIds()));
        if (scopePlanItemIds.isEmpty()) return Set.of();
        Map<Long, SchedulePlanItem> sourceById = indexById(sourceItems);
        return scopePlanItemIds.stream()
                .map(sourceById::get)
                .filter(Objects::nonNull)
                .map(SchedulePlanItem::getTeachingTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void loadTimeSlots(Map<String, TimeSlot> cache) {
        List<TimeSlot> slots = timeSlotMapper.selectList(new LambdaQueryWrapper<>());
        for (TimeSlot slot : slots) {
            cache.put(slot.getDayOfWeek() + "_" + slot.getPeriodNo(), slot);
        }
    }

    private Classroom loadRoom(Long id, Map<Long, Classroom> cache) {
        if (id == null) return null;
        return cache.computeIfAbsent(id, classroomMapper::selectById);
    }

    private Teacher loadTeacher(Long id, Map<Long, Teacher> cache) {
        if (id == null) return null;
        return cache.computeIfAbsent(id, teacherMapper::selectById);
    }

    private ClassInfo loadClass(Long id, Map<Long, ClassInfo> cache) {
        if (id == null) return null;
        return cache.computeIfAbsent(id, classInfoMapper::selectById);
    }

    private Course loadCourse(Long id, Map<Long, Course> cache) {
        if (id == null) return null;
        return cache.computeIfAbsent(id, courseMapper::selectById);
    }

    private boolean placementChanged(SchedulePlanItem before, SchedulePlanItem after) {
        return !Objects.equals(before.getWeekday(), after.getWeekday())
                || !Objects.equals(before.getStartPeriod(), after.getStartPeriod())
                || !Objects.equals(before.getEndPeriod(), after.getEndPeriod())
                || !Objects.equals(before.getClassroomId(), after.getClassroomId());
    }

    private boolean overlap(Integer aStart, Integer aEnd, Integer bStart, Integer bEnd) {
        if (aStart == null || aEnd == null || bStart == null || bEnd == null) return false;
        return !(aEnd < bStart || bEnd < aStart);
    }

    private boolean isRoomTypeMismatch(Course course, Classroom room) {
        if (course == null || room == null) return false;
        if (CourseType.EXPERIMENT.getCode().equals(course.getCourseType())) {
            return !RoomType.LAB.getCode().equals(room.getRoomType());
        }
        if (CourseType.COMPUTER.getCode().equals(course.getCourseType())) {
            return !RoomType.COMPUTER.getCode().equals(room.getRoomType());
        }
        return false;
    }

    private Map<Long, SchedulePlanItem> indexById(List<SchedulePlanItem> items) {
        Map<Long, SchedulePlanItem> map = new LinkedHashMap<>();
        for (SchedulePlanItem item : items) {
            if (item.getId() != null) map.put(item.getId(), item);
        }
        return map;
    }

    private Map<Long, SchedulePlanItem> indexByTeachingTaskId(List<SchedulePlanItem> items) {
        Map<Long, SchedulePlanItem> map = new LinkedHashMap<>();
        for (SchedulePlanItem item : items) {
            if (item.getTeachingTaskId() != null) map.putIfAbsent(item.getTeachingTaskId(), item);
        }
        return map;
    }

    private V5ConsistencyIssueVo issue(String code, String severity, String category, String name, String message, String suggestion) {
        V5ConsistencyIssueVo vo = new V5ConsistencyIssueVo();
        vo.setCode(code);
        vo.setSeverity(severity);
        vo.setCategory(category);
        vo.setName(name);
        vo.setMessage(message);
        vo.setSuggestion(suggestion);
        return vo;
    }

    private void fillItemContext(V5ConsistencyIssueVo vo, SchedulePlanItem item, Map<Long, Classroom> roomCache) {
        if (item == null) return;
        vo.setPlanItemId(item.getId());
        vo.setTeachingTaskId(item.getTeachingTaskId());
        vo.setWeekday(item.getWeekday());
        vo.setStartPeriod(item.getStartPeriod());
        vo.setEndPeriod(item.getEndPeriod());
        vo.setCourseName(item.getCourseName());
        if (vo.getTeacherName() == null) vo.setTeacherName(item.getTeacherName());
        if (vo.getClassName() == null) vo.setClassName(item.getClassName());
        if (item.getRoomName() != null) {
            vo.setClassroomName(item.getRoomName());
        } else if (roomCache != null && item.getClassroomId() != null) {
            Classroom r = loadRoom(item.getClassroomId(), roomCache);
            if (r != null) vo.setClassroomName(r.getRoomName());
        }
    }

    private List<Long> readLongList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isArray()) return List.of();
            List<Long> result = new ArrayList<>();
            for (JsonNode item : node) {
                if (item != null && item.canConvertToLong()) {
                    result.add(item.asLong());
                }
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private V5ConsistencyCheckReportVo readReport(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, V5ConsistencyCheckReportVo.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String safeName(String value) {
        if (value == null || value.isBlank()) return "未知";
        return value.trim();
    }

    private String buildSummary(int blocking, int warning, int info) {
        Collection<String> parts = new ArrayList<>();
        if (blocking > 0) parts.add("阻塞 " + blocking);
        if (warning > 0) parts.add("警告 " + warning);
        if (info > 0) parts.add("提示 " + info);
        if (parts.isEmpty()) return "一致性校验通过，未发现问题。";
        return "一致性校验发现问题：" + String.join("、", parts) + "。";
    }

    private String buildRecommendation(boolean passed, int blocking, int warning) {
        if (!passed) {
            return "存在 " + blocking + " 个阻塞问题，禁止应用该试算方案，请按问题清单逐项修复或放弃方案重新生成。";
        }
        if (warning > 0) {
            return "存在 " + warning + " 个警告项，建议人工复核后再应用。";
        }
        return "一致性校验全部通过，可以确认并应用试算方案。";
    }
}
