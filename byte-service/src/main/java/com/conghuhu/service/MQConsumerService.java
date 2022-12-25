package com.conghuhu.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.conghuhu.entity.MailMsg;
import com.conghuhu.utils.RedisUtil;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;



@Component
@RabbitListener(queues = "byte.mail.delayQueue")
@Slf4j
public class MQConsumerService {

    @Autowired
    private MailService mailService;


    @Autowired
    private RedisUtil redisUtil;

    @RabbitHandler
    public void onMessage(byte[] message,
                          @Headers Map<String, Object> headers,
                          Channel channel) {

        // 1. 查询消息中的dead_line_time 与 redis中的是否相等
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        JSONObject jsonObject = JSON.parseObject(new String(message));


        String start_time_list = jsonObject.getString("start_time_list");
        Long user_id = jsonObject.getLong("user_id");
        Long card_id = jsonObject.getLong("card_id");
        String product_name = jsonObject.getString("product_name");
        String card_name = jsonObject.getString("card_name");
        Boolean is_reach_three = jsonObject.getBoolean("is_reach_three");
        String email = jsonObject.getString("email");
        MailMsg msg = new MailMsg(user_id, card_id, email, product_name, card_name, is_reach_three, start_time_list);

        String key = msg.getCardID() + "-C";
        String value = (String)redisUtil.get(key);
        if (value != null && value.equals(msg.getStartTimeList())){
            mailService.sendNoticeMail(msg);
            log.info(sdf.format(new Date())+"处理完消息:" + new String(message));
        }else {
            log.warn(sdf.format(new Date())+"截止时间被清除或者是被修改！！" + new String(message));
        }
    }
}
