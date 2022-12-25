package com.conghuhu.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.conghuhu.entity.*;
import com.conghuhu.mapper.*;

import com.conghuhu.params.*;
import com.conghuhu.result.JsonResult;
import com.conghuhu.result.ResultCode;

import com.conghuhu.result.ResultTool;
import com.conghuhu.service.CardService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.conghuhu.service.MQProducerService;
import com.conghuhu.service.ThreadService;
import com.conghuhu.service.UserService;
import com.conghuhu.utils.RedisUtil;
import com.conghuhu.utils.TimeUtils;
import com.conghuhu.utils.UserThreadLocal;
import com.conghuhu.vo.CardVo;
import com.conghuhu.vo.UserVo;
import com.conghuhu.vo.WebsocketDetail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.sql.Time;
import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;


/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author conghuhu
 * @since 2022-01-26
 */
@Service
@Slf4j
public class CardServiceImpl extends ServiceImpl<CardMapper, Card> implements CardService {

    private final ThreadService threadService;
    private final CardTagMapper cardTagMapper;
    private final CardUserMapper cardUserMapper;
    private final CardMapper cardMapper;
    private final UserService userService;

    @Autowired
    MQProducerService producerService;

    private final ProductMapper productMapper;

    private final UserMapper userMapper;

    private final RedisUtil redisUtil;

    public CardServiceImpl(CardTagMapper cardTagMapper, CardMapper cardMapper, CardUserMapper cardUserMapper, UserService userService, ThreadService threadService, ProductMapper productMapper, UserMapper userMapper, RedisUtil redisUtil) {
        this.cardTagMapper = cardTagMapper;
        this.cardMapper = cardMapper;
        this.cardUserMapper = cardUserMapper;
        this.userService = userService;
        this.threadService = threadService;
        this.productMapper = productMapper;
        this.userMapper = userMapper;
        this.redisUtil = redisUtil;
    }

    @Override
    public Card getByName(String cardname) {
        return cardMapper.getByCardName(cardname);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public JsonResult removeCardById(Long cardId) {
        Card card = cardMapper.selectById(cardId);
        int res = cardMapper.deleteById(cardId);
        cardTagMapper.delete(new LambdaQueryWrapper<CardTag>().eq(CardTag::getCardId, cardId));
        cardUserMapper.delete(new LambdaQueryWrapper<CardUser>().eq(CardUser::getCardId, cardId));
        if (res > 0) {
            WebsocketDetail detail = CardVo.builder().id(cardId).listAfterId(card.getListId()).build();
            threadService.notifyAllMemberByProductId(card.getProductId(),
                    "removeModels", "Card",
                    new ArrayList<>(Arrays.asList("updates")), detail);
            //  删除cardID对应的 card 提醒消息
            redisDeleteMsg(cardId);
            return ResultTool.success();
        } else {
            // 手动回滚
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ResultTool.fail();
        }
    }


    @Override
    public JsonResult createTag(CardParam cardParam) {
        Card card = new Card();

        BeanUtils.copyProperties(cardParam, card);

        String cardname = cardParam.getCardname();

        card.setTag(false);
        card.setExpired(false);
        card.setClosed(false);
        card.setExecutor(false);
        card.setDescription("");
        card.setCreatedTime(LocalDateTime.now());
        card.setCompleted(false);
        User user = UserThreadLocal.get();
        UserVo userVo = new UserVo();
        BeanUtils.copyProperties(user, userVo);
        card.setCreator(user.getUserId());

        Card selectOne = cardMapper.selectOne(new LambdaQueryWrapper<Card>()
                .eq(Card::getCardname, cardname)
                .eq(Card::getProductId, cardParam.getProductId())
        );

        if (selectOne != null) {
            return ResultTool.fail(ResultCode.CARD_CONSIST);
        }
        int res = cardMapper.insert(card);
        if (res > 0) {
            CardVo cardVo = new CardVo();
            cardVo.setId(card.getCardId());
            cardVo.setName(card.getCardname());
            cardVo.setProductId(card.getProductId());
            cardVo.setClosed(false);
            cardVo.setPos(card.getPos());
            cardVo.setListAfterId(card.getListId());
            cardVo.setCreatedTime(card.getCreatedTime());
            cardVo.setCreator(userVo);
            threadService.notifyAllMemberByProductId(card.getProductId(),
                    "addModels", "Card",
                    new ArrayList<>(Arrays.asList("updates", "add")), cardVo);
            return ResultTool.success(card);
        } else {
            return ResultTool.fail();
        }
    }

    @Override
    public JsonResult updateTag(Card cardParam) {
        Card card = new Card();
        BeanUtils.copyProperties(cardParam, card);
        int res = cardMapper.updateById (card);
        if (res > 0) {
            return ResultTool.success();
        } else {
            return ResultTool.fail();
        }
    }

    @Override
    public JsonResult editDescByCardId(EditParam editParam, Long cardId) {
        Card card = new Card();
        card.setCardId(cardId);
        card.setDescription(editParam.getEditContent());
        int res = cardMapper.updateById(card);
        if (res > 0) {
            return ResultTool.success();
        } else {
            return ResultTool.fail();
        }
    }


    @Override
    public JsonResult getCardsByListId(Long listId) {
        LambdaUpdateWrapper<Card> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Card::getListId, listId);
        List<Card> cardList = cardMapper.selectList(updateWrapper);
        if (cardList != null) {
            return ResultTool.success(cardList);
        } else {
            return ResultTool.fail();
        }
    }

    @Override
    public JsonResult editCardNameByCardId(EditParam editParam, Long cardId) {
        Card card = new Card();
        card.setCardId(cardId);
        card.setCardname(editParam.getEditContent());
        Card selectById = cardMapper.selectById(cardId);
        int res = cardMapper.updateById(card);
        if (res > 0) {
            CardVo cardVo = new CardVo();
            cardVo.setId(cardId);
            cardVo.setName(card.getCardname());
            cardVo.setListId(selectById.getListId());
            threadService.notifyAllMemberByProductId(selectById.getProductId(),
                    "updateModels", "Card",
                    new ArrayList<>(Arrays.asList("updates", "name")), cardVo);
            return ResultTool.success();
        } else {
            return ResultTool.fail();
        }
    }

    @Override
    public JsonResult setCardDeadline(CardDateParam cardDateParam, Long cardId) throws InterruptedException {
        Card card = cardMapper.selectById(cardId);
        if (card == null) {
            return ResultTool.fail(ResultCode.CARD_ID_NOT_CONSIST);
        }
        LocalDateTime beginTime = cardDateParam.getBeginTime();
        LocalDateTime deadline = cardDateParam.getDeadline();
        LambdaUpdateWrapper<Card> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Card::getCardId, cardId);
        boolean remove = beginTime == null && deadline == null;
        if (remove) {
            updateWrapper.set(Card::getBegintime, null)
                    .set(Card::getDeadline, null)
                    .set(Card::getExpired, false)
                    .set(Card::getCompleted, false);
        } else {
            LocalDateTime now = LocalDateTime.now();
            boolean isExpire = false;
            if (deadline.isBefore(now)) {
                updateWrapper.set(Card::getExpired, true);
                isExpire = true;
            }

            updateWrapper.set(Card::getBegintime, beginTime)
                    .set(Card::getDeadline, deadline);

            if (!isExpire) {
                // 发消息
                sendBuildMsg(card,now,beginTime);

                // 落redis
                sendRedis(card,beginTime);
            }

        }

        int res = cardMapper.update(new Card(), updateWrapper);
        if (res > 0) {
            CardVo cardVo = new CardVo();
            cardVo.setId(cardId);
            cardVo.setBegintime(beginTime);
            cardVo.setDeadline(deadline);
            cardVo.setListId(card.getListId());
            threadService.notifyAllMemberByProductId(card.getProductId(),
                    "updateModels", "Card",
                    new ArrayList<>(Arrays.asList("updates", "time", remove ? "remove" : "add")), cardVo);
            return ResultTool.success();
        } else {
            return ResultTool.fail();
        }
    }

    @Override
    public JsonResult moveCard(CardMoveParam cardMoveParam) {
        Long cardId = cardMoveParam.getCardId();
        Float pos = cardMoveParam.getPos();
        Long listId = cardMoveParam.getListId();
        Card card = new Card();
        card.setCardId(cardId);
        card.setPos(pos);
        if (listId != null) {
            // 跨列移动,为null就是同列移动
            card.setListId(listId);
        }
        Card selectById = cardMapper.selectById(cardId);
        Long listBeforeId = selectById.getListId();
        int res = cardMapper.updateById(card);
        if (res > 0) {
            WebsocketDetail detail = CardVo.builder()
                    .id(cardId)
                    .pos(pos)
                    .listBeforeId(listBeforeId)
                    .listAfterId(listId).build();
            threadService.notifyAllMemberByProductId(selectById.getProductId(),
                    "moveModels", "Card",
                    new ArrayList<>(Arrays.asList("move", listBeforeId.equals(listId) ? "same" : "different"))
                    , detail);
            return ResultTool.success();
        } else {
            return ResultTool.fail();
        }
    }

    @Override
    public List<Tag> getTagsByCardId(Long cardId) {
        return cardMapper.getTagsByCardId(cardId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public JsonResult setExecutor(Long userId, Long cardId) throws InterruptedException {
        CardUser cardUser = new CardUser();
        cardUser.setUserId(userId);
        cardUser.setCardId(cardId);
        Integer selectCount = cardUserMapper.selectCount(new LambdaQueryWrapper<CardUser>()
                .eq(CardUser::getCardId, cardId).eq(CardUser::getUserId, userId));
        if (selectCount > 0) {
            List<UserVo> executors = getExecutorsByCardId(cardId);
            return ResultTool.success(executors);
        }
        int res = cardUserMapper.insert(cardUser);
        Card card = new Card();
        card.setCardId(cardId);
        card.setExecutor(true);
        int updateCard = cardMapper.updateById(card);
        LocalDateTime now = LocalDateTime.now();
        Card realCard = cardMapper.selectById(cardId);
        if (res > 0 && updateCard > 0) {
            List<UserVo> executors = getExecutorsByCardId(cardId);
            if (realCard.getBegintime() != null){
                // 给新执行人重新发消息
                sendBuildMsg(realCard,now,realCard.getBegintime());

                // 落redis
                sendRedis(realCard,realCard.getBegintime());
            }
            Card selectById = cardMapper.selectById(cardId);
            CardVo cardVo = new CardVo();
            cardVo.setId(cardId);
            cardVo.setListId(selectById.getListId());
            cardVo.setExecutorList(executors);
            threadService.notifyAllMemberByProductId(selectById.getProductId(),
                    "updateModels", "Card",
                    new ArrayList<>(Arrays.asList("updates", "member"))
                    , cardVo);
            return ResultTool.success(executors);
        } else {
            // 手动回滚
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ResultTool.fail();
        }
    }



    @Override
    public JsonResult removeExecutor(Long userId, Long cardId) {
        CardUser cardUser = new CardUser();
        cardUser.setUserId(userId);
        cardUser.setCardId(cardId);
        LambdaQueryWrapper<CardUser> queryWrapper = new LambdaQueryWrapper<CardUser>().eq(CardUser::getUserId, userId).eq(CardUser::getCardId, cardId);
        Integer selectCount = cardUserMapper.selectCount(queryWrapper);
        if (selectCount <= 0) {
            return ResultTool.fail(ResultCode.PARAMS_ERROR);
        }
        int delete = cardUserMapper.delete(queryWrapper);
        if (delete > 0) {
            List<UserVo> executors = getExecutorsByCardId(cardId);
            Card selectById = cardMapper.selectById(cardId);
            CardVo cardVo = new CardVo();
            // 删除消息
            redisDeleteMsg(cardId);
            cardVo.setId(cardId);
            cardVo.setListId(selectById.getListId());
            cardVo.setExecutorList(executors);
            threadService.notifyAllMemberByProductId(selectById.getProductId(),
                    "updateModels", "Card",
                    new ArrayList<>(Arrays.asList("updates", "member"))
                    , cardVo);
            return ResultTool.success();
        } else {
            return ResultTool.fail();
        }
    }

    @Override
    public List<UserVo> getExecutorsByCardId(Long cardId) {
        List<UserVo> executors = cardMapper.getExecutorsByCardId(cardId);
        return executors;
    }

    @Override
    public JsonResult setCardBackground(String background, Long cardId) {
        Card selectById = cardMapper.selectById(cardId);
        Card card = new Card();
        card.setCardId(cardId);
        card.setBackground("#" + background);
        int res = cardMapper.updateById(card);
        if (res > 0) {
            CardVo cardVo = new CardVo();
            cardVo.setId(cardId);
            cardVo.setListId(selectById.getListId());
            cardVo.setBackground("#" + background);
            threadService.notifyAllMemberByProductId(selectById.getProductId(),
                    "updateModels", "Card",
                    new ArrayList<>(Arrays.asList("updates", "background"))
                    , cardVo);
            return ResultTool.success();
        } else {
            return ResultTool.fail();
        }
    }

    @Override
    public JsonResult changeCardCompleteStatus(Boolean completed, Long cardId) {
        if (completed == null || cardId == null) {
            return ResultTool.fail(ResultCode.PARAM_IS_BLANK);
        }
        Card selectById = cardMapper.selectById(cardId);
        Card card = new Card();
        card.setCardId(cardId);
        card.setCompleted(completed);
        int update = cardMapper.updateById(card);
        if (update > 0) {
            CardVo cardVo = new CardVo();
            cardVo.setId(cardId);
            cardVo.setCompleted(completed);
            cardVo.setListId(selectById.getListId());
            threadService.notifyAllMemberByProductId(selectById.getProductId(),
                    "updateModels", "Card",
                    new ArrayList<>(Arrays.asList("updates", "time", "complete")), cardVo);
            return ResultTool.success();
        } else {
            return ResultTool.fail();
        }
    }

    @Override
    public JsonResult getCardById(Long cardId) {
        Card card = cardMapper.selectById(cardId);
        if (card == null) {
            return ResultTool.fail(ResultCode.PARAMS_ERROR);
        }
        CardVo cardVo = new CardVo();
        BeanUtils.copyProperties(card, cardVo);
        List<Tag> tags = null;
        List<UserVo> executorList = null;
        if (card.getTag()) {
            tags = cardMapper.getTagsByCardId(cardId);
        }
        if (card.getExecutor()) {
            executorList = getExecutorsByCardId(cardId);
        }

        UserVo creator = userService.findUserVoById(card.getCreator());

        cardVo.setTagList(tags != null ? tags : new ArrayList<>());
        cardVo.setExecutorList(executorList != null ? executorList : new ArrayList<>());
        cardVo.setCreator(creator);
        return ResultTool.success(cardVo);
    }






    public void sendBuildMsg(Card card,LocalDateTime nowTime,LocalDateTime startTime) throws InterruptedException {
        LocalDateTime revertNomalStartTime = TimeUtils.UTCToNORMAL(startTime);

        List<UserVo> executorsList = getExecutorsByCardId(card.getCardId());
        MailMsg mailMsg = new MailMsg();

        String productName = productMapper.getProductNameById(card.getProductId());
        Product product = productMapper.selectById(card.getProductId());
        User owner = userMapper.selectById(product.getOwnerId());

        mailMsg.setEmail(owner.getUsername());
        mailMsg.setCardID(card.getCardId());
        mailMsg.setProjectName(productName);
        mailMsg.setCardName(card.getCardname());
        mailMsg.setUserID(owner.getUserId());
        mailMsg.setUserID(owner.getUserId());
        mailMsg.setStartTimeList(revertNomalStartTime.toString() + "-" + executorsList.toString());
        log.info("nowtime : {},  revertNormalStartTime : {}",nowTime,revertNomalStartTime);


        if (executorsList.size() == 0) {
            mailMsg.setUserID(owner.getUserId());

            int period = TimeUtils.timeMinus(nowTime, revertNomalStartTime);
            if (period < 60000 * 3 && period > 0) {
                period = 0;
                mailMsg.setReachThree(false);
            }else {
                period = period - (60000 * 3);
                mailMsg.setReachThree(true);
            }
            producerService.send(JSON.toJSONString(mailMsg),period);
            log.info("此卡片无设置执行者，事件提醒默认给Owner,perid " + period);
            return;
        }

        CountDownLatch countDownLatch = new CountDownLatch(executorsList.size());
        for (int i = 0; i < executorsList.size(); i++) {
            MailMsg msg = new MailMsg();
            UserVo user = executorsList.get(i);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    int period = TimeUtils.timeMinus(nowTime, revertNomalStartTime);
                    if (period < 60000 * 3 && period > 0) {
                        period = 0;
                        msg.setReachThree(false);
                    }else {
                        period = period - (60000 * 3);
                        msg.setReachThree(true);
                    }
                    msg.setCardID(card.getCardId());
                    msg.setEmail(user.getUsername());
                    msg.setUserID(user.getUserId());
                    msg.setProjectName(productName);
                    msg.setCardName(card.getCardname());
                    msg.setStartTimeList(revertNomalStartTime.toString() + "-" + executorsList.toString());
                    producerService.send(JSON.toJSONString(msg),period);
                    countDownLatch.countDown();
                }
            }).start();

        }
        countDownLatch.await();
        log.info("此卡片已发送给对应的执行者MQ （ >= 2）");
    }




    private void sendRedis(Card card, LocalDateTime beginTime) {
        LocalDateTime revertNomalStartTime = TimeUtils.UTCToNORMAL(beginTime);
        List<UserVo> executorsList = getExecutorsByCardId(card.getCardId());
        String key = card.getCardId().toString() + "-C";
        String value = revertNomalStartTime.toString() +  "-" + executorsList.toString();
        redisUtil.set(key,value);
        log.info("---------------------------------------------------");
        log.info("发消息后落Redis记录成功，key:" + key  +", value:" + value);
        log.info("---------------------------------------------------");
    }



    private void redisDeleteMsg(Long cardID) {
        redisUtil.del(cardID + "-C");
        log.info("移除reids中卡片的msg,cardID : {}",cardID);
    }




}
