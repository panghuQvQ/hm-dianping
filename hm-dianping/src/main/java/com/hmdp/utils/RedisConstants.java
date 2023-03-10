package com.hmdp.utils;

/**
 * @title 字符串常量池
 * @description
 * @author wzy
 * @updateTime 2023/2/24 15:28
 * @throws
 */
public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:"; // 用户登录验证码
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:"; // 登录用户的信息
    public static final Long LOGIN_USER_TTL = 30L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:"; // 缓存的商铺信息

    public static final Long CACHE_SHOP_TYPE_TTL = 30L;
    public static final String CACHE_SHOP_TYPE_KEY = "cache:shop:type:"; // 缓存的商铺类型

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:"; // 优惠券库存

    public static final String BLOG_LIKED_KEY = "blog:liked:"; // 博客点赞人员


    public static final String FOLLOWS_USERID_KEY = "follows:userId:"; // 用户关注集合，用户id

    public static final String FACE_USERID_KEY = "feed:userId:"; // 用户收件箱,feed流

    public static final String SHOP_GEO_KEY = "shop:geo:"; // 存储店铺的 geo 坐标


    public static final String USER_SIGN_KEY = "sign:"; // 用户签到key









}
