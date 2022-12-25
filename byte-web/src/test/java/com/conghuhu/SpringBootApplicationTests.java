package com.conghuhu;

import com.conghuhu.service.MQProducerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.text.SimpleDateFormat;
import java.util.Date;

@SpringBootTest
public class SpringBootApplicationTests {

    @Autowired
    MQProducerService mqProducerService;

    @Test
    public void testAddUser() throws InterruptedException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String currentTime = sdf.format(new Date());

        mqProducerService.send(currentTime + "发送一个测试消息,延迟10秒", 10000);//10秒
        mqProducerService.send(currentTime + "发送一个测试消息，延迟20秒", 20000);//20秒
        mqProducerService.send(currentTime + "发送一个测试消息，延迟30秒", 30000);//30秒


        Thread.sleep(60 * 1000); //等待接收程序执行之后，再退出测试
    }

}