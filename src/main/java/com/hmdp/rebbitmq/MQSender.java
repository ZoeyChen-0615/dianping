package com.hmdp.rebbitmq;

import com.hmdp.config.RabbitMQTopicConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *  Message producer
 */
@Slf4j
@Service
public class MQSender {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final String ROUTINGKEY = "seckill.message";
    /**
     * Send flash-sale messages
     * @param msg
     */
    public void sendSeckillMessage(String msg){
        log.info("Sending message: " + msg);
        rabbitTemplate.convertAndSend(RabbitMQTopicConfig.EXCHANGE,ROUTINGKEY,msg);
    }
}
