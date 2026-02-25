package com.heibai.clawworld.infrastructure.scheduler;

import com.heibai.clawworld.infrastructure.persistence.entity.PartyEntity;
import com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity;
import com.heibai.clawworld.infrastructure.persistence.entity.TradeEntity;
import com.heibai.clawworld.infrastructure.persistence.repository.PartyRepository;
import com.heibai.clawworld.infrastructure.persistence.repository.PlayerRepository;
import com.heibai.clawworld.infrastructure.persistence.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 邀请和请求清理定时任务
 * 清理过期的组队邀请、加入请求和交易请求
 * 过期时间：1分钟
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvitationCleanupScheduler {

    private final PartyRepository partyRepository;
    private final PlayerRepository playerRepository;
    private final TradeRepository tradeRepository;

    private static final long EXPIRATION_TIME_MS = 60 * 1000; // 1分钟

    /**
     * 每10秒检查一次过期的邀请和请求
     */
    @Scheduled(fixedRate = 10000)
    @Transactional
    public void cleanupExpiredInvitations() {
        cleanupExpiredPartyInvitations();
        cleanupExpiredTradeRequests();
    }

    /**
     * 清理过期的组队邀请和加入请求
     */
    private void cleanupExpiredPartyInvitations() {
        List<PartyEntity> allParties = partyRepository.findAll();
        long currentTime = System.currentTimeMillis();

        for (PartyEntity party : allParties) {
            boolean modified = false;

            // 清理过期的邀请
            int invitationsBefore = party.getPendingInvitations().size();
            party.getPendingInvitations().removeIf(inv ->
                inv.getInviteTime() == null || currentTime - inv.getInviteTime() > EXPIRATION_TIME_MS);
            if (party.getPendingInvitations().size() < invitationsBefore) {
                modified = true;
                log.debug("清理了 {} 个过期的组队邀请 (队伍: {})",
                    invitationsBefore - party.getPendingInvitations().size(), party.getId());
            }

            // 清理过期的加入请求
            int requestsBefore = party.getPendingRequests().size();
            party.getPendingRequests().removeIf(req ->
                req.getRequestTime() == null || currentTime - req.getRequestTime() > EXPIRATION_TIME_MS);
            if (party.getPendingRequests().size() < requestsBefore) {
                modified = true;
                log.debug("清理了 {} 个过期的加入请求 (队伍: {})",
                    requestsBefore - party.getPendingRequests().size(), party.getId());
            }

            // 如果是单人队伍且没有待处理的邀请了，解散队伍
            if (party.isSolo() && party.getPendingInvitations().isEmpty()) {
                disbandParty(party);
                log.info("解散了空的单人队伍: {}", party.getId());
            } else if (modified) {
                partyRepository.save(party);
            }
        }
    }

    /**
     * 清理过期的交易请求
     */
    private void cleanupExpiredTradeRequests() {
        List<TradeEntity> pendingTrades = tradeRepository.findByStatus(TradeEntity.TradeStatus.PENDING);
        long currentTime = System.currentTimeMillis();

        for (TradeEntity trade : pendingTrades) {
            if (trade.getCreateTime() == null || currentTime - trade.getCreateTime() > EXPIRATION_TIME_MS) {
                // 取消过期的交易请求
                trade.setStatus(TradeEntity.TradeStatus.CANCELLED);
                tradeRepository.save(trade);

                // 清理发起者的tradeId
                Optional<PlayerEntity> initiatorOpt = playerRepository.findById(trade.getInitiatorId());
                if (initiatorOpt.isPresent()) {
                    PlayerEntity initiator = initiatorOpt.get();
                    if (trade.getId().equals(initiator.getTradeId())) {
                        initiator.setTradeId(null);
                        playerRepository.save(initiator);
                    }
                }

                log.info("取消了过期的交易请求: {} (发起者: {})", trade.getId(), trade.getInitiatorId());
            }
        }
    }

    /**
     * 解散队伍
     */
    private void disbandParty(PartyEntity party) {
        // 清除所有成员的队伍信息
        for (String memberId : party.getMemberIds()) {
            Optional<PlayerEntity> memberOpt = playerRepository.findById(memberId);
            if (memberOpt.isPresent()) {
                PlayerEntity member = memberOpt.get();
                member.setPartyId(null);
                member.setPartyLeader(false);
                playerRepository.save(member);
            }
        }

        // 删除队伍
        partyRepository.delete(party);
    }
}
