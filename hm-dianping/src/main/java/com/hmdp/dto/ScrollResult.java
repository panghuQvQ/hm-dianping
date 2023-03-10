package com.hmdp.dto;

import lombok.Data;

import java.util.List;

/**
 * @author admin
 * @version 1.0.0
 * @ClassName ScrollResult.java
 * @Description TODO 滚动分页 返回值对象
 * @createTime 2023年03月07日 13:11:00
 */
@Data
public class ScrollResult {

    private List<?> list; // 小于指定时间戳的笔记集合
    private Long minTime; // 本次查询的推送的最小时间戳
    private Integer offset; // 偏移量
}
