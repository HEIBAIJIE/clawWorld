package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.application.service.EquipmentInstanceService;
import com.heibai.clawworld.infrastructure.persistence.entity.EquipmentCounterEntity;
import com.heibai.clawworld.infrastructure.persistence.repository.EquipmentCounterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 装备实例编号服务实现
 * 为每种装备生成唯一的递增实例编号
 */
@Service
@RequiredArgsConstructor
public class EquipmentInstanceServiceImpl implements EquipmentInstanceService {

    private final EquipmentCounterRepository counterRepository;

    @Override
    @Transactional
    public Long getNextInstanceNumber(String equipmentId) {
        Optional<EquipmentCounterEntity> counterOpt = counterRepository.findById(equipmentId);

        EquipmentCounterEntity counter;
        if (counterOpt.isPresent()) {
            counter = counterOpt.get();
            counter.setCurrentNumber(counter.getCurrentNumber() + 1);
        } else {
            counter = new EquipmentCounterEntity();
            counter.setEquipmentId(equipmentId);
            counter.setCurrentNumber(1L);
        }

        counterRepository.save(counter);
        return counter.getCurrentNumber();
    }
}
