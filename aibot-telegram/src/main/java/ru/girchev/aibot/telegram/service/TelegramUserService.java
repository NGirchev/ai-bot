package ru.girchev.aibot.telegram.service;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.User;
import ru.girchev.aibot.bulkhead.service.IUserService;
import ru.girchev.aibot.common.model.AssistantRole;
import ru.girchev.aibot.common.service.AssistantRoleService;
import ru.girchev.aibot.telegram.model.TelegramUser;
import ru.girchev.aibot.telegram.model.TelegramUserSession;
import ru.girchev.aibot.telegram.repository.TelegramUserRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

@RequiredArgsConstructor
public class TelegramUserService implements IUserService {
    private final TelegramUserRepository telegramUserRepository;
    private final TelegramUserSessionService telegramUserSessionService;
    private final AssistantRoleService assistantRoleService;

    @Override
    public Optional<TelegramUser> findById(Long id) {
        return telegramUserRepository.findById(id);
    }

    public Optional<TelegramUser> findByTelegramId(Long telegramId) {
        return telegramUserRepository.findByTelegramId(telegramId);
    }

    @Transactional
    public TelegramUser getOrCreateUser(User telegramUser) {
        return getOrCreateUserInner(telegramUser);
    }

    @Transactional
    public TelegramUser updateUserActivity(TelegramUser user) {
        user.setLastActivityAt(OffsetDateTime.now());
        return telegramUserRepository.save(user);
    }

    @Transactional
    public TelegramUser createUser(User telegramUser) {
        return createUserInner(telegramUser);
    }

    /**
     * Обновляет роль ассистента для пользователя
     * @param telegramUser telegram пользователь
     * @param assistantRoleContent новое содержание роли
     * @return обновленный пользователь
     */
    @Transactional
    public TelegramUser updateAssistantRole(User telegramUser, String assistantRoleContent) {
        TelegramUser user = telegramUserRepository.findByTelegramId(telegramUser.getId())
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
        
        // Добавляем универсальное требование учитывать локаль пользователя
        String languageCode = user.getLanguageCode() != null ? user.getLanguageCode() : "ru";
        String enhancedRoleContent = addLanguageRequirement(assistantRoleContent, languageCode);
        
        // Обновляем роль через сервис (создаст новую версию или активирует существующую)
        AssistantRole role = assistantRoleService.updateActiveRole(user, enhancedRoleContent);
        
        // Устанавливаем ссылку на активную роль
        user.setCurrentAssistantRole(role);
        
        return telegramUserRepository.save(user);
    }
    
    /**
     * Добавляет универсальное требование учитывать локаль пользователя к промпту роли.
     */
    private String addLanguageRequirement(String roleContent, String languageCode) {
        if (roleContent == null || roleContent.trim().isEmpty()) {
            return roleContent;
        }
        
        String normalizedLangCode = languageCode != null ? languageCode.toLowerCase().split("-")[0] : "ru";
        String languageRequirement = String.format(
                " Учитывай локаль пользователя [%s] чтобы всегда отвечать на соответствующем языке, кроме тех случаев когда пользователь просит об ином.",
                normalizedLangCode);
        
        String enhanced = roleContent.trim();
        if (!enhanced.endsWith(".") && !enhanced.endsWith("!") && !enhanced.endsWith("?")) {
            enhanced += ".";
        }
        enhanced += languageRequirement;
        
        return enhanced;
    }
    
    /**
     * Получает активную роль ассистента для пользователя
     * @param user пользователь
     * @param defaultContent содержание роли по умолчанию
     * @return активная роль
     */
    @Transactional
    public AssistantRole getOrCreateAssistantRole(TelegramUser user, String defaultContent) {
        // Важно: сюда часто приходит detatched user (метод вызывается из bulkhead потока).
        // Поэтому сначала заново загружаем пользователя в текущую сессию, а роль - инициализируем.
        Long telegramId = user.getTelegramId();
        if (telegramId == null) {
            throw new IllegalArgumentException("telegramId is null");
        }

        TelegramUser managedUser = telegramUserRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        // Проверяем, есть ли связь с ролью
        AssistantRole role = managedUser.getCurrentAssistantRole();
        if (role == null) {
            // Добавляем универсальное требование учитывать локаль пользователя
            String languageCode = managedUser.getLanguageCode() != null ? managedUser.getLanguageCode() : "ru";
            String enhancedDefaultContent = addLanguageRequirement(defaultContent, languageCode);
            
            // Получаем или создаем роль
            role = assistantRoleService.getOrCreateDefaultRole(managedUser, enhancedDefaultContent);

            // Сохраняем ссылку в пользователе
            managedUser.setCurrentAssistantRole(role);
            telegramUserRepository.save(managedUser);
        }

        // Принудительно инициализируем нужные поля роли внутри транзакции,
        // чтобы потом безопасно использовать их вне Hibernate Session
        role.getId();
        role.getVersion();
        role.getContent();

        return role;
    }

    /**
     * Обновляет статус бота в текущей активной сессии пользователя.
     *
     * @param user пользователь
     * @param botStatus новый статус бота
     */
    @Transactional
    public void updateUserSession(TelegramUser user, String botStatus) {
        telegramUserSessionService.updateSessionStatus(user, botStatus);
    }

    @Transactional
    public TelegramUserSession getOrCreateSession(User telegramUser) {
        TelegramUser user = getOrCreateUserInner(telegramUser);
        return telegramUserSessionService.getOrCreateSession(user);
    }

    @Transactional
    public Optional<TelegramUserSession> tryToGetSession(Long userId) {
        return findByTelegramId(userId).map(telegramUserSessionService::getOrCreateSession);
    }

    private TelegramUser getOrCreateUserInner(User telegramUser) {
        Optional<TelegramUser> existingUser = telegramUserRepository.findByTelegramId(telegramUser.getId());

        if (existingUser.isPresent()) {
            TelegramUser user = existingUser.get();
            updateUserInfo(user, telegramUser);
            return telegramUserRepository.save(user);
        } else {
            return createUserInner(telegramUser);
        }
    }

    private TelegramUser createUserInner(User telegramUser) {
        TelegramUser user = new TelegramUser();
        user.setTelegramId(telegramUser.getId());
        user.setUsername(telegramUser.getUserName());
        user.setFirstName(telegramUser.getFirstName());
        user.setLastName(telegramUser.getLastName());
        user.setLanguageCode(telegramUser.getLanguageCode());
        user.setIsPremium(telegramUser.getIsPremium());
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        user.setLastActivityAt(OffsetDateTime.now());
        user.setIsBlocked(false);
        return telegramUserRepository.save(user);
    }

    private void updateUserInfo(TelegramUser user, User telegramUser) {
        String username = telegramUser.getUserName();
        if (username != null) {
            user.setUsername(username);
        }
        String firstName = telegramUser.getFirstName();
        if (firstName != null) {
            user.setFirstName(firstName);
        }
        String lastName = telegramUser.getLastName();
        if (lastName != null) {
            user.setLastName(lastName);
        }
        String languageCode = telegramUser.getLanguageCode();
        if (languageCode != null) {
            user.setLanguageCode(languageCode);
        }
        Boolean isPremium = telegramUser.getIsPremium();
        if (isPremium != null) {
            user.setIsPremium(isPremium);
        }
        user.setLastActivityAt(OffsetDateTime.now());
    }
}
