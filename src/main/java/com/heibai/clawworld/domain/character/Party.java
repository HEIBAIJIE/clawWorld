package com.heibai.clawworld.domain.character;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 队伍领域对象（纯业务逻辑对象，不包含持久化注解）
 * 根据设计文档：
 * - 玩家初始状态没有队伍（partyId为null）
 * - 只有当两个或以上玩家组队时，才会创建真正的队伍实体
 * - 当队伍成员少于2人时，队伍自动解散
 * - 注意：邀请者发起邀请时会创建一个临时队伍（只有1人），等待被邀请者加入
 */
@Data
public class Party {
    private String id;

    /**
     * 队长的玩家ID
     */
    private String leaderId;

    /**
     * 队伍成员的玩家ID列表（包括队长）
     * 最多4个人
     */
    private List<String> memberIds = new ArrayList<>();

    /**
     * 队伍阵营
     * 队伍中所有成员共享同一个阵营
     */
    private String faction;

    /**
     * 队伍创建时间
     */
    private Long createdTime;

    /**
     * 待处理的组队邀请
     */
    private List<PartyInvitation> pendingInvitations = new ArrayList<>();

    /**
     * 待处理的加入请求
     */
    private List<PartyRequest> pendingRequests = new ArrayList<>();

    /**
     * 检查队伍是否已满
     */
    public boolean isFull() {
        return memberIds.size() >= 4;
    }

    /**
     * 检查是否为单人队伍（临时队伍，等待被邀请者加入）
     * 单人队伍不是真正的队伍，其他玩家不能请求加入
     */
    public boolean isSolo() {
        return memberIds.size() <= 1;
    }

    /**
     * 检查玩家是否为队长
     */
    public boolean isLeader(String playerId) {
        return leaderId != null && leaderId.equals(playerId);
    }

    /**
     * 检查玩家是否在队伍中
     */
    public boolean hasMember(String playerId) {
        return memberIds.contains(playerId);
    }

    /**
     * 组队邀请
     */
    @Data
    public static class PartyInvitation {
        /**
         * 邀请发起者的玩家ID
         */
        private String inviterId;

        /**
         * 被邀请者的玩家ID
         */
        private String inviteeId;

        /**
         * 邀请发起时间（毫秒间戳）
         */
        private Long inviteTime;

        /**
         * 邀请是否过期（5分钟）
         */
        public boolean isExpired() {
            return System.currentTimeMillis() - inviteTime > 5 * 60 * 1000;
        }
    }

    /**
     * 加入队伍请求
     */
    @Data
    public static class PartyRequest {
        /**
         * 请求者的玩家ID
         */
        private String requesterId;

        /**
         * 被请求的队伍ID
         */
        private String partyId;

        /**
         * 请求发起时间（毫秒时间戳）
         */
        private Long requestTime;

        /**
         * 请求是否过期（5分钟）
         */
        public boolean isExpired() {
            return System.currentTimeMillis() - requestTime > 5 * 60 * 1000;
        }
    }
}
