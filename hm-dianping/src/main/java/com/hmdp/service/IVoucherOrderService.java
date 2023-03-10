package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;

/**
 * @author admin
 * @version 1.0.0
 * @ClassName IVoucherOrderService.java
 * @Description TODO
 * @createTime 2023年02月24日 16:40:00
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    // 秒杀代金券
    Result seckillVoucher(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);
//    Result createVoucherOrder(Long voucherId);
}
