package com.paike.scheduler.service.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class V5ConsistencyCheckReportVo {
    /** 持久化记录ID，未持久化时为 null */
    private Long reportId;
    private Long taskId;
    private Long planId;
    private Long sourcePlanId;
    private Long semesterId;
    /** PASS / WARN / FAIL */
    private String status;
    /** 是否通过（无 BLOCKING） */
    private Boolean passed;
    /** 阻塞问题数 */
    private Integer blockingIssueCount;
    /** 警告问题数 */
    private Integer warningIssueCount;
    /** 提示问题数 */
    private Integer infoIssueCount;
    /** 摘要 */
    private String summary;
    /** 推荐操作 */
    private String recommendation;
    /** 问题清单 */
    private List<V5ConsistencyIssueVo> issues;
    /** 检查时间 */
    private LocalDateTime checkedAt;
}
