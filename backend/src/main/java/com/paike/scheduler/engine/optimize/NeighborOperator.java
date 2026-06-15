package com.paike.scheduler.engine.optimize;

import com.paike.scheduler.engine.conflict.InMemoryConflictDetector;
import com.paike.scheduler.engine.model.Assignment;
import com.paike.scheduler.engine.model.EngineContext;
import com.paike.scheduler.engine.model.EngineTask;
import com.paike.scheduler.service.WeekTypeSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * Generates feasible neighbors only. Locked and existing assignments live in the detector baseline.
 */
public final class NeighborOperator {

    private static final int MAX_ATTEMPTS = 120;

    private final EngineContext ctx;

    public NeighborOperator(EngineContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx must not be null");
    }

    public Optional<List<Assignment>> next(List<Assignment> current, Random random) {
        if (current == null || current.isEmpty()) {
            return Optional.empty();
        }
        // 构建 baseline detector 一次 (place existing+locked 在构造内, 再 place 全部 current),
        // 供 moveOne/swapTwo 增量 remove→check→place→rollback 复用, 省去每次 attempt 全量重建的 O(n) 成本。
        InMemoryConflictDetector baseline = new InMemoryConflictDetector(ctx);
        for (Assignment a : current) {
            baseline.place(a);
        }
        boolean move = current.size() < 2 || random.nextDouble() < 0.70D;
        return move ? moveOne(current, random, baseline) : swapTwo(current, random, baseline);
    }

    public boolean isFeasible(List<Assignment> assignments) {
        InMemoryConflictDetector detector = new InMemoryConflictDetector(ctx);
        for (Assignment assignment : assignments) {
            if (detector.check(assignment) != null) {
                return false;
            }
            detector.place(assignment);
        }
        return true;
    }

    private Optional<List<Assignment>> moveOne(List<Assignment> current, Random random, InMemoryConflictDetector baseline) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int index = random.nextInt(current.size());
            Assignment original = current.get(index);
            EngineTask task = ctx.tasks().get(original.taskIndex());
            if (task.candidateClassroomIndices().isEmpty()) {
                continue;
            }
            // V9 阶段3：按 task.weekType 选合法 slot。ODD→偶数 slot，EVEN→奇数 slot，ALL→偶数 slot（扩散）。
            // 用物理 slot（0..timeSlotCount/2-1）随机后映射到合法翻倍 slot，避免随机到错误周次。
            int physicalSlot = random.nextInt(ctx.timeSlotCount() / 2);
            String taskWt = task.weekType();
            int slotIndex = WeekTypeSupport.EVEN.equals(taskWt)
                    ? physicalSlot * 2 + 1
                    : physicalSlot * 2;
            int roomIndex = task.candidateClassroomIndices()
                    .get(random.nextInt(task.candidateClassroomIndices().size()));
            Assignment moved = new Assignment(original.taskIndex(), original.slotIndex(), slotIndex, roomIndex);
            if (moved.equals(original)) {
                continue;
            }
            // 增量冲突检测: 从 baseline 移除 original, 检查 moved 是否可放入 (与 current 其余元素无冲突),
            // 失败则 place 回 original 恢复 baseline。等价于全量 isFeasible(candidate) 但 O(1)/attempt 而非 O(n)。
            baseline.remove(original);
            if (baseline.check(moved) == null) {
                baseline.place(moved);
                List<Assignment> candidate = new ArrayList<>(current);
                candidate.set(index, moved);
                return Optional.of(candidate);
            }
            baseline.place(original);
        }
        return Optional.empty();
    }

    private Optional<List<Assignment>> swapTwo(List<Assignment> current, Random random, InMemoryConflictDetector baseline) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int left = random.nextInt(current.size());
            int right = random.nextInt(current.size());
            if (left == right) {
                continue;
            }
            Assignment a = current.get(left);
            Assignment b = current.get(right);
            // V9 阶段3：只 swap 同 weekType 分类的 task，避免跨周次 slot 交换导致语义错误
            String wtA = ctx.tasks().get(a.taskIndex()).weekType();
            String wtB = ctx.tasks().get(b.taskIndex()).weekType();
            boolean aAll = WeekTypeSupport.ALL.equals(wtA);
            boolean bAll = WeekTypeSupport.ALL.equals(wtB);
            // ALL 与非 ALL 不可 swap（slot 占用模型不同）；ODD 与 EVEN 不可 swap
            if (aAll != bAll) {
                continue;
            }
            if (!aAll && !wtA.equals(wtB)) {
                continue;
            }
            Optional<Assignment> movedA = chooseRoom(a, b.timeSlotIndex(), random);
            Optional<Assignment> movedB = chooseRoom(b, a.timeSlotIndex(), random);
            if (movedA.isEmpty() || movedB.isEmpty()) {
                continue;
            }
            Assignment newA = movedA.get();
            Assignment newB = movedB.get();
            // 增量: 移除 a, b, 检查 newA→newB 是否可放入 (newA 放 b 的 slot, newB 放 a 的 slot)。
            // 任一冲突则按逆序回滚到 baseline。等价于全量 isFeasible(candidate) 但 O(1)/attempt。
            baseline.remove(a);
            baseline.remove(b);
            if (baseline.check(newA) == null) {
                baseline.place(newA);
                if (baseline.check(newB) == null) {
                    baseline.place(newB);
                    List<Assignment> candidate = new ArrayList<>(current);
                    candidate.set(left, newA);
                    candidate.set(right, newB);
                    return Optional.of(candidate);
                }
                baseline.remove(newA);
            }
            baseline.place(a);
            baseline.place(b);
        }
        return Optional.empty();
    }

    private Optional<Assignment> chooseRoom(Assignment original, int newSlotIndex, Random random) {
        EngineTask task = ctx.tasks().get(original.taskIndex());
        if (task.candidateClassroomIndices().isEmpty()) {
            return Optional.empty();
        }
        int roomIndex = task.candidateClassroomIndices()
                .get(random.nextInt(task.candidateClassroomIndices().size()));
        return Optional.of(new Assignment(original.taskIndex(), original.slotIndex(), newSlotIndex, roomIndex));
    }
}
