package com.heibai.clawworld.infrastructure.scheduler;

import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.infrastructure.persistence.entity.EnemyInstanceEntity;
import com.heibai.clawworld.infrastructure.persistence.repository.EnemyInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 敌人刷新定时任务
 * 根据设计文档：敌人被击败后短暂消失，然后根据刷新时间定时刷回来
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnemyRespawnScheduler {

    private final EnemyInstanceRepository enemyInstanceRepository;
    private final ConfigDataManager configDataManager;

    /**
     * 每10秒检查一次是否有敌人需要刷新
     */
    @Scheduled(fixedRate = 10000)
    public void checkEnemyRespawn() {
        List<EnemyInstanceEntity> deadEnemies = enemyInstanceRepository.findAll().stream()
            .filter(EnemyInstanceEntity::isDead)
            .filter(e -> e.getLastDeathTime() != null)
            .toList();

        if (deadEnemies.isEmpty()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        int respawnedCount = 0;

        for (EnemyInstanceEntity enemy : deadEnemies) {
            // 获取敌人配置以获取刷新时间
            var enemyConfig = configDataManager.getEnemy(enemy.getTemplateId());
            if (enemyConfig == null) {
                continue;
            }

            int respawnSeconds = enemyConfig.getRespawnSeconds();
            long deathTime = enemy.getLastDeathTime();
            long respawnTime = deathTime + (respawnSeconds * 1000L);

            if (currentTime >= respawnTime) {
                // 刷新敌人
                enemy.setDead(false);
                enemy.setLastDeathTime(null);
                enemy.setCurrentHealth(enemyConfig.getHealth());
                enemy.setCurrentMana(enemyConfig.getMana());
                enemy.setInCombat(false);
                enemy.setCombatId(null);
                enemyInstanceRepository.save(enemy);
                respawnedCount++;
                log.debug("敌人 {} 已刷新", enemy.getDisplayName());
            }
        }

        if (respawnedCount > 0) {
            log.info("刷新了 {} 个敌人", respawnedCount);
        }
    }
}
