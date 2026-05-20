package com.paike.scheduler.service.vo;

import lombok.Data;

@Data
public class V5ConsistencyIssueVo {
    /** 规则代码，例如 SEMESTER_MISMATCH / LOCKED_ITEM_MOVED */
    private String code;
    /** 严重级别：BLOCKING / WARNING / INFO */
    private String severity;
    /** 分类：LINEAGE / SCOPE / DATA / CONFLICT / RULE / PROCESS */
    private String category;
    /** 规则名称 */
    private String name;
    /** 问题描述 */
    private String message;
    /** 处理建议 */
    private String suggestion;
    /** 关联试算方案明细ID */
    private Long planItemId;
    /** 关联教学任务 */
    private Long teachingTaskId;
    /** 关联课程名称 */
    private String courseName;
    /** 关联教师名称 */
    private String teacherName;
    /** 关联班级名称 */
    private String className;
    /** 关联教室名称 */
    private String classroomName;
    /** 星期 */
    private Integer weekday;
    /** 开始节次 */
    private Integer startPeriod;
    /** 结束节次 */
    private Integer endPeriod;
}
