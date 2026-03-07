package ru.girchev.aibot.common.service;

import ru.girchev.aibot.common.model.AssistantRole;
import ru.girchev.aibot.common.model.User;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Сервис для управления ролями ассистента с версионированием
 */
public interface AssistantRoleService {
    
    /**
     * Получает активную роль для пользователя
     * 
     * @param user пользователь
     * @return активная роль или Optional.empty()
     */
    Optional<AssistantRole> getActiveRole(User user);
    
    /**
     * Создает или возвращает существующую роль с таким же содержанием
     * Если роль с таким содержанием уже существует, возвращает её
     * Если нет - создает новую версию
     * 
     * @param user пользователь
     * @param content содержание роли
     * @return роль
     */
    AssistantRole createOrGetRole(User user, String content);
    
    /**
     * Устанавливает роль как активную для пользователя
     * Деактивирует все остальные роли пользователя
     * 
     * @param role роль для активации
     */
    void setActiveRole(AssistantRole role);
    
    /**
     * Обновляет активную роль пользователя
     * Если содержание не изменилось - возвращает текущую роль
     * Если изменилось - создает новую версию или активирует существующую
     * 
     * @param user пользователь
     * @param content новое содержание роли
     * @return активная роль
     */
    AssistantRole updateActiveRole(User user, String content);
    
    /**
     * Увеличивает счетчик использования роли
     * 
     * @param role роль
     */
    void incrementUsage(AssistantRole role);
    
    /**
     * Получает все роли пользователя
     * 
     * @param user пользователь
     * @return список ролей отсортированный по версии (от новых к старым)
     */
    List<AssistantRole> getAllUserRoles(User user);
    
    /**
     * Получает роль по версии
     * 
     * @param user пользователь
     * @param version версия роли
     * @return роль или Optional.empty()
     */
    Optional<AssistantRole> getRoleByVersion(User user, Integer version);
    
    /**
     * Удаляет неиспользуемые роли (не активные, без запросов, старше указанного периода)
     * 
     * @param thresholdDate дата, роли старше которой будут удалены
     * @return количество удаленных ролей
     */
    int cleanupUnusedRoles(OffsetDateTime thresholdDate);
    
    /**
     * Получает список неиспользуемых ролей
     * 
     * @param thresholdDate дата, роли старше которой считаются неиспользуемыми
     * @return список неиспользуемых ролей
     */
    List<AssistantRole> findUnusedRoles(OffsetDateTime thresholdDate);
    
    /**
     * Получает роль по умолчанию для пользователя
     * Если у пользователя нет активной роли, создает новую с дефолтным содержанием
     * 
     * @param user пользователь
     * @param defaultContent содержание роли по умолчанию
     * @return активная роль
     */
    AssistantRole getOrCreateDefaultRole(User user, String defaultContent);
    
    /**
     * Получает роль по ID
     * 
     * @param roleId ID роли
     * @return роль или Optional.empty()
     */
    Optional<AssistantRole> findById(Long roleId);
}

