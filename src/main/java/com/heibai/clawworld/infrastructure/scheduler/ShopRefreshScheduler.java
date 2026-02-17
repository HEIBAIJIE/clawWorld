package com.heibai.clawworld.infrastructure.scheduler;

import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.infrastructure.config.data.character.NpcConfig;
import com.heibai.clawworld.infrastructure.config.data.character.NpcShopItemConfig;
import com.heibai.clawworld.infrastructure.persistence.entity.NpcShopInstanceEntity;
import com.heibai.clawworld.infrastructure.persistence.repository.NpcShopInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商店刷新定时任务
 * 根据设计文档：商品的数量和NPC资金5分钟刷新一次
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShopRefreshScheduler {

    private final NpcShopInstanceRepository npcShopInstanceRepository;
    private final ConfigDataManager configDataManager;

    // 刷新间隔：5分钟
    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000;

    /**
     * 每分钟检查一次是否有商店需要刷新
     */
    @Scheduled(fixedRate = 60000)
    public void checkShopRefresh() {
        List<NpcShopInstanceEntity> shops = npcShopInstanceRepository.findAll();

        if (shops.isEmpty()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        int refreshedCount = 0;

        for (NpcShopInstanceEntity shop : shops) {
            Long lastRefreshTime = shop.getLastRefreshTime();
            if (lastRefreshTime == null) {
                lastRefreshTime = 0L;
            }

            // 检查是否需要刷新（距离上次刷新超过5分钟）
            if (currentTime - lastRefreshTime >= REFRESH_INTERVAL_MS) {
                refreshShop(shop);
                refreshedCount++;
            }
        }

        if (refreshedCount > 0) {
            log.info("刷新了 {} 个商店", refreshedCount);
        }
    }

    /**
     * 刷新单个商店
     */
    private void refreshShop(NpcShopInstanceEntity shop) {
        String npcId = shop.getNpcId();

        // 获取NPC配置
        NpcConfig npcConfig = configDataManager.getNpc(npcId);
        if (npcConfig == null || !npcConfig.isHasShop()) {
            return;
        }

        // 刷新NPC资金
        shop.setCurrentGold(npcConfig.getShopGold());

        // 刷新商品库存
        List<NpcShopItemConfig> shopItemConfigs = configDataManager.getNpcShopItems(npcId);
        for (NpcShopInstanceEntity.ShopItemData itemData : shop.getItems()) {
            // 查找对应的配置
            NpcShopItemConfig itemConfig = shopItemConfigs.stream()
                .filter(c -> c.getItemId().equals(itemData.getItemId()))
                .findFirst()
                .orElse(null);

            if (itemConfig != null) {
                // 恢复到最大库存
                itemData.setCurrentQuantity(itemConfig.getQuantity());
            }
        }

        // 更新刷新时间
        shop.setLastRefreshTime(System.currentTimeMillis());

        npcShopInstanceRepository.save(shop);
        log.debug("商店 {} 已刷新", npcId);
    }
}
