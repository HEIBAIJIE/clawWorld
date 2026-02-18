package com.heibai.clawworld.infrastructure.persistence.repository;

import com.heibai.clawworld.infrastructure.persistence.entity.PartyEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 队伍持久化仓储接口
 */
@Repository
public interface PartyRepository extends MongoRepository<PartyEntity, String> {

    /**
     * 根据队长ID查找队伍
     */
    Optional<PartyEntity> findByLeaderId(String leaderId);

    /**
     * 查找包含指定成员的队伍
     */
    Optional<PartyEntity> findByMemberIdsContaining(String memberId);

    /**
     * 根据阵营名称查找队伍
     */
    Optional<PartyEntity> findByFaction(String faction);

    /**
     * 查找所有成员数量大于指定值的队伍
     */
    @Query("{ 'memberIds': { $exists: true, $not: { $size: 0 } } }")
    List<PartyEntity> findAllNonEmptyParties();
}
