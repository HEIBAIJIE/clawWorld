package com.heibai.clawworld.application.impl.combat;

import com.heibai.clawworld.application.service.PlayerSessionService;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.infrastructure.persistence.entity.PartyEntity;
import com.heibai.clawworld.infrastructure.persistence.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 战斗发起服务 - 负责收集参战成员和准备战斗
 */
@Component
@RequiredArgsConstructor
public class CombatInitiationService {

    private final PlayerSessionService playerSessionService;
    private final PartyRepository partyRepository;

    /**
     * 收集同地图的队伍成员
     * 根据设计文档：只有在同一地图的队友才能参战
     * 如果玩家有队伍，收集同地图的队友；如果没有队伍，只返回玩家自己
     */
    public List<Player> collectPartyMembers(Player player) {
        List<Player> partyMembers = new ArrayList<>();
        partyMembers.add(player);

        // 如果玩家有队伍，收集同地图的队友
        if (player.getPartyId() != null) {
            Optional<PartyEntity> partyOpt = partyRepository.findById(player.getPartyId());

            if (partyOpt.isPresent()) {
                PartyEntity party = partyOpt.get();
                for (String memberId : party.getMemberIds()) {
                    if (!memberId.equals(player.getId())) {
                        Player member = playerSessionService.getPlayerState(memberId);
                        // 只收集同地图的队友
                        if (member != null && member.getMapId() != null
                            && member.getMapId().equals(player.getMapId())) {
                            partyMembers.add(member);
                        }
                    }
                }
            }
        }

        return partyMembers;
    }

    /**
     * 检查两个玩家是否在同一地图
     */
    public boolean arePlayersOnSameMap(Player player1, Player player2) {
        return player1.getMapId() != null && player1.getMapId().equals(player2.getMapId());
    }

    /**
     * 检查玩家是否在指定位置的交互范围内（九宫格）
     */
    public boolean isInInteractionRange(Player player, int targetX, int targetY) {
        int dx = Math.abs(player.getX() - targetX);
        int dy = Math.abs(player.getY() - targetY);
        return dx <= 1 && dy <= 1;
    }
}
