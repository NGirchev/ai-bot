package io.github.ngirchev.aibot.rest.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.HashSet;
import java.util.Set;


@Slf4j
@Validated
@Getter
@Setter
@ConfigurationProperties(prefix = "ai-bot.rest")
public class RestProperties {

    @Valid
    private AccessConfig access = new AccessConfig();

    @Getter
    @Setter
    public static class AccessConfig {
        private LevelConfig admin = new LevelConfig();
        private LevelConfig vip = new LevelConfig();
        private LevelConfig regular = new LevelConfig();

        @Getter
        @Setter
        public static class LevelConfig {
            private Set<String> ids = new HashSet<>();
            private Set<String> emails = new HashSet<>();
        }
    }

    @PostConstruct
    public void parseAccessConfig() {
        parseLevelConfig(access.getAdmin(), "admin");
        parseLevelConfig(access.getVip(), "vip");
        parseLevelConfig(access.getRegular(), "regular");
    }

    private void parseLevelConfig(AccessConfig.LevelConfig level, String levelName) {
        if (level == null) {
            return;
        }
        level.setIds(level.getIds() != null ? level.getIds() : new HashSet<>());
        level.setEmails(level.getEmails() != null ? level.getEmails() : new HashSet<>());
        log.info("REST access config {}: ids={}, emails={}", 
                levelName, level.getIds(), level.getEmails());
    }
}
