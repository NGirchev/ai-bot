package ru.girchev.aibot.bulkhead.service;

import ru.girchev.aibot.bulkhead.model.UserPriority;

/**
 * Интерфейс для определения приоритета пользователя.
 * Этот интерфейс изолирован от бизнес-логики чата и может быть переиспользован в других сервисах.
 */
public interface IUserPriorityService {
    
    /**
     * Определяет приоритет пользователя по его идентификатору.
     *
     * @param userId идентификатор пользователя
     * @return приоритет пользователя (ADMIN, VIP, REGULAR или BLOCKED)
     */
    UserPriority getUserPriority(Long userId);
}