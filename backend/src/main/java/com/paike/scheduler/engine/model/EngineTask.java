package com.paike.scheduler.engine.model;

import java.util.List;

/**
 * 引擎内部的教学任务表示，包含派生数据。
 *
 * @param weekType V9 阶段3：周次类型 ALL/ODD/EVEN，决定该任务可占用的翻倍 slot 子集
 *                 （ODD→只能占 ODD slot，EVEN→EVEN slot，ALL→两者皆可）
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
    List<Integer> candidateClassroomIndices,
    String weekType
) {}
