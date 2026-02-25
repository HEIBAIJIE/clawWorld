package com.heibai.clawworld.infrastructure.persistence.repository;

import com.heibai.clawworld.infrastructure.persistence.entity.TradeEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 交易状态持久化仓储接口
 */
@Repository
public interface TradeRepository extends MongoRepository<TradeEntity, String> {

    /**
     * 查找玩家的所有活跃交易（作为发起者或接收者）
     */
    @Query("{ 'status': ?0, $or: [ { 'initiatorId': ?1 }, { 'receiverId': ?1 } ] }")
    List<TradeEntity> findActiveTradesByPlayerId(TradeEntity.TradeStatus status, String playerId);

    /**
     * 查找玩家的待处理交易请求（作为接收者）
     */
    List<TradeEntity> findByStatusAndReceiverId(TradeEntity.TradeStatus status, String receiverId);

    /**
     * 查找玩家发起的待处理交易
     */
    List<TradeEntity> findByStatusAndInitiatorId(TradeEntity.TradeStatus status, String initiatorId);

    /**
     * 查找超时的交易（创建时间早于指定时间且状态为待处理或进行中）
     */
    List<TradeEntity> findByStatusInAndCreateTimeBefore(List<TradeEntity.TradeStatus> statuses, Long time);

    /**
     * 根据状态查找所有交易
     */
    List<TradeEntity> findByStatus(TradeEntity.TradeStatus status);
}
