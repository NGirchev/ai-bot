package ru.girchev.aibot.bulkhead.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import ru.girchev.aibot.bulkhead.model.UserPriority;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для {@link BulkHeadProperties}.
 */
@SpringBootTest(classes = BulkHeadPropertiesTest.TestConfiguration.class)
@TestPropertySource(properties = {
        "ai-bot.common.bulkhead.instances.ADMIN.maxConcurrentCalls=20",
        "ai-bot.common.bulkhead.instances.ADMIN.maxWaitDuration=1s",
        "ai-bot.common.bulkhead.instances.VIP.maxConcurrentCalls=10",
        "ai-bot.common.bulkhead.instances.VIP.maxWaitDuration=1s",
        "ai-bot.common.bulkhead.instances.REGULAR.maxConcurrentCalls=5",
        "ai-bot.common.bulkhead.instances.REGULAR.maxWaitDuration=500ms",
        "ai-bot.common.bulkhead.instances.BLOCKED.maxConcurrentCalls=0",
        "ai-bot.common.bulkhead.instances.BLOCKED.maxWaitDuration=0ms"
})
class BulkHeadPropertiesTest {

    @EnableConfigurationProperties(BulkHeadProperties.class)
    static class TestConfiguration {
    }

    @Autowired
    private BulkHeadProperties properties;

    @Test
    void testBulkHeadProperties_ShouldLoadAllInstances() {
        // Assert
        Map<UserPriority, BulkHeadProperties.BulkheadInstance> instances = properties.getInstances();
        
        assertNotNull(instances, "Instances не должны быть null");
        assertEquals(4, instances.size(), "Должно быть 4 экземпляра (ADMIN, VIP, REGULAR, BLOCKED)");
        
        assertTrue(instances.containsKey(UserPriority.ADMIN), "Должен содержать экземпляр для ADMIN");
        assertTrue(instances.containsKey(UserPriority.VIP), "Должен содержать экземпляр для VIP");
        assertTrue(instances.containsKey(UserPriority.REGULAR), "Должен содержать экземпляр для REGULAR");
        assertTrue(instances.containsKey(UserPriority.BLOCKED), "Должен содержать экземпляр для BLOCKED");
    }

    @Test
    void testAdminInstance_ShouldHaveCorrectValues() {
        // Arrange
        BulkHeadProperties.BulkheadInstance adminInstance = properties.getInstances().get(UserPriority.ADMIN);

        // Assert
        assertNotNull(adminInstance, "ADMIN экземпляр не должен быть null");
        assertEquals(20, adminInstance.maxConcurrentCalls(), "ADMIN должен иметь 20 максимальных одновременных вызовов");
        assertEquals(Duration.ofSeconds(1), adminInstance.maxWaitDuration(), "ADMIN должен иметь максимальное время ожидания 1 секунду");
    }

    @Test
    void testVipInstance_ShouldHaveCorrectValues() {
        // Arrange
        BulkHeadProperties.BulkheadInstance vipInstance = properties.getInstances().get(UserPriority.VIP);
        
        // Assert
        assertNotNull(vipInstance, "VIP экземпляр не должен быть null");
        assertEquals(10, vipInstance.maxConcurrentCalls(), "VIP должен иметь 10 максимальных одновременных вызовов");
        assertEquals(Duration.ofSeconds(1), vipInstance.maxWaitDuration(), "VIP должен иметь максимальное время ожидания 1 секунду");
    }

    @Test
    void testRegularInstance_ShouldHaveCorrectValues() {
        // Arrange
        BulkHeadProperties.BulkheadInstance regularInstance = properties.getInstances().get(UserPriority.REGULAR);
        
        // Assert
        assertNotNull(regularInstance, "REGULAR экземпляр не должен быть null");
        assertEquals(5, regularInstance.maxConcurrentCalls(), "REGULAR должен иметь 5 максимальных одновременных вызовов");
        assertEquals(Duration.ofMillis(500), regularInstance.maxWaitDuration(), "REGULAR должен иметь максимальное время ожидания 500 миллисекунд");
    }

    @Test
    void testBlockedInstance_ShouldHaveCorrectValues() {
        // Arrange
        BulkHeadProperties.BulkheadInstance blockedInstance = properties.getInstances().get(UserPriority.BLOCKED);
        
        // Assert
        assertNotNull(blockedInstance, "BLOCKED экземпляр не должен быть null");
        assertEquals(0, blockedInstance.maxConcurrentCalls(), "BLOCKED должен иметь 0 максимальных одновременных вызовов");
        assertEquals(Duration.ZERO, blockedInstance.maxWaitDuration(), "BLOCKED должен иметь максимальное время ожидания 0 миллисекунд");
    }
} 
