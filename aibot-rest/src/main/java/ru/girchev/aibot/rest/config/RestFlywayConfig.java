package ru.girchev.aibot.rest.config;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Конфигурация Flyway для REST модуля
 * Миграции выполняются только если включен REST модуль
 */
@Configuration
@DependsOn("coreFlyway")
public class RestFlywayConfig {

    private final DataSource dataSource;

    public RestFlywayConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Bean(value = "restFlyway", initMethod = "migrate")
    public Flyway restFlyway() {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/rest")
                .table("flyway_schema_history_rest")
                .baselineVersion("0")
                .baselineOnMigrate(true)
                .validateOnMigrate(true)
                .load();
    }
}

