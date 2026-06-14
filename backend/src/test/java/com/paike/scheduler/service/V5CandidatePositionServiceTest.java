package com.paike.scheduler.service;

import com.paike.scheduler.entity.ClassInfo;
import com.paike.scheduler.entity.Classroom;
import com.paike.scheduler.entity.ScheduleLockedItem;
import com.paike.scheduler.entity.SchedulePlan;
import com.paike.scheduler.entity.SchedulePlanItem;
import com.paike.scheduler.entity.TimeSlot;
import com.paike.scheduler.mapper.ClassInfoMapper;
import com.paike.scheduler.mapper.ClassroomMapper;
import com.paike.scheduler.mapper.ScheduleLockedItemMapper;
import com.paike.scheduler.mapper.ScheduleMapper;
import com.paike.scheduler.mapper.SchedulePlanItemMapper;
import com.paike.scheduler.mapper.SchedulePlanMapper;
import com.paike.scheduler.mapper.TimeSlotMapper;
import com.paike.scheduler.service.dto.V5CandidatePositionGenerateRequest;
import com.paike.scheduler.service.vo.V5CandidateEvaluationVo;
import com.paike.scheduler.service.vo.V5CandidatePositionResultVo;
import com.paike.scheduler.service.vo.V5CandidatePositionVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V5CandidatePositionServiceTest {

    private SchedulePlanMapper schedulePlanMapper;
    private SchedulePlanItemMapper schedulePlanItemMapper;
    private ScheduleMapper scheduleMapper;
    private TimeSlotMapper timeSlotMapper;
    private ClassroomMapper classroomMapper;
    private ClassInfoMapper classInfoMapper;
    private ScheduleLockedItemMapper scheduleLockedItemMapper;
    private TeacherUnavailableTimeService unavailableTimeService;
    private V5RuleEvaluationService ruleEvaluationService;
    private V5CandidatePositionService service;

    @BeforeEach
    void setUp() {
        schedulePlanMapper = mock(SchedulePlanMapper.class);
        schedulePlanItemMapper = mock(SchedulePlanItemMapper.class);
        scheduleMapper = mock(ScheduleMapper.class);
        timeSlotMapper = mock(TimeSlotMapper.class);
        classroomMapper = mock(ClassroomMapper.class);
        classInfoMapper = mock(ClassInfoMapper.class);
        scheduleLockedItemMapper = mock(ScheduleLockedItemMapper.class);
        unavailableTimeService = mock(TeacherUnavailableTimeService.class);
        ruleEvaluationService = mock(V5RuleEvaluationService.class);
        service = new V5CandidatePositionService(
                schedulePlanMapper,
                schedulePlanItemMapper,
                scheduleMapper,
                timeSlotMapper,
                classroomMapper,
                classInfoMapper,
                scheduleLockedItemMapper,
                unavailableTimeService,
                ruleEvaluationService
        );
    }

    @Test
    void generate_marksCandidateUnavailableWhenClassExceedsRoomCapacity() {
        SchedulePlanItem item = seedBaseData(60, 50);

        V5CandidatePositionResultVo result = service.generate(request(item.getId()));

        assertEquals(1, result.getTotalCount());
        assertEquals(0, result.getAvailableCount());
        V5CandidatePositionVo candidate = result.getCandidates().get(0);
        assertFalse(Boolean.TRUE.equals(candidate.getAvailable()));
        assertEquals(1, candidate.getHardConflictCount());
        assertTrue(candidate.getReason().contains("教室容量不足"));
        verify(ruleEvaluationService, never()).evaluateCandidate(any());
    }

    @Test
    void generate_allowsCandidateWhenClassFitsRoomCapacity() {
        SchedulePlanItem item = seedBaseData(40, 50);
        V5CandidateEvaluationVo evaluation = new V5CandidateEvaluationVo();
        evaluation.setAvailable(true);
        evaluation.setHardViolationCount(0);
        evaluation.setSoftScoreDelta(BigDecimal.ZERO);
        evaluation.setTotalScoreDelta(BigDecimal.TEN);
        evaluation.setSummary("硬约束通过");
        when(ruleEvaluationService.evaluateCandidate(any())).thenReturn(evaluation);

        V5CandidatePositionResultVo result = service.generate(request(item.getId()));

        assertEquals(1, result.getTotalCount());
        assertEquals(1, result.getAvailableCount());
        V5CandidatePositionVo candidate = result.getCandidates().get(0);
        assertTrue(Boolean.TRUE.equals(candidate.getAvailable()));
        assertEquals(0, candidate.getHardConflictCount());
        assertTrue(candidate.getReason().contains("可用"));
        verify(ruleEvaluationService).evaluateCandidate(any());
    }

    /**
     * V9 阶段 2C T7：候选位置不跨 weekType 错配。
     * target=ODD，候选时段有同教师 EVEN peer（period overlap）→ weekType 不 overlap → 候选可用。
     */
    @Test
    void generate_oddCandidateAvailableWithEvenPeerAtSameSlot() {
        SchedulePlanItem item = seedBaseDataWithPeer(40, 50, "ODD", peer("EVEN"));
        V5CandidateEvaluationVo evaluation = new V5CandidateEvaluationVo();
        evaluation.setAvailable(true);
        evaluation.setHardViolationCount(0);
        evaluation.setSoftScoreDelta(BigDecimal.ZERO);
        evaluation.setTotalScoreDelta(BigDecimal.TEN);
        evaluation.setSummary("硬约束通过");
        when(ruleEvaluationService.evaluateCandidate(any())).thenReturn(evaluation);

        V5CandidatePositionResultVo result = service.generate(request(item.getId()));

        assertEquals(1, result.getAvailableCount());
        V5CandidatePositionVo candidate = result.getCandidates().get(0);
        assertTrue(Boolean.TRUE.equals(candidate.getAvailable()), "ODD 候选与 EVEN peer 共槽应可用");
        assertEquals(0, candidate.getHardConflictCount());
    }

    /**
     * V9 阶段 2C：target=ALL，候选时段有同教师 ODD peer → ALL 与任意 overlap → 候选不可用（真冲突）。
     */
    @Test
    void generate_allCandidateUnavailableWithOddPeerAtSameSlot() {
        SchedulePlanItem item = seedBaseDataWithPeer(40, 50, "ALL", peer("ODD"));

        V5CandidatePositionResultVo result = service.generate(request(item.getId()));

        assertEquals(0, result.getAvailableCount());
        V5CandidatePositionVo candidate = result.getCandidates().get(0);
        assertFalse(Boolean.TRUE.equals(candidate.getAvailable()), "ALL 候选与 ODD peer 共槽应不可用");
        assertTrue(candidate.getHardConflictCount() >= 1);
    }

    private SchedulePlanItem seedBaseData(Integer studentCount, Integer roomCapacity) {
        SchedulePlan plan = new SchedulePlan();
        plan.setId(20L);
        plan.setSemesterId(1L);

        SchedulePlanItem item = new SchedulePlanItem();
        item.setId(10L);
        item.setPlanId(plan.getId());
        item.setSemesterId(plan.getSemesterId());
        item.setClassId(30L);
        item.setTeacherId(40L);
        item.setClassroomId(50L);
        item.setWeekday(1);
        item.setStartPeriod(1);
        item.setEndPeriod(2);

        Classroom sourceRoom = new Classroom();
        sourceRoom.setId(50L);
        sourceRoom.setRoomName("原教室");
        sourceRoom.setCapacity(100);

        Classroom candidateRoom = new Classroom();
        candidateRoom.setId(60L);
        candidateRoom.setRoomName("候选教室");
        candidateRoom.setCapacity(roomCapacity);

        TimeSlot slot = new TimeSlot();
        slot.setId(70L);
        slot.setDayOfWeek(2);
        slot.setPeriodNo(1);

        ClassInfo classInfo = new ClassInfo();
        classInfo.setId(item.getClassId());
        classInfo.setStudentCount(studentCount);

        when(schedulePlanItemMapper.selectById(item.getId())).thenReturn(item);
        when(schedulePlanMapper.selectById(plan.getId())).thenReturn(plan);
        when(classroomMapper.selectById(item.getClassroomId())).thenReturn(sourceRoom);
        when(timeSlotMapper.selectList(any())).thenReturn(List.of(slot));
        when(classroomMapper.selectList(any())).thenReturn(List.of(candidateRoom));
        when(schedulePlanItemMapper.selectList(any())).thenReturn(List.of(item));
        when(classInfoMapper.selectBatchIds(anyCollection())).thenReturn(List.of(classInfo));
        when(scheduleLockedItemMapper.selectList(any())).thenReturn(List.<ScheduleLockedItem>of());
        when(unavailableTimeService.isUnavailable(item.getTeacherId(), slot.getId())).thenReturn(false);
        return item;
    }

    private V5CandidatePositionGenerateRequest request(Long planItemId) {
        V5CandidatePositionGenerateRequest request = new V5CandidatePositionGenerateRequest();
        request.setPlanItemId(planItemId);
        request.setIncludeUnavailable(true);
        request.setLimit(10);
        return request;
    }

    /** 构造 peer item：同 teacher(40L)，落在候选时段 day=2 period 1-2，weekType 指定 */
    private SchedulePlanItem peer(String weekType) {
        SchedulePlanItem peer = new SchedulePlanItem();
        peer.setId(11L);
        peer.setPlanId(20L);
        peer.setClassId(30L);
        peer.setTeacherId(40L);
        peer.setClassroomId(50L);
        peer.setWeekday(2);
        peer.setStartPeriod(1);
        peer.setEndPeriod(2);
        peer.setWeekType(weekType);
        return peer;
    }

    /**
     * seedBaseData 的 weekType 变体：target item 设 weekType，候选时段 day=2 含一个 peer（同 teacher）。
     * 用于验证 weekType overlap 网关。
     */
    private SchedulePlanItem seedBaseDataWithPeer(Integer studentCount, Integer roomCapacity,
                                                   String targetWeekType, SchedulePlanItem peer) {
        SchedulePlan plan = new SchedulePlan();
        plan.setId(20L);
        plan.setSemesterId(1L);

        SchedulePlanItem item = new SchedulePlanItem();
        item.setId(10L);
        item.setPlanId(plan.getId());
        item.setSemesterId(plan.getSemesterId());
        item.setClassId(30L);
        item.setTeacherId(40L);
        item.setClassroomId(50L);
        item.setWeekday(1);
        item.setStartPeriod(1);
        item.setEndPeriod(2);
        item.setWeekType(targetWeekType);

        Classroom sourceRoom = new Classroom();
        sourceRoom.setId(50L);
        sourceRoom.setRoomName("原教室");
        sourceRoom.setCapacity(100);

        Classroom candidateRoom = new Classroom();
        candidateRoom.setId(60L);
        candidateRoom.setRoomName("候选教室");
        candidateRoom.setCapacity(roomCapacity);

        TimeSlot slot = new TimeSlot();
        slot.setId(70L);
        slot.setDayOfWeek(2);
        slot.setPeriodNo(1);

        ClassInfo classInfo = new ClassInfo();
        classInfo.setId(item.getClassId());
        classInfo.setStudentCount(studentCount);

        when(schedulePlanItemMapper.selectById(item.getId())).thenReturn(item);
        when(schedulePlanMapper.selectById(plan.getId())).thenReturn(plan);
        when(classroomMapper.selectById(item.getClassroomId())).thenReturn(sourceRoom);
        when(timeSlotMapper.selectList(any())).thenReturn(List.of(slot));
        when(classroomMapper.selectList(any())).thenReturn(List.of(candidateRoom));
        // peers 含 target + peer（都查出来供 fastHardCheck 对比）
        when(schedulePlanItemMapper.selectList(any())).thenReturn(List.of(item, peer));
        when(classInfoMapper.selectBatchIds(anyCollection())).thenReturn(List.of(classInfo));
        when(scheduleLockedItemMapper.selectList(any())).thenReturn(List.<ScheduleLockedItem>of());
        when(unavailableTimeService.isUnavailable(item.getTeacherId(), slot.getId())).thenReturn(false);
        return item;
    }
}
