package com.heibai.clawworld.infrastructure.persistence.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 账号持久化实体
 * 用于MongoDB存储，与领域对象Account分离
 */
@Data
@Document(collection = "accounts")
public class AccountEntity {
    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    @Indexed(unique = true)
    private String nickname;

    private String password;

    // 关联的玩家ID
    @Indexed
    private String playerId;

    // 会话信息
    @Indexed
    private String sessionId;
    private boolean online;
    private Long lastLoginTime;
    private Long lastLogoutTime;

    // 当前窗口状态
    private String currentWindowId;
    private String currentWindowType; // REGISTER, MAP, COMBAT, TRADE

    // 玩家上次收到响应时的窗口类型（用于检测被动窗口变化）
    // 当其他玩家的操作导致当前玩家窗口变化时，currentWindowType会被更新，
    // 但lastKnownWindowType不会更新，直到玩家收到包含新窗口内容的响应
    private String lastKnownWindowType;

    // 最后一次获取状态的时间戳（用于追踪环境变化）
    private Long lastStateTimestamp;

    // 最后一次执行的指令（用于在状态中显示）
    private String lastCommand;
    private Long lastCommandTimestamp;

    // 上次状态的实体快照（用于追踪实体变化）
    // 格式：Map<entityName, EntitySnapshot>
    // EntitySnapshot包含：位置(x,y)和交互选项列表
    private java.util.Map<String, EntitySnapshot> lastEntitySnapshot;

    // 上次状态的地图ID（用于检测地图切换）
    private String lastMapId;

    // 上次获取的战斗日志序列号（用于增量获取战斗日志）
    private Integer lastCombatLogSequence;

    // 上次的队伍状态快照（用于追踪队伍变化）
    private PartySnapshot lastPartySnapshot;

    @Data
    public static class EntitySnapshot {
        private int x;
        private int y;
        private java.util.List<String> interactionOptions;
        private Boolean isDead; // 敌人死亡状态（仅对敌人有效）
        private String entityType; // 实体类型
    }

    @Data
    public static class PartySnapshot {
        private String partyId;
        private boolean isLeader;
        private String leaderName;  // 队长名字
        private java.util.List<String> memberNames;
        // 收到的待处理邀请（inviterName -> inviteTime）
        private java.util.Map<String, Long> pendingInvitationsReceived;
    }
}
