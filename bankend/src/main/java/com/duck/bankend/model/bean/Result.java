package com.duck.bankend.model.bean;

import com.duck.bankend.constant.HttpConst;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result {
    private Integer code;
    private String msg;
    private Object data;

    public Result(String msg, Object data) {
        this.msg = msg;
        this.data = data;
    }

    public Result(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public static Result success(String msg, Object data) {
        return new Result(HttpConst.SUCCESS, msg, data);
    }

    public static Result success() {
        return new Result(HttpConst.SUCCESS, "操作成功", null);
    }

    public static Result success(Object data) {
        return new Result(HttpConst.SUCCESS, "操作成功", data);
    }

    public static Result success(String msg) {
        return new Result(HttpConst.SUCCESS, msg);
    }

    public static Result insertSuccess() {
        return new Result(HttpConst.SUCCESS, "插入数据成功");
    }

    public static Result searchSuccess(Object data) {
        return new Result(HttpConst.SUCCESS, "查询成功", data);
    }

    public static Result updateSuccess() {
        return new Result(HttpConst.SUCCESS, "更新成功");
    }

    public static Result deleteSuccess() {
        return new Result(HttpConst.SUCCESS, "删除成功");
    }

    public static Result error(String msg, Object data) {
        return new Result(HttpConst.ERROR, msg, data);
    }

    public static Result error(String msg) {
        return new Result(HttpConst.ERROR, msg);
    }

    public static Result badRequest(String msg) {
        return new Result(HttpConst.BAD_REQUEST, msg);
    }

    public static Result badRequest(String msg, Object data) {
        return new Result(HttpConst.BAD_REQUEST, msg, data);
    }

    public static Result fail(String msg) {
        return new Result(HttpConst.BAD_REQUEST, msg);
    }

    public static Result updateFail() {
        return new Result(HttpConst.BAD_REQUEST, "更新失败");
    }

    public static Result notFound() {
        return new Result(HttpConst.NOT_FOUND, "未找到相关数据");
    }

    public static Result notFound(String msg) {
        return new Result(HttpConst.NOT_FOUND, msg);
    }

    public static Result unauthorized(String msg) {
        return new Result(HttpConst.UNAUTHORIZED, msg);
    }

    public static Result forbidden(String msg) {
        return new Result(HttpConst.FORBIDDEN, msg);
    }

    public static Result requestEntityTooLarge(String msg) {
        return new Result(HttpConst.REQUEST_ENTITY_TOO_LARGE, "传输文件过大 :" + msg);
    }

    public static Result unprocessableContent(String msg) {
        return new Result(HttpConst.UNPROCESSABLE_CONTENT, msg);
    }

    public static Result noContent(String msg) {
        return new Result(HttpConst.NO_CONTENT, msg);
    }

    public static Result notLogIn(String msg) {
        return new Result(HttpConst.UNAUTHORIZED, msg);
    }
}
