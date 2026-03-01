package com.heibai.clawworld.interfaces.log;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 游戏日志
 * 统一的日志格式：[来源][时间][类型][子类型]内容
 */
@Data
@Builder
public class GameLog {

    /**
     * 日志来源
     */
    public enum Source {
        SERVER("服务端"),
        CLIENT("你");

        private final String displayName;

        Source(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 日志类型
     */
    public enum Type {
        BACKGROUND("背景"),      // 背景信息（游戏说明、规则等）
        WINDOW("窗口"),          // 窗口内容
        STATE("状态"),           // 状态更新
        COMMAND("发送指令");     // 玩家发送的指令

        private final String displayName;

        Type(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private Source source;
    private Long timestamp;
    private Type type;
    private String subType;  // 子类型，如"系统说明"、"地图窗口"、"环境变化"等
    private String content;

    /**
     * 格式化为日志字符串
     */
    public String format() {
        StringBuilder sb = new StringBuilder();

        // [时间] - 只显示小时和分钟
        sb.append(formatTimestamp(timestamp));

        // [类型]
        sb.append("[").append(type.getDisplayName()).append("]");

        // [子类型]
        if (subType != null && !subType.isEmpty()) {
            sb.append("[").append(subType).append("]");
        }

        // 内容
        sb.append(content);

        return sb.toString();
    }

    /**
     * 格式化时间戳 - 显示小时、分钟和秒
     */
    private String formatTimestamp(Long timestamp) {
        if (timestamp == null) {
            return "";
        }
        Instant instant = Instant.ofEpochMilli(timestamp);
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDateTime dateTime = LocalDateTime.ofInstant(instant, zoneId);

        return String.format("[%02d:%02d:%02d]",
            dateTime.getHour(),
            dateTime.getMinute(),
            dateTime.getSecond());
    }

    /**
     * 创建服务端背景日志
     */
    public static GameLog serverBackground(String subType, String content) {
        return GameLog.builder()
            .source(Source.SERVER)
            .timestamp(System.currentTimeMillis())
            .type(Type.BACKGROUND)
            .subType(subType)
            .content(content)
            .build();
    }

    /**
     * 创建服务端窗口日志
     */
    public static GameLog serverWindow(String subType, String content) {
        return GameLog.builder()
            .source(Source.SERVER)
            .timestamp(System.currentTimeMillis())
            .type(Type.WINDOW)
            .subType(subType)
            .content(content)
            .build();
    }

    /**
     * 创建服务端窗口日志（自定义时间戳）
     */
    public static GameLog serverWindowWithTimestamp(String subType, String content, long timestamp) {
        return GameLog.builder()
            .source(Source.SERVER)
            .timestamp(timestamp)
            .type(Type.WINDOW)
            .subType(subType)
            .content(content)
            .build();
    }

    /**
     * 创建服务端状态日志
     */
    public static GameLog serverState(String subType, String content) {
        return GameLog.builder()
            .source(Source.SERVER)
            .timestamp(System.currentTimeMillis())
            .type(Type.STATE)
            .subType(subType)
            .content(content)
            .build();
    }

    /**
     * 创建服务端状态日志（自定义时间戳）
     */
    public static GameLog serverStateWithTimestamp(String subType, String content, long timestamp) {
        return GameLog.builder()
            .source(Source.SERVER)
            .timestamp(timestamp)
            .type(Type.STATE)
            .subType(subType)
            .content(content)
            .build();
    }

    /**
     * 创建客户端指令日志
     */
    public static GameLog clientCommand(String command) {
        return GameLog.builder()
            .source(Source.CLIENT)
            .timestamp(System.currentTimeMillis())
            .type(Type.COMMAND)
            .subType("发送指令")
            .content(command)
            .build();
    }
}
