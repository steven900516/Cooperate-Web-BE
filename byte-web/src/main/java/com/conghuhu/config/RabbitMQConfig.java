package com.conghuhu.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;


@Configuration
public class RabbitMQConfig {

    public final static String QUEUE_NAME = "byte.mail.delayQueue";
    public final static String EXCHANGE_NAME = "byte.mail.delayExc";

    @Bean
    public CustomExchange delayExchange() {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-delayed-type", "direct");
        return new CustomExchange(RabbitMQConfig.EXCHANGE_NAME, "x-delayed-message", true, false, args);
    }
    /**
     * 延迟消息队列
     * @return
     */
    @Bean
    public Queue delayQueue() {
        return new Queue(RabbitMQConfig.QUEUE_NAME, true);
    }

    /**
     * 绑定 消息队列至交换机
     * @return
     */
    @Bean
    public Binding deplyBinding() {
        return BindingBuilder.bind(delayQueue()).to(delayExchange()).with(RabbitMQConfig.QUEUE_NAME).noargs();
    }
}
