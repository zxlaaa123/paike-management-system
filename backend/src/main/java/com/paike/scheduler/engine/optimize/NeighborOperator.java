package com.paike.scheduler.engine.optimize;

import com.paike.scheduler.engine.conflict.InMemoryConflictDetector;
import com.paike.scheduler.engine.model.Assignment;
import com.paike.scheduler.engine.model.EngineContext;
import com.paike.scheduler.engine.model.EngineTask;

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
        boolean move = current.size() < 2 || random.nextDouble() < 0.70D;
        return move ? moveOne(current, random) : swapTwo(current, random);
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

    private Optional<List<Assignment>> moveOne(List<Assignment> current, Random random) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int index = random.nextInt(current.size());
            Assignment original = current.get(index);
            EngineTask task = ctx.tasks().get(original.taskIndex());
            if (task.candidateClassroomIndices().isEmpty()) {
                continue;
            }
            int slotIndex = random.nextInt(ctx.timeSlotCount());
            int roomIndex = task.candidateClassroomIndices()
                    .get(random.nextInt(task.candidateClassroomIndices().size()));
            Assignment moved = new Assignment(original.taskIndex(), original.slotIndex(), slotIndex, roomIndex);
            if (moved.equals(original)) {
                continue;
            }
            List<Assignment> candidate = new ArrayList<>(current);
            candidate.set(index, moved);
            if (isFeasible(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private Optional<List<Assignment>> swapTwo(List<Assignment> current, Random random) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int left = random.nextInt(current.size());
            int right = random.nextInt(current.size());
            if (left == right) {
                continue;
            }
            Assignment a = current.get(left);
            Assignment b = current.get(right);
            Optional<Assignment> movedA = chooseRoom(a, b.timeSlotIndex(), random);
            Optional<Assignment> movedB = chooseRoom(b, a.timeSlotIndex(), random);
            if (movedA.isEmpty() || movedB.isEmpty()) {
                continue;
            }
            List<Assignment> candidate = new ArrayList<>(current);
            candidate.set(left, movedA.get());
            candidate.set(right, movedB.get());
            if (isFeasible(candidate)) {
                return Optional.of(candidate);
            }
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
