package com.hmdp.service.impl;

import com.alibaba.fastjson.JSON;
import com.google.common.util.concurrent.RateLimiter;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.rebbitmq.MQSender;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  Service implementation
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private MQSender mqSender;

    private RateLimiter rateLimiter=RateLimiter.create(10);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // Lua script
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        // Token-bucket rate limiting
        if (!rateLimiter.tryAcquire(1000, TimeUnit.MILLISECONDS)){
            return Result.fail("Server is busy, please retry");
        }
        // 1. Run the Lua script
        Long userId = UserHolder.getUser().getId();

        Long r = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        // 2. Result 0 means success
        int result = r.intValue();
        if (result != 0) {
            // 2.1 Non-zero means not eligible
            return Result.fail(r == 1 ? "Out of stock" : "You have already placed an order");
        }
        // 2.2 Eligible: queue the order info

        // 2.3 Build the order
        VoucherOrder voucherOrder = new VoucherOrder();
        // 2.4 Order ID
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 2.5 User ID
        voucherOrder.setUserId(userId);
        // 2.6 Voucher ID
        voucherOrder.setVoucherId(voucherId);

        // 2.7 Publish to MQ
        mqSender.sendSeckillMessage(JSON.toJSONString(voucherOrder));


        // 2.8 Return the order ID
        return Result.ok(orderId);
// Standalone mode: use synchronized as the lock
//        synchronized (userId.toString().intern())
//        {
// The transaction on createVoucherOrder would not take effect with a plain this.* call,
// so the transactional proxy must be obtained to invoke it
//            return voucherOrderService.createVoucherOrder(voucherId);
//        }
    }


//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
// One order per user
//        Long userId = UserHolder.getUser().getId();
//
//
//        int count = query().eq("voucher_id", voucherId).eq("user_id", userId).count();
//        if (count > 0){
//            return Result.fail("You have already grabbed this voucher");
//        }
//
// 5. Decrement stock
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock - 1")
//                .eq("voucher_id", voucherId)
//                .gt("stock",0)   // CAS optimistic lock (compare and swap)
//                .update();
//
//        if (!success) {
//            return Result.fail("Out of stock");
//        }
//
// Stock available and within the time window: create the order
// 6. Create the order
//        VoucherOrder voucherOrder = new VoucherOrder();
// 6.1 Set the order ID (globally unique)
//        long orderId = redisIdWorker.nextId("order");
// 6.2 Set the user ID
//        Long id = UserHolder.getUser().getId();
// 6.3 Set the voucher ID
//        voucherOrder.setVoucherId(voucherId);
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(id);
// 7. Persist the order
//        save(voucherOrder);
// 8. Return the order ID
//        return Result.ok(orderId);
//    }
}
