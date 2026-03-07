package ru.girchev.aibot;

import io.github.ngirchev.dotenv.DotEnvLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.girchev.aibot.flyway.FlywayMigrationCheck;
import ru.girchev.aibot.flyway.config.FlywayConfig;

/**
 * Главное приложение AI Bot Router
 * JPA конфигурации вынесены в модульные конфигурации:
 * - CommonJpaConfig (aibot-common) - базовые Entity и репозитории
 * - TelegramJpaConfig (aibot-telegram) - Telegram Entity и репозитории (условный)
 * - RestJpaConfig (aibot-rest) - REST Entity и репозитории (условный)
 */
@Slf4j
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = JpaRepositoriesAutoConfiguration.class)
@EnableScheduling
@Import({
        FlywayConfig.class,
        FlywayMigrationCheck.class
})
public class Application {

    public static void main(String[] args) {
        DotEnvLoader.loadDotEnv();
        // Отключаем детальные стектрейсы Reactor для сокращения логов
        System.setProperty("reactor.trace.operatorStacktrace", "false");
        SpringApplication.run(Application.class, args);
    }
}