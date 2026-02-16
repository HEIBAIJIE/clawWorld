package com.heibai.clawworld.service.game;

import com.heibai.clawworld.domain.character.Party;

/**
 * 队伍管理服务
 * 负责队伍的创建、解散、成员管理等
 */
public interface PartyService {

    /**
     * 邀请玩家组队
     * @param inviterId 邀请者ID
     * @param targetPlayerId 被邀请者ID
     * @return 操作结果
     */
    PartyResult invitePlayer(String inviterId, String targetPlayerId);

    /**
     * 接受组队邀请
     * @param playerId 玩家ID
     * @param inviterId 邀请者ID
     * @return 操作结果
     */
    PartyResult acceptInvite(String playerId, String inviterId);

    /**
     * 拒绝组队邀请
     * @param playerId 玩家ID
     * @param inviterId 邀请者ID
     * @return 操作结果
     */
    PartyResult rejectInvite(String playerId, String inviterId);

    /**
     * 请求加入队伍
     * @param playerId 玩家ID
     * @param targetPlayerId 目标队伍成员ID
     * @return 操作结果
     */
    PartyResult requestJoin(String playerId, String targetPlayerId);

    /**
     * 接受加入请求
     * @param leaderId 队长ID
     * @param requesterId 请求者ID
     * @return 操作结果
     */
    PartyResult acceptJoinRequest(String leaderId, String requesterId);

    /**
     * 拒绝加入请求
     * @param leaderId 队长ID
     * @param requesterId 请求者ID
     * @return 操作结果
     */
    PartyResult rejectJoinRequest(String leaderId, String requesterId);

    /**
     * 踢出队员（仅队长）
     * @param leaderId 队长ID
     * @param targetPlayerId 被踢出的玩家ID
     * @return 操作结果
     */
    PartyResult kickPlayer(String leaderId, String targetPlayerId);

    /**
     * 离开队伍（非队长）
     * @param playerId 玩家ID
     * @return 操作结果
     */
    PartyResult leaveParty(String playerId);

    /**
     * 解散队伍（仅队长）
     * @param leaderId 队长ID
     * @return 操作结果
     */
    PartyResult disbandParty(String leaderId);

    /**
     * 获取队伍信息
     * @param partyId 队伍ID
     * @return 队伍对象
     */
    Party getParty(String partyId);

    /**
     * 获取玩家所在队伍
     * @param playerId 玩家ID
     * @return 队伍对象，如果没有队伍返回null
     */
    Party getPlayerParty(String playerId);

    /**
     * 队伍操作结果
     */
    class PartyResult {
        private boolean success;
        private String message;
        private String partyId;

        public static PartyResult success(String message) {
            PartyResult result = new PartyResult();
            result.success = true;
            result.message = message;
            return result;
        }

        public static PartyResult success(String message, String partyId) {
            PartyResult result = new PartyResult();
            result.success = true;
            result.message = message;
            result.partyId = partyId;
            return result;
        }

        public static PartyResult error(String message) {
            PartyResult result = new PartyResult();
            result.success = false;
            result.message = message;
            return result;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getPartyId() {
            return partyId;
        }
    }
}
