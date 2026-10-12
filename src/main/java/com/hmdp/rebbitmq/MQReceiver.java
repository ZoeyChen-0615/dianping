package com.hmdp.rebbitmq;

import com.alibaba.fastjson.JSON;
import com.hmdp.config.RabbitMQTopicConfig;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 *  Message consumer
 */
@Slf4j
@Service
public class MQReceiver {

    @Resource
    IVoucherOrderService voucherOrderService;

    @Resource
    ISeckillVoucherService seckillVoucherService;
    /**
     * Receive flash-sale messages and place orders
     * @param msg
     */
    @Transactional
    @RabbitListener(queues = RabbitMQTopicConfig.QUEUE)
    public void receiveSeckillMessage(String msg){
        log.info("Received message: " + msg);
        VoucherOrder voucherOrder = JSON.parseObject(msg, VoucherOrder.class);

        Long voucherId = voucherOrder.getVoucherId();
        // 5. One order per user
        Long userId = voucherOrder.getUserId();
        // 5.1 Look up the order
        int count = voucherOrderService.query().eq("user_id",userId).eq("voucher_id", voucherId).count();
        // 5.2 Check whether it exists
        if(count>0){
            // The user has already purchased
            log.error("User already purchased");
            return ;
        }
        log.info("Decrementing stock");
        // 6. Decrement stock
        boolean success = seckillVoucherService
                .update()
                .setSql("stock = stock-1")
                .eq("voucher_id", voucherId)
                .gt("stock",0)// CAS optimistic lock
                .update();
        if(!success){
            log.error("Out of stock");
            return;
        }
        // Save the order directly
        voucherOrderService.save(voucherOrder);
    }

}
