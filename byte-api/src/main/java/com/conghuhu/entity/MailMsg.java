package com.conghuhu.entity;


import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

@Data
public class MailMsg {
    @JSONField(name = "user_id")
    private Long userID;


    @JSONField(name = "card_id")
    private Long cardID;

    private String email;


    @JSONField(name = "product_name")
    private String projectName;


    @JSONField(name = "card_name")
    private String cardName;

    @JSONField(name = "is_reach_three")
    public boolean isReachThree;

    @JSONField(name = "start_time_list")
    private String startTimeList;

    public MailMsg(Long userID, Long cardID, String email, String projectName, String cardName, boolean isReachThree, String startTimeList) {
        this.userID = userID;
        this.cardID = cardID;
        this.email = email;
        this.projectName = projectName;
        this.cardName = cardName;
        this.isReachThree = isReachThree;
        this.startTimeList = startTimeList;
    }

    public MailMsg(){

    }
}
