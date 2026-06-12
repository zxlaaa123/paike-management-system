package com.paike.scheduler.engine.model;

/**
 * 一次排课分配：某个教学任务的第 slotIndex 个大节，安排在 timeSlotId 时段、classroomId 教室。
 * 全部使用稠密 int 索引（0..n-1），输出时再映射回 Long ID。
 */
public record Assignment(
    int taskIndex,
    int slotIndex,
    int timeSlotIndex,
    int classroomIndex
) {}
