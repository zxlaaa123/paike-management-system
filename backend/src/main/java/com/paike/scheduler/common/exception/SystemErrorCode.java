package com.paike.scheduler.common.exception;

import lombok.Getter;

@Getter
public enum SystemErrorCode {

    AUTH_UNAUTHORIZED("AUTH_UNAUTHORIZED", 401, "AUTH", 401,
            "未登录或登录已过期", "请重新登录后再操作。", "检查登录态、Token 或 Cookie。"),
    AUTH_FORBIDDEN("AUTH_FORBIDDEN", 403, "AUTH", 403,
            "无权限执行当前操作", "请确认账号权限或刷新页面后重试。", "检查权限配置和 CSRF 校验。"),
    AUTH_RATE_LIMITED("AUTH_RATE_LIMITED", 429, "AUTH", 429,
            "登录尝试过于频繁", "请稍后再试。", "检查登录失败次数和限流窗口。"),
    VALIDATION_ERROR("VALIDATION_ERROR", 400, "VALIDATION", 400,
            "参数校验失败", "请检查输入项。", "查看字段校验错误或请求参数格式。"),
    REQUEST_BODY_INVALID("REQUEST_BODY_INVALID", 400, "VALIDATION", 400,
            "请求体格式错误", "请检查提交内容格式。", "检查 JSON 格式和 Content-Type。"),
    BUSINESS_ERROR("BUSINESS_ERROR", 400, "BUSINESS", 400,
            "业务规则不允许当前操作", "请按页面提示调整后重试。", "查看业务异常消息和操作前置条件。"),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", 404, "BUSINESS", 404,
            "资源不存在", "请刷新列表后重试。", "检查目标 ID 是否存在或已被删除。"),
    CONFLICT_ERROR("CONFLICT_ERROR", 409, "CONFLICT", 409,
            "资源冲突", "请处理冲突后重试。", "检查唯一约束、排课冲突或并发修改。"),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", 405, "VALIDATION", 405,
            "请求方法不支持", "请刷新页面后重试。", "检查前端接口方法和后端路由。"),
    SYSTEM_ERROR("SYSTEM_ERROR", 500, "SYSTEM", 500,
            "系统异常，请联系管理员", "系统异常，请联系管理员。", "查看后端日志和异常堆栈。"),

    // ===== 排课冲突域（硬冲突，409）：手动/自动排课与一致性校验实际抛出的冲突类型 =====
    TEACHER_CONFLICT("TEACHER_CONFLICT", 409, "CONFLICT", 409,
            "教师时间冲突", "该教师在此时间段已有其他课程，请调整时间。", "检查同教师同时段是否已有排课。"),
    CLASS_CONFLICT("CLASS_CONFLICT", 409, "CONFLICT", 409,
            "班级时间冲突", "该班级在此时间段已有其他课程，请调整时间。", "检查同班级同时段是否已有排课。"),
    ROOM_CONFLICT("ROOM_CONFLICT", 409, "CONFLICT", 409,
            "教室时间冲突", "该教室在此时间段已被占用，请更换教室或时间。", "检查同教室同时段是否已有排课。"),
    TEACHER_HARD_CONFLICT("TEACHER_HARD_CONFLICT", 409, "CONFLICT", 409,
            "教师时间硬冲突", "试算方案中教师存在重叠课程，请调整。", "一致性校验命中教师重叠，需调整其中一节。"),
    CLASS_HARD_CONFLICT("CLASS_HARD_CONFLICT", 409, "CONFLICT", 409,
            "班级时间硬冲突", "试算方案中班级存在重叠课程，请调整。", "一致性校验命中班级重叠，需调整其中一节。"),
    CLASSROOM_HARD_CONFLICT("CLASSROOM_HARD_CONFLICT", 409, "CONFLICT", 409,
            "教室时间硬冲突", "试算方案中教室存在重叠课程，请调整。", "一致性校验命中教室重叠，需调整其中一节。"),
    DUPLICATE_TEACHING_TASK("DUPLICATE_TEACHING_TASK", 409, "CONFLICT", 409,
            "教学任务在同一时段重复排入", "同一教学任务在同一时段出现多条记录。", "检查复制/移动逻辑，确保同时段仅一条明细。"),

    // ===== 容量与房型域（400）=====
    CLASSROOM_CAPACITY_NOT_ENOUGH("CLASSROOM_CAPACITY_NOT_ENOUGH", 400, "CAPACITY", 400,
            "教室容量不足", "所选教室容量小于班级人数，请更换更大的教室。", "比较班级人数与教室容量。"),
    CLASSROOM_CAPACITY_OVERFLOW("CLASSROOM_CAPACITY_OVERFLOW", 400, "CAPACITY", 400,
            "教室容量超限", "试算方案中教室容量不足以容纳班级。", "一致性校验命中容量超限，需更换教室。"),
    ROOM_TYPE_MISMATCH("ROOM_TYPE_MISMATCH", 400, "CAPACITY", 400,
            "教室类型不匹配", "课程要求的教室类型与所选教室不符。", "实验课需实验室、机房课需机房，检查房型。"),
    CLASSROOM_TYPE_MISMATCH("CLASSROOM_TYPE_MISMATCH", 400, "CAPACITY", 400,
            "教室类型不匹配", "试算方案中教室类型与课程要求不符。", "一致性校验命中房型不匹配，需更换教室。"),
    // ===== 规则约束域（400）：禁排时间、每日上限、同课同天 =====
    TEACHER_UNAVAILABLE("TEACHER_UNAVAILABLE", 400, "RULE", 400,
            "教师禁排时间", "该时间段为教师禁排时间，请调整。", "检查教师禁排时间设置。"),
    TEACHER_UNAVAILABLE_HIT("TEACHER_UNAVAILABLE_HIT", 400, "RULE", 400,
            "教师禁排时间命中", "试算方案排入了教师禁排时间。", "一致性校验命中禁排时间，需调整。"),
    TEACHER_DAILY_LIMIT("TEACHER_DAILY_LIMIT", 400, "RULE", 400,
            "教师当日课时超限", "该教师当日课时已达上限，请调整。", "检查教师每日最大课时规则。"),
    CLASS_DAILY_LIMIT("CLASS_DAILY_LIMIT", 400, "RULE", 400,
            "班级当日课时超限", "该班级当日课时已达上限，请调整。", "检查班级每日最大课时规则。"),
    SAME_COURSE_SAME_DAY("SAME_COURSE_SAME_DAY", 400, "RULE", 400,
            "同一课程同天重复", "同一课程同一天已排课，建议分散。", "检查同课分散规则。"),

    // ===== 资源状态域（400/404）：资源不存在或被停用、任务未排满 =====
    TASK_NOT_FOUND("TASK_NOT_FOUND", 404, "RESOURCE", 404,
            "教学任务不存在", "所选教学任务不存在或已删除，请刷新。", "检查 teachingTaskId 是否有效。"),
    TIME_SLOT_NOT_FOUND("TIME_SLOT_NOT_FOUND", 404, "RESOURCE", 404,
            "时间段不存在", "所选时间段不存在，请刷新后重试。", "检查 timeSlotId 是否有效。"),
    CLASSROOM_NOT_FOUND("CLASSROOM_NOT_FOUND", 404, "RESOURCE", 404,
            "教室不存在", "所选教室不存在或已删除，请刷新。", "检查 classroomId 是否有效。"),
    TEACHER_DISABLED("TEACHER_DISABLED", 400, "RESOURCE", 400,
            "教师已停用", "该教师已停用，不能排课。", "检查教师启用状态。"),
    CLASS_DISABLED("CLASS_DISABLED", 400, "RESOURCE", 400,
            "班级已停用", "该班级已停用，不能排课。", "检查班级启用状态。"),
    CLASSROOM_DISABLED("CLASSROOM_DISABLED", 400, "RESOURCE", 400,
            "教室已停用", "该教室已停用，不能排课。", "检查教室启用状态。"),
    TASK_NOT_FULLY_SCHEDULED("TASK_NOT_FULLY_SCHEDULED", 400, "RESOURCE", 400,
            "教学任务未排满", "该教学任务的课时尚未全部排入。", "检查任务周课时与已排课时是否一致。"),
    // ===== 一致性校验域（400）：试算方案 apply 前的血缘/流程/范围/数据校验 =====
    SEMESTER_MISMATCH("SEMESTER_MISMATCH", 400, "CONSISTENCY", 400,
            "试算方案学期不一致", "试算方案与原方案学期不一致，请放弃重建。", "检查试算方案是否串学期。"),
    SEMESTER_TASK_MISMATCH("SEMESTER_TASK_MISMATCH", 400, "CONSISTENCY", 400,
            "试算方案与任务学期不一致", "试算方案与修复任务学期不一致，请重建。", "检查任务与方案学期是否一致。"),
    MISSING_REPAIR_TASK_REF("MISSING_REPAIR_TASK_REF", 400, "CONSISTENCY", 400,
            "试算方案缺少修复任务引用", "试算方案未绑定修复任务，请重建。", "检查 repairTaskId 是否写入。"),
    REPAIR_TASK_MISMATCH("REPAIR_TASK_MISMATCH", 400, "CONSISTENCY", 400,
            "试算方案归属不一致", "试算方案不属于当前修复任务。", "用正确的修复任务详情页打开方案。"),
    MISSING_SOURCE_PLAN("MISSING_SOURCE_PLAN", 400, "CONSISTENCY", 400,
            "试算方案缺少原方案引用", "试算方案未引用原方案，请重建。", "检查 sourcePlanId 是否写入。"),
    SOURCE_PLAN_NOT_FOUND("SOURCE_PLAN_NOT_FOUND", 404, "CONSISTENCY", 404,
            "原方案不存在", "试算方案引用的原方案不存在，可能已删除。", "检查原方案是否被删除。"),
    SOURCE_SCHEDULE_MISMATCH("SOURCE_SCHEDULE_MISMATCH", 400, "CONSISTENCY", 400,
            "正式课表来源不一致", "试算方案与任务的正式课表来源不一致。", "确认是否串了正式课表来源。"),
    INVALID_SIMULATION_STATUS("INVALID_SIMULATION_STATUS", 400, "CONSISTENCY", 400,
            "方案状态非试算", "只能基于试算方案进行校验或应用。", "检查方案状态是否为 SIMULATION/CONFIRMED。"),
    INVALID_PLAN_MODE("INVALID_PLAN_MODE", 400, "CONSISTENCY", 400,
            "方案模式非试算", "该方案模式非 SIMULATION。", "确认该方案是否为试算方案。"),
    LOCKED_ITEM_MISSING("LOCKED_ITEM_MISSING", 400, "CONSISTENCY", 400,
            "锁定课程缺失", "锁定课程在试算方案中未找到，请重建。", "确保锁定课程被完整复制。"),
    LOCKED_ITEM_MOVED("LOCKED_ITEM_MOVED", 400, "CONSISTENCY", 400,
            "锁定课程未被保护", "锁定课程发生了移动，请重建保留锁定。", "检查锁定课程保护链路。"),
    OUT_OF_SCOPE_CHANGE("OUT_OF_SCOPE_CHANGE", 400, "CONSISTENCY", 400,
            "课程变动超出修复范围", "有课程在任务范围外发生移动。", "检查局部重排范围设置。"),
    ITEM_FIELD_MISSING("ITEM_FIELD_MISSING", 400, "CONSISTENCY", 400,
            "课程明细字段缺失", "试算方案明细缺少必要字段。", "检查试算方案明细生成逻辑。"),
    FORMAL_SCHEDULE_OVERWRITE("FORMAL_SCHEDULE_OVERWRITE", 409, "CONSISTENCY", 409,
            "正式课表将被覆盖", "应用该方案会覆盖正式课表，需人工确认。", "检查 apply 前的覆盖保护。"),

    // ===== 并发编辑域（409）：乐观锁版本冲突，丢失更新保护 =====
    CONCURRENT_MODIFIED("CONCURRENT_MODIFIED", 409, "CONFLICT", 409,
            "数据已被他人修改，请刷新后重试", "数据已被他人修改，请刷新后重试。", "乐观锁版本不一致：客户端提交的 version 落后于库内最新值。"),
    ;

    private final String code;
    private final Integer numericCode;
    private final String category;
    private final Integer httpStatus;
    private final String defaultMessage;
    private final String frontendPrompt;
    private final String handlingSuggestion;

    SystemErrorCode(String code, Integer numericCode, String category, Integer httpStatus,
                    String defaultMessage, String frontendPrompt, String handlingSuggestion) {
        this.code = code;
        this.numericCode = numericCode;
        this.category = category;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
        this.frontendPrompt = frontendPrompt;
        this.handlingSuggestion = handlingSuggestion;
    }
}
