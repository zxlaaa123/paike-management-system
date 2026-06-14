package com.paike.scheduler.engine.solver;

import com.paike.scheduler.engine.conflict.InMemoryConflictDetector;
import com.paike.scheduler.engine.model.Assignment;
import com.paike.scheduler.engine.model.EngineContext;
import com.paike.scheduler.engine.model.EngineSolution;
import com.paike.scheduler.engine.model.EngineTask;
import com.paike.scheduler.service.WeekTypeSupport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * V8 阶段 2 回溯求解器。
 *
 * <h2>算法</h2>
 * <ol>
 *   <li>把每个任务拆成 (requiredSlots - lockedOrExisting) 个"待排大节"。锁定 + 已有 schedule 已在
 *       {@link InMemoryConflictDetector} 构造时 placeInternal，回溯器只排"还没排的"部分。</li>
 *   <li>变量排序 MRV：剩余候选 (slot, room) 数最少的待排大节先排；并列时按任务原始索引稳定排序。</li>
 *   <li>值排序：候选按"day 散度（已有同任务同 day 数）+ 教室利用率接近 1 + 时段/教室索引"启发。
 *       不可行候选（detector.check 非 null）一律丢弃。</li>
 *   <li>place 第一个候选；无可行候选则回溯弹栈换下一候选。</li>
 *   <li>回溯次数超过 maxBacktracks 或耗时超 feasibleTimeBudgetMs：剩余大节按贪心 first-fit 收尾。</li>
 * </ol>
 *
 * <h2>纯度</h2>
 * 不引用 Spring / Mapper / System.currentTimeMillis()。
 * 阶段 2 只做确定性回溯；阶段 3 优化器再使用 {@link SolverConfig} 的种子随机源。
 */
public class BacktrackingSolver {

    private final EngineContext ctx;
    private final InMemoryConflictDetector detector;
    private final SolverConfig config;

    private long startedNanos;
    private int backtracks;

    public BacktrackingSolver(EngineContext ctx, SolverConfig config) {
        this.ctx = ctx;
        this.detector = new InMemoryConflictDetector(ctx);
        this.config = config;
    }

    public EngineSolution solve() {
        startedNanos = System.nanoTime();
        backtracks = 0;

        List<PendingSlot> pending = buildPendingSlots();
        if (pending.isEmpty()) {
            return new EngineSolution(new ArrayList<>(), new ArrayList<>(), EngineSolution.SolverStats.feasibleOnly(backtracks));
        }

        List<Assignment> placed = new ArrayList<>();
        backtrackFromIndex(0, pending, placed);

        // 超预算后剩余按贪心 first-fit 收尾（detector 已含 locked + existing + 已 place 的）
        if (config.greedyFallback() && hasUnplaced(pending, placed)) {
            greedyFill(pending, placed);
        }

        // 收集未排：扫一遍 pending 比对 placed
        List<EngineSolution.UnassignedSlot> unassigned = new ArrayList<>();
        int[] placedCountByTask = new int[ctx.taskCount()];
        for (Assignment a : placed) {
            placedCountByTask[a.taskIndex()]++;
        }
        for (PendingSlot p : pending) {
            if (placedCountByTask[p.taskIndex] == 0 || !containsSlot(placed, p)) {
                unassigned.add(new EngineSolution.UnassignedSlot(p.taskIndex, p.slotIndex, p.reasonType));
            }
        }

        return new EngineSolution(placed, unassigned, EngineSolution.SolverStats.feasibleOnly(backtracks));
    }

    public int backtracksUsed() {
        return backtracks;
    }

    public long elapsedMillis() {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    // ----- 内部 -----

    private List<PendingSlot> buildPendingSlots() {
        // 计算每个任务"locked + existing" 已占的大节数
        int[] preOccupied = new int[ctx.taskCount()];
        for (Assignment locked : ctx.lockedAssignments()) {
            preOccupied[locked.taskIndex()]++;
        }
        for (Assignment existing : ctx.existingScheduleAssignments()) {
            preOccupied[existing.taskIndex()]++;
        }

        List<PendingSlot> pending = new ArrayList<>();
        for (int t = 0; t < ctx.taskCount(); t++) {
            EngineTask task = ctx.tasks().get(t);
            int required = task.requiredSlots();
            int already = Math.min(preOccupied[t], required);
            int remaining = required - already;
            for (int k = 0; k < remaining; k++) {
                pending.add(new PendingSlot(t, k, "NO_AVAILABLE_SLOT"));
            }
        }
        return pending;
    }

    private boolean backtrackFromIndex(int startIdx, List<PendingSlot> pending, List<Assignment> placed) {
        if (startIdx >= pending.size()) {
            return true;
        }
        if (backtracks >= config.maxBacktracks() || elapsedMillis() > config.feasibleTimeBudgetMs()) {
            return false;
        }

        // MRV 重排：找剩余候选数最少的 pending[startIdx..]
        int mrvIdx = findMrvIndex(startIdx, pending, placed);
        if (mrvIdx != startIdx) {
            swap(pending, startIdx, mrvIdx);
        }

        PendingSlot slot = pending.get(startIdx);
        List<int[]> candidates = listFeasibleCandidates(slot.taskIndex, placed);
        if (candidates.isEmpty()) {
            slot.reasonType = "NO_AVAILABLE_SLOT";
            return false;
        }
        // 启发排序
        candidates.sort(candidateComparator(slot.taskIndex, placed));

        for (int[] cand : candidates) {
            Assignment a = new Assignment(slot.taskIndex, slot.slotIndex, cand[0], cand[1]);
            String conflict = detector.check(a);
            if (conflict != null) {
                continue;
            }
            detector.place(a);
            placed.add(a);
            if (backtrackFromIndex(startIdx + 1, pending, placed)) {
                return true;
            }
            // 回溯
            placed.remove(placed.size() - 1);
            detector.remove(a);
            backtracks++;
            if (backtracks >= config.maxBacktracks() || elapsedMillis() > config.feasibleTimeBudgetMs()) {
                slot.reasonType = "BACKTRACK_BUDGET_EXCEEDED";
                return false;
            }
        }
        slot.reasonType = "NO_AVAILABLE_SLOT";
        return false;
    }

    private int findMrvIndex(int startIdx, List<PendingSlot> pending, List<Assignment> placed) {
        int bestIdx = startIdx;
        int bestCount = Integer.MAX_VALUE;
        for (int i = startIdx; i < pending.size(); i++) {
            int n = listFeasibleCandidates(pending.get(i).taskIndex, placed).size();
            if (n < bestCount) {
                bestCount = n;
                bestIdx = i;
                if (n == 0) {
                    return i; // 0 候选无可救药，立刻选
                }
            }
        }
        return bestIdx;
    }

    private List<int[]> listFeasibleCandidates(int taskIndex, List<Assignment> placed) {
        EngineTask task = ctx.tasks().get(taskIndex);
        String taskWt = task.weekType();
        List<int[]> result = new ArrayList<>();
        for (int s = 0; s < ctx.timeSlotCount(); s++) {
            // V9 阶段3：按 task.weekType 过滤 slot。ODD task 只进 ODD slot，EVEN 只进 EVEN，
            // ALL 进 ODD slot（Detector place 自动扩散到 EVEN，保证与 ODD/EVEN 都冲突）。
            String slotWt = ctx.timeSlots().get(s).weekType();
            boolean slotOk = WeekTypeSupport.ALL.equals(taskWt)
                    ? WeekTypeSupport.ODD.equals(slotWt)
                    : taskWt.equals(slotWt);
            if (!slotOk) {
                continue;
            }
            for (int r : task.candidateClassroomIndices()) {
                Assignment probe = new Assignment(taskIndex, 0, s, r);
                if (detector.check(probe) == null) {
                    result.add(new int[]{s, r});
                }
            }
        }
        return result;
    }

    /**
     * 候选排序启发：先 day 散度（少占的天优先，可拉开日排课），再教室利用率（学生数 / 容量 接近 1 优先），
     * 最后按时段、教室索引稳定排序，保证比较器传递性与可复现。
     */
    private Comparator<int[]> candidateComparator(int taskIndex, List<Assignment> placed) {
        EngineTask task = ctx.tasks().get(taskIndex);
        int[] dayCount = new int[8];
        for (Assignment a : placed) {
            if (a.taskIndex() == taskIndex) {
                int day = ctx.timeSlots().get(a.timeSlotIndex()).dayOfWeek();
                dayCount[day]++;
            }
        }
        int[] roomCapacity = new int[ctx.classroomCount()];
        for (int i = 0; i < ctx.classroomCount(); i++) {
            Integer cap = ctx.classrooms().get(i).capacity();
            roomCapacity[i] = cap != null && cap > 0 ? cap : 0;
        }
        int studentCount = task.studentCount();
        return (a, b) -> {
            int dayA = ctx.timeSlots().get(a[0]).dayOfWeek();
            int dayB = ctx.timeSlots().get(b[0]).dayOfWeek();
            int dayCmp = Integer.compare(dayCount[dayA], dayCount[dayB]);
            if (dayCmp != 0) return dayCmp;
            int utilA = utilizationScore(roomCapacity[a[1]], studentCount);
            int utilB = utilizationScore(roomCapacity[b[1]], studentCount);
            int utilCmp = Integer.compare(utilB, utilA); // 越接近 1 越优
            if (utilCmp != 0) return utilCmp;
            int slotCmp = Integer.compare(a[0], b[0]);
            if (slotCmp != 0) return slotCmp;
            return Integer.compare(a[1], b[1]);
        };
    }

    private static int utilizationScore(int capacity, int studentCount) {
        if (capacity <= 0 || studentCount < 0) {
            return 0;
        }
        int diff = Math.abs(capacity - studentCount);
        return 10_000 - Math.min(diff, 10_000);
    }

    private boolean hasUnplaced(List<PendingSlot> pending, List<Assignment> placed) {
        if (pending.size() > placed.size()) {
            return true;
        }
        // pending.size == placed.size 但可能某任务全失败（不会发生但兜底）
        return false;
    }

    private void greedyFill(List<PendingSlot> pending, List<Assignment> placed) {
        for (PendingSlot slot : pending) {
            if (containsSlot(placed, slot)) {
                continue;
            }
            List<int[]> candidates = listFeasibleCandidates(slot.taskIndex, placed);
            if (candidates.isEmpty()) {
                slot.reasonType = "NO_AVAILABLE_SLOT";
                continue;
            }
            for (int[] cand : candidates) {
                Assignment a = new Assignment(slot.taskIndex, slot.slotIndex, cand[0], cand[1]);
                if (detector.check(a) != null) {
                    continue;
                }
                detector.place(a);
                placed.add(a);
                break;
            }
            if (!containsSlot(placed, slot)) {
                slot.reasonType = "NO_AVAILABLE_SLOT";
            }
        }
    }

    private static boolean containsSlot(List<Assignment> placed, PendingSlot slot) {
        for (Assignment a : placed) {
            if (a.taskIndex() == slot.taskIndex && a.slotIndex() == slot.slotIndex) {
                return true;
            }
        }
        return false;
    }

    private static <T> void swap(List<T> list, int i, int j) {
        if (i == j) return;
        T tmp = list.get(i);
        list.set(i, list.get(j));
        list.set(j, tmp);
    }

    /** 待排大节：含失败原因，便于回溯失败时回写。 */
    private static final class PendingSlot {
        final int taskIndex;
        final int slotIndex;
        String reasonType;

        PendingSlot(int taskIndex, int slotIndex, String reasonType) {
            this.taskIndex = taskIndex;
            this.slotIndex = slotIndex;
            this.reasonType = reasonType;
        }

        @Override
        public String toString() {
            return "PendingSlot{t=" + taskIndex + ",k=" + slotIndex + ",reason=" + reasonType + "}";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PendingSlot that)) return false;
            return taskIndex == that.taskIndex && slotIndex == that.slotIndex;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(new int[]{taskIndex, slotIndex});
        }
    }
}
