package ru.girchev.aibot.bulkhead.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import ru.girchev.aibot.bulkhead.model.UserPriority;

import java.util.EnumMap;
import java.util.Map;
import java.time.Duration;

@ConfigurationProperties(prefix = "ai-bot.common.bulkhead")
@Validated
@Getter
@Setter
public class BulkHeadProperties {

    private Map<UserPriority, BulkheadInstance> instances = new EnumMap<>(UserPriority.class);

    /**
     * Размер внутреннего пула потоков в {@code PriorityRequestExecutor}, в котором реально выполняются задачи.
     * Если 0 или меньше — будет рассчитан автоматически как сумма maxConcurrentCalls по всем приоритетам.
     */
    private int executorThreads;
    
    public record BulkheadInstance(int maxConcurrentCalls, Duration maxWaitDuration) {
    }
} 