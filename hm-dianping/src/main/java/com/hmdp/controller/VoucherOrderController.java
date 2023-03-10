package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author wzy
 * @title 订单类
 * TODO 注意！！！
 *  为何订单ID需要使用全局唯一ID：
 *      1.自增ID的规律性太明显，容易暴露信息(比如一天的订单量为多少容易猜出)
 *      2.自增ID受单表数据量的限制 (后期分库分表会出现ID重复的情况)
 * @description
 * @updateTime 2023/2/24 16:23
 * @throws
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Autowired
    IVoucherOrderService voucherOrderService;

    /**
     * 重点：秒杀功能，抢购优惠券，生成订单
     * 涉及：秒杀
     *      全局唯一ID
     *      Lua脚本
     *      proxy 代理
     *      异步线程
     *      一人一单
     *
     * @param voucherId
     * @return
     */
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {

        Result result = voucherOrderService.seckillVoucher(voucherId);
        return result;
    }
}
