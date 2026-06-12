package com.paike.scheduler.engine.model;

import java.util.List;

/**
 * 引擎内部的教学任务表示，包含派生数据。
 */
public record EngineTask(
    int index,
    long originalId,
    long teacherIndex,
    long classIndex,
    long courseIndex,
    int requiredSlots,
    String courseType,
    int studentCount,
    List<Integer> candidateClassroomIndices
) {}
