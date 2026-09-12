package com.daylog.common.result;

import lombok.Getter;

/**
 * 统一响应状态码
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或令牌已过期"),
    FORBIDDEN(403, "无权访问"),
    NOT_FOUND(404, "资源不存在"),
    BUSINESS_ERROR(500, "业务处理失败"),
    SYSTEM_ERROR(500, "系统内部错误"),

    // ---------- 业务错误码 1xxx：认证与用户 ----------
    USERNAME_EXISTS(1001, "用户名已存在"),
    USERNAME_OR_PASSWORD_ERROR(1004, "用户名或密码错误"),

    // ---------- 业务错误码 2xxx：日记与标签 ----------
    DIARY_ALREADY_EXISTS(1002, "当天已有日记，一天只能写一篇"),
    TAG_NAME_EXISTS(1003, "标签名已存在"),

    // ---------- 业务错误码 3xxx：文件与 AI ----------
    FILE_TYPE_NOT_ALLOWED(3001, "不支持的文件类型"),
    FILE_UPLOAD_FAILED(3002, "文件保存失败"),
    FILE_NOT_FOUND(3003, "文件不存在"),
    AI_GENERATE_FAILED(3004, "AI 生成失败，请稍后重试"),
    AI_NO_DIARY_THIS_WEEK(3005, "该周没有日记，无法生成周报");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
