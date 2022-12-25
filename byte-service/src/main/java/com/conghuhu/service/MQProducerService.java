package com.conghuhu.service;



import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MQProducerService {

    @Autowired
    private RabbitTemplate rabbitTemplate;




    public void send(String msg, int delayTime) {
        log.info("msg= " + msg + ".delayTime" + delayTime);
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setDelay(delayTime);
        Message message = new Message(msg.getBytes(), messageProperties);
        rabbitTemplate.send("byte.mail.delayExc", "byte.mail.delayQueue", message);
    }
}
