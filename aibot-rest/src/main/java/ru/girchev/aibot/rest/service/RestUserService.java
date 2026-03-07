package ru.girchev.aibot.rest.service;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import ru.girchev.aibot.common.model.AssistantRole;
import ru.girchev.aibot.common.service.AssistantRoleService;
import ru.girchev.aibot.rest.model.RestUser;
import ru.girchev.aibot.rest.repository.RestUserRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

@RequiredArgsConstructor
public class RestUserService {
    
    private final RestUserRepository restUserRepository;
    private final AssistantRoleService assistantRoleService;
    
    /**
     * Получает или создает пользователя по email
     */
    @Transactional
    public RestUser getOrCreateUser(String email) {
        return restUserRepository.findByEmail(email)
            .orElseGet(() -> {
                RestUser newUser = new RestUser();
                newUser.setEmail(email);
                newUser.setUsername(email);
                newUser.setCreatedAt(OffsetDateTime.now());
                newUser.setUpdatedAt(OffsetDateTime.now());
                newUser.setLastActivityAt(OffsetDateTime.now());
                return restUserRepository.save(newUser);
            });
    }
    
    /**
     * Получает активную роль ассистента для пользователя
     * @param user пользователь
     * @param defaultContent содержание роли по умолчанию
     * @return активная роль
     */
    @Transactional
    public AssistantRole getOrCreateAssistantRole(RestUser user, String defaultContent) {
        // Важно: сюда часто приходит detatched user (метод вызывается из bulkhead потока).
        // Поэтому сначала заново загружаем пользователя в текущую сессию, а роль - инициализируем.
        String email = user.getEmail();
        if (email == null) {
            throw new IllegalArgumentException("email is null");
        }

        RestUser managedUser = restUserRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        // Проверяем, есть ли связь с ролью
        AssistantRole role = managedUser.getCurrentAssistantRole();
        if (role == null) {
            // Получаем или создаем роль
            role = assistantRoleService.getOrCreateDefaultRole(managedUser, defaultContent);

            // Сохраняем ссылку в пользователе
            managedUser.setCurrentAssistantRole(role);
            restUserRepository.save(managedUser);
        }

        // Принудительно инициализируем нужные поля роли внутри транзакции,
        // чтобы потом безопасно использовать их вне Hibernate Session
        role.getId();
        role.getVersion();
        role.getContent();

        return role;
    }
    
    /**
     * Находит пользователя по email
     */
    public Optional<RestUser> findByEmail(String email) {
        return restUserRepository.findByEmail(email);
    }
    
    /**
     * Находит пользователя по id
     */
    public Optional<RestUser> findById(Long id) {
        return restUserRepository.findById(id);
    }
}

