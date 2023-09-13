package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData {

    // 万能存储数据类，用来增加过期时间字段
    private LocalDateTime expireTime;
    private Object data;

    public RedisData RedisData() {
        return new RedisData();
    }
}
