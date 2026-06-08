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
            "系统异常，请联系管理员", "系统异常，请联系管理员。", "查看后端日志和异常堆栈。");

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
