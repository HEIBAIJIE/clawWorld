package com.heibai.clawworld.interfaces.rest;

import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.infrastructure.config.data.map.TerrainTypeConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigDataManager configDataManager;

    @GetMapping("/terrain-types")
    public ResponseEntity<Collection<TerrainTypeConfig>> getTerrainTypes() {
        return ResponseEntity.ok(configDataManager.getAllTerrainTypes());
    }
}
