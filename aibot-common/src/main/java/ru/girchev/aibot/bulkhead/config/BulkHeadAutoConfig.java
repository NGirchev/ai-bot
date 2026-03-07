package ru.girchev.aibot.bulkhead.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import ru.girchev.aibot.bulkhead.service.PriorityRequestExecutor;
import ru.girchev.aibot.bulkhead.service.IUserPriorityService;
import ru.girchev.aibot.bulkhead.service.IUserService;
import ru.girchev.aibot.bulkhead.service.IWhitelistService;
import ru.girchev.aibot.bulkhead.service.impl.DefaultUserPriorityService;

@AutoConfiguration
@EnableConfigurationProperties(BulkHeadProperties.class)
@ConditionalOnProperty(name = "ai-bot.common.bulkhead.enabled", havingValue = "true")
public class BulkHeadAutoConfig {

    @Bean
    @ConditionalOnMissingBean
    public IUserPriorityService userPriorityService(
            IUserService userService,
            IWhitelistService whitelistService) {
        return new DefaultUserPriorityService(userService, whitelistService);
    }

    @Bean
    @ConditionalOnMissingBean
    public PriorityRequestExecutor priorityRequestExecutor(
            IUserPriorityService userPriorityService,
            BulkHeadProperties bulkHeadProperties) {
        return new PriorityRequestExecutor(userPriorityService, bulkHeadProperties);
    }
}
