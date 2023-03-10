package com.hmdp.controller;


import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_TTL;

/**
 * @author wzy
 * @title 商铺类型
 * @description
 * @updateTime 2023/2/22 13:38
 * @throws
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @GetMapping("list")
    public Result queryTypeList() {

        List<ShopType> typeList = new ArrayList<>();
        String key = CACHE_SHOP_TYPE_KEY + "list";
        // 1.判断Redis 缓存中是否有商铺信息
        String listJson = stringRedisTemplate.opsForValue().get(key);

        // 2.存在，直接返回
        if (StrUtil.isNotBlank(listJson)) {
            typeList = JSONUtil.toList(listJson, ShopType.class);
            return Result.ok(typeList);
        }

        // 3.不存在，从数据库中读取
        typeList = typeService
                .query().orderByAsc("sort").list();

        // 4.存入缓存
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList), CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);

        return Result.ok(typeList);
    }
}
