package com.heibai.clawworld.config;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MongoConnectionTest {

    private static final Logger log = LoggerFactory.getLogger(MongoConnectionTest.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void testMongoConnection() {
        log.info("=== MongoDB连接测试 ===");

        try {
            // 测试连接
            String dbName = mongoTemplate.getDb().getName();
            assertNotNull(dbName);
            log.info("✓ 成功连接到MongoDB数据库: {}", dbName);
            assertEquals("clawworld", dbName);

            // 获取集合列表
            var collections = mongoTemplate.getDb().listCollectionNames();
            assertNotNull(collections);
            log.info("✓ 当前数据库中的集合:");
            collections.forEach(name -> log.info("  - {}", name));

            // 测试ping
            var result = mongoTemplate.getDb().runCommand(new org.bson.Document("ping", 1));
            assertNotNull(result);
            assertEquals(1.0, result.get("ok"));
            log.info("✓ MongoDB连接正常，可以执行操作");

        } catch (Exception e) {
            log.error("✗ MongoDB连接失败: {}", e.getMessage());
            fail("MongoDB连接失败: " + e.getMessage());
        }
    }

    @Test
    void testMongoTemplateNotNull() {
        assertNotNull(mongoTemplate, "MongoTemplate应该被正确注入");
    }
}
