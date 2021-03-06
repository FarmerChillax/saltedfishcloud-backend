package com.xiaotao.saltedfishcloud.exception;

import com.xiaotao.saltedfishcloud.po.JsonResult;
import lombok.Getter;

public class JsonException extends RuntimeException {
    private static final long serialVersionUID = -6859013370470905290L;

    @Getter
    private final JsonResult res;

    public JsonException(String message) {
        super(message);
        res = JsonResult.getInstance(500, null, message);
    }

    public JsonException(JsonResult jsonResult) {
        this.res = jsonResult;
    }

    public JsonException(Integer code, String msg) {
        res = JsonResult.getInstance(code, null, msg);
    }

    public JsonException() {
        res = JsonResult.getInstance(500, null, "服务器其他错误");
    }
}
