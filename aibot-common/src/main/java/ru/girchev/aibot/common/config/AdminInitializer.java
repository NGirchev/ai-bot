package ru.girchev.aibot.common.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Компонент для инициализации администратора при старте приложения.
 * Создает администратора на основе конфигурации (Telegram ID или REST email).
 */
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai-bot.common.admin.enabled", havingValue = "true", matchIfMissing = false)
public class AdminInitializer {

    private final CoreCommonProperties coreCommonProperties;
    private final ApplicationContext applicationContext;

    @PostConstruct
    @Transactional
    public void initAdmin() {
        log.info("Инициализация администратора...");
        
        CoreCommonProperties.AdminProperties adminProperties = coreCommonProperties.getAdmin();
        if (adminProperties == null) {
            log.warn("Конфигурация администратора не найдена, пропускаем инициализацию");
            return;
        }
        
        // Проверяем, указан ли Telegram ID администратора
        if (adminProperties.getTelegramId() != null) {
            Object telegramRepo = getBeanByClassName("ru.girchev.aibot.telegram.repository.TelegramUserRepository");
            if (telegramRepo != null) {
                initTelegramAdmin(adminProperties.getTelegramId(), telegramRepo);
            }
        }
        
        // Проверяем, указан ли REST администратор (email)
        if (adminProperties.getRestEmail() != null) {
            Object restRepo = getBeanByClassName("ru.girchev.aibot.rest.repository.RestUserRepository");
            if (restRepo != null) {
                initRestAdmin(adminProperties.getRestEmail(), restRepo);
            }
        }
        
        log.info("Инициализация администратора завершена");
    }

    @SuppressWarnings("unchecked")
    private void initTelegramAdmin(Long telegramId, Object repository) {
        log.info("Создание/обновление Telegram администратора с ID: {}", telegramId);
        
        try {
            // Используем рефлексию для работы с TelegramUserRepository
            java.lang.reflect.Method findByTelegramId = repository.getClass().getMethod("findByTelegramId", Long.class);
            Optional<Object> existingUserOpt = (Optional<Object>) findByTelegramId.invoke(repository, telegramId);
            
            if (existingUserOpt.isPresent()) {
                Object user = existingUserOpt.get();
                java.lang.reflect.Method getIsAdmin = user.getClass().getMethod("getIsAdmin");
                Boolean isAdmin = (Boolean) getIsAdmin.invoke(user);
                
                if (!Boolean.TRUE.equals(isAdmin)) {
                    user.getClass().getMethod("setIsAdmin", Boolean.class).invoke(user, true);
                    user.getClass().getMethod("setIsPremium", Boolean.class).invoke(user, true);
                    user.getClass().getMethod("setIsBlocked", Boolean.class).invoke(user, false);
                    repository.getClass().getMethod("save", Object.class).invoke(repository, user);
                    log.info("Пользователь Telegram с ID {} обновлен до администратора", telegramId);
                } else {
                    log.info("Пользователь Telegram с ID {} уже является администратором", telegramId);
                }
            } else {
                // Создаем нового администратора
                Object admin = Class.forName("ru.girchev.aibot.telegram.model.TelegramUser").getDeclaredConstructor().newInstance();
                admin.getClass().getMethod("setTelegramId", Long.class).invoke(admin, telegramId);
                admin.getClass().getMethod("setUsername", String.class).invoke(admin, "admin_" + telegramId);
                admin.getClass().getMethod("setIsAdmin", Boolean.class).invoke(admin, true);
                admin.getClass().getMethod("setIsPremium", Boolean.class).invoke(admin, true);
                admin.getClass().getMethod("setIsBlocked", Boolean.class).invoke(admin, false);
                admin.getClass().getMethod("setCreatedAt", OffsetDateTime.class).invoke(admin, OffsetDateTime.now());
                admin.getClass().getMethod("setUpdatedAt", OffsetDateTime.class).invoke(admin, OffsetDateTime.now());
                admin.getClass().getMethod("setLastActivityAt", OffsetDateTime.class).invoke(admin, OffsetDateTime.now());
                repository.getClass().getMethod("save", Object.class).invoke(repository, admin);
                log.info("Создан новый Telegram администратор с ID: {}", telegramId);
            }
        } catch (Exception e) {
            log.error("Ошибка при создании Telegram администратора", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void initRestAdmin(String email, Object repository) {
        log.info("Создание/обновление REST администратора с email: {}", email);
        
        try {
            // Используем рефлексию для работы с RestUserRepository
            java.lang.reflect.Method findByEmail = repository.getClass().getMethod("findByEmail", String.class);
            Optional<Object> existingUserOpt = (Optional<Object>) findByEmail.invoke(repository, email);
            
            if (existingUserOpt.isPresent()) {
                Object user = existingUserOpt.get();
                java.lang.reflect.Method getIsAdmin = user.getClass().getMethod("getIsAdmin");
                Boolean isAdmin = (Boolean) getIsAdmin.invoke(user);
                
                if (!Boolean.TRUE.equals(isAdmin)) {
                    user.getClass().getMethod("setIsAdmin", Boolean.class).invoke(user, true);
                    user.getClass().getMethod("setIsPremium", Boolean.class).invoke(user, true);
                    user.getClass().getMethod("setIsBlocked", Boolean.class).invoke(user, false);
                    repository.getClass().getMethod("save", Object.class).invoke(repository, user);
                    log.info("Пользователь REST с email {} обновлен до администратора", email);
                } else {
                    log.info("Пользователь REST с email {} уже является администратором", email);
                }
            } else {
                // Создаем нового администратора
                Object admin = Class.forName("ru.girchev.aibot.rest.model.RestUser").getDeclaredConstructor().newInstance();
                admin.getClass().getMethod("setEmail", String.class).invoke(admin, email);
                admin.getClass().getMethod("setUsername", String.class).invoke(admin, email);
                admin.getClass().getMethod("setIsAdmin", Boolean.class).invoke(admin, true);
                admin.getClass().getMethod("setIsPremium", Boolean.class).invoke(admin, true);
                admin.getClass().getMethod("setIsBlocked", Boolean.class).invoke(admin, false);
                admin.getClass().getMethod("setCreatedAt", OffsetDateTime.class).invoke(admin, OffsetDateTime.now());
                admin.getClass().getMethod("setUpdatedAt", OffsetDateTime.class).invoke(admin, OffsetDateTime.now());
                admin.getClass().getMethod("setLastActivityAt", OffsetDateTime.class).invoke(admin, OffsetDateTime.now());
                repository.getClass().getMethod("save", Object.class).invoke(repository, admin);
                log.info("Создан новый REST администратор с email: {} (API ключ можно установить позже)", email);
            }
        } catch (Exception e) {
            log.error("Ошибка при создании REST администратора", e);
        }
    }
    
    private Object getBeanByClassName(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            String[] beanNames = applicationContext.getBeanNamesForType(clazz);
            if (beanNames.length > 0) {
                return applicationContext.getBean(beanNames[0]);
            }
        } catch (Exception e) {
            // Класс не найден или бин не существует - это нормально для опциональных модулей
            log.debug("Бин {} не найден, пропускаем", className);
        }
        return null;
    }
}

