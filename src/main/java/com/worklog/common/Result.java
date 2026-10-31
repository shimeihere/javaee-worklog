package com.worklog.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 全局统一返回体。
 *
 * <p>约定：HTTP 状态码恒为 200，业务结果由 {@code code} 表达；前端 axios 拦截器只需判断
 * {@code code === 200} 与 {@code code === 401/4002}（跳登录）两种情况。</p>
 *
 * <p>成功：{@code {"code":200,"message":"操作成功","data":{...}}}；
 * 失败：{@code {"code":4001,"message":"账号或密码错误","data":null}}。</p>
 *
 * @param <T> 业务数据类型
 */
@Data
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务状态码，见 {@link ResultCode} */
    private int code;

    /** 提示信息，可直接用于前端 ElMessage */
    private String message;

    /** 业务数据 */
    private T data;

    private Result() {
    }

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功，无返回数据 */
    public static <T> Result<T> ok() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /** 成功，带返回数据 */
    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /** 成功，自定义提示语 */
    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    /** 失败，使用标准错误码文案 */
    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /** 失败，使用标准错误码但覆盖提示语 */
    public static <T> Result<T> fail(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }

    /** 失败，完全自定义 */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    /** 便于业务代码判断（不参与 JSON 序列化） */
    @JsonIgnore
    public boolean isSuccess() {
        return this.code == ResultCode.SUCCESS.getCode();
    }
}
