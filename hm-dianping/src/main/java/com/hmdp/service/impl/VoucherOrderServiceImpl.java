package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author admin
 * @version 1.0.0
 * @ClassName VoucherOrderServiceImpl.java
 * @Description TODO
 * @createTime 2023年02月24日 16:41:00
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient; // redisson 客户端

    private IVoucherOrderService proxy;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static { // 静态代码块，在类加载时就会初始化 lua脚本
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua")); // 设置脚本位置（ClassPath下的资源）
        SECKILL_SCRIPT.setResultType(Long.class); // 设置返回值类型
    }


    // Ctrl + Shift + U  大小写转换
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor(); // 线程池

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    @Value("${ConsumerGroup.consumer}")
    private String consumer;

    private class VoucherOrderHandler implements Runnable {

        String queueName = "stream.orders";
        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取消息队列中的订单信息 XREADGROUP GROUP consumerGroup consumer1 COUNT 1 BLOCK 2000 STREAMS stream.orders >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("consumerGroup", consumer),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed()) // > 从下一个未消费的消息开始
                    );
                    // 2.判断消息获取是否成功
                    if(CollectionUtil.isEmpty(list)){
                        // 2.1 如果获取失败，说明没有消息，继续下次循环
                        continue;
                    }
                    // 3. 解析信息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);

                    // 4. 如果获取成功，可以下单
                    handlerVoucherOrder(voucherOrder);
                    // 5. ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"consumerGroup",record.getId());

                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    handlerPendingList();
                }
            }
        }

        // 从 pending-list中读取消息
        private void handlerPendingList() {
            while (true) {
                try {
                    // 1.获取pending-list中的已消费但未确认的订单信息 XREADGROUP GROUP consumerGroup consumer1 COUNT 1 STREAMS stream.orders 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("consumerGroup", consumer),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0")) // 0：从pending-list中的第一个已消费但未确认的消息开始
                    );
                    // 2.判断消息获取是否成功
                    if(CollectionUtil.isEmpty(list)){
                        // 2.1 如果获取失败，说明pending-list没有异常消息，结束循环
                        break;
                    }
                    // 3. 解析信息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);


                    // 4. 如果获取成功，可以下单
                    handlerVoucherOrder(voucherOrder);
                    // 5. ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"consumerGroup",record.getId());

                } catch (Exception e) {
                    log.error("处理pending-list异常", e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

/*    // 搭配第二个版本，通过阻塞队列的方式实现消息推送与获取
    // 当线程尝试从 BlockingQueue中获取元素时，如果没有元素，该线程就会阻塞，直到队列中有元素才会唤醒线程，并获取元素
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();// 获取和删除队列的头部，如果需要则等待直到元素可用
                    // 2.创建订单
                    handlerVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }*/

    private void handlerVoucherOrder(VoucherOrder voucherOrder) {
        // 1.获取用户
        Long userId = voucherOrder.getUserId();
        // 2.创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 3.获取锁
        boolean isLock = lock.tryLock();
        // 4.判断是否获取锁成功
        if (!isLock) {
            log.error("不允许重复下单");
            return;
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            // 释放锁
            lock.unlock();
        }


    }

    @Override
    public Result seckillVoucher(Long voucherId) {

        // 获取用户
        Long userId = UserHolder.getUser().getId();
        // 使用全局唯一ID生成器生成ID
        long orderId = redisIdWorker.nextId("order");

        /**
         * 1.RedisTemplate调用lua脚本的API： execute(脚本, keys[],argv[])
         * 内部判断：库存是否充足，是否符合一人一单
         */
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(),String.valueOf(orderId));
        // 2.判断结果是否为0
        int re = result.intValue();
        if (re != 0) {
            // 2.1 不为0，代表没有购买资格
            return Result.fail(re == 1 ? "库存不足" : "不能重复下单");
        }

        // 3. 获取代理对象(事务)
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        // 4.返回订单id
        return Result.ok(orderId);
    }

    // 第二个版本
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//
//        // 获取用户
//        Long userId = UserHolder.getUser().getId();
//
//        // 1.RedisTemplate调用lua脚本的API： execute(脚本, keys[],argv[])
//        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(), userId.toString());
//        // 2.判断结果是否为0
//        int re = result.intValue();
//        if (re != 0) {
//            // 2.1 不为0，代表没有购买资格
//            return Result.fail(re == 1 ? "库存不足" : "不能重复下单");
//        }
//
//
//        // 2.2 为0，有购买资格，把下单信息保存到阻塞队列中
//        VoucherOrder voucherOrder = new VoucherOrder();
//        // TODO 保存阻塞队列
//
//        // 使用全局唯一ID生成器生成ID
//        long orderId = redisIdWorker.nextId("order");
//
//        voucherOrder.setId(orderId);
//
//        // 2.3 用户ID
//        voucherOrder.setUserId(userId);
//        // 2.4 代金券ID
//        voucherOrder.setVoucherId(voucherId);
//        // 2.5 放入阻塞队列
//        orderTasks.add(voucherOrder);
//
//        // 3. 获取代理对象(事务)
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//
//        // 4.返回订单id
//        return Result.ok(orderId);
//
//    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {

        // 5. 一人一单
        Long userId = voucherOrder.getUserId();

        // 5.1 查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        // 5.2 判断是否存在
        if (count > 0) {
            // 用户已经购买过了
            log.error("用户已经购买过一次！");
            return;
        }


        /** 超卖问题，使用乐观锁 (不加锁，只在更新数据时去判断有没有其他线程做了修改)
         * 6.扣减库存,想象stock是原子类,假设库存只剩下1个,当A线程把库存-1,对应B线程判断的stock 是 0 > 0 B线程失败  所以数据不会超卖
         */
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0) // where id = ? and stock > 0
                .update();

  /*      // 使用 LambdaUpdateWrapper 实现
        seckillVoucherService.update(new LambdaUpdateWrapper<SeckillVoucher>()
                .set(SeckillVoucher::getStock, seckillVoucher.getStock() - 1)
                .eq(SeckillVoucher::getVoucherId, seckillVoucher.getVoucherId()));

        // 使用 UpdateWrapper 实现
        seckillVoucherService.update(new UpdateWrapper<SeckillVoucher>()
                .set("stock", seckillVoucher.getStock() - 1)
                .eq("voucher_id", seckillVoucher.getVoucherId())
        );
*/


        // 7.创建订单
        if (!success) {
            // 扣减失败
            log.error("库存不足");
            return;
        }

        save(voucherOrder);
    }




// ================= 最初版本==========================================================================================
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//
//        // 1.查询优惠券
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        // 2.判断秒杀是否开始
//        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始");
//        }
//        // 3.判断秒杀是否结束
//        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束");
//        }
//        // 4.判断库存是否充足
//        if (seckillVoucher.getStock() < 1) {
//            return Result.fail("库存不足");
//        }
//
//        // 5. 一人一单
//        Long userId = UserHolder.getUser().getId();
//
//        /**
//         * 单线程适用
//         * TODO 事务提交前释放锁，会锁不住。所以需要先提交事务，后释放锁
//         * 锁用户，从常量池中获取，保证只锁多次请求的同一用户，提高性能
//         * 即：同一用户串行，不同用户并行
//         */
////        synchronized (userId.toString().intern()){
////            // 获取代理对象（事务）
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////            return proxy.createVoucherOrder(voucherId);
////        }
//        // 创建锁对象
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        // 获取锁
//        boolean isLock = lock.tryLock();
//        if (!isLock) {
//            return Result.fail("不允许重复下单");
//        }
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            // 释放锁
//            lock.unlock();
//        }
//
//    }


//    /**
//     * 快速代码封装：Ctrl + Alt + m
//     * <p>
//     * 不在方法上加 synchronized 锁，是为了保证其他用户可以正常运行，只锁重复用户
//     *
//     * @param voucherId
//     * @return
//     */
//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//
//        // 5. 一人一单
//        Long userId = UserHolder.getUser().getId();
//
//        // 5.1 查询订单
//        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//        // 5.2 判断是否存在
//        if (count > 0) {
//            // 用户已经购买过了
//            return Result.fail("用户已经购买过一次！");
//        }
//
//
//        /** 超卖问题，使用乐观锁 (不加锁，只在更新数据时去判断有没有其他线程做了修改)
//         * 6.扣减库存,想象stock是原子类,假设库存只剩下1个,当A线程把库存-1,对应B线程判断的stock 是 0 > 0 B线程失败  所以数据不会超卖
//         */
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock - 1") // set stock = stock - 1
//                .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
//                .update();
//
//  /*      // 使用 LambdaUpdateWrapper 实现
//        seckillVoucherService.update(new LambdaUpdateWrapper<SeckillVoucher>()
//                .set(SeckillVoucher::getStock, seckillVoucher.getStock() - 1)
//                .eq(SeckillVoucher::getVoucherId, seckillVoucher.getVoucherId()));
//
//        // 使用 UpdateWrapper 实现
//        seckillVoucherService.update(new UpdateWrapper<SeckillVoucher>()
//                .set("stock", seckillVoucher.getStock() - 1)
//                .eq("voucher_id", seckillVoucher.getVoucherId())
//        );
//*/
//
//
//        // 7.创建订单
//        if (!success) {
//            // 扣减失败
//            return Result.fail("库存不足");
//        }
//        VoucherOrder voucherOrder = new VoucherOrder();
//        // 7.1 使用全局唯一ID生成器生成ID
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//
//        // 7.2 用户ID
//
//        voucherOrder.setUserId(userId);
//        // 7.3 代金券ID
//        voucherOrder.setVoucherId(voucherId);
//        save(voucherOrder);
//
//        // 8.返回订单ID
//        return Result.ok(orderId);
//    }
}
