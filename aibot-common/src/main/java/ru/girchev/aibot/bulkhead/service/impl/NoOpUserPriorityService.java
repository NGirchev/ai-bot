package ru.girchev.aibot.bulkhead.service.impl;

import ru.girchev.aibot.bulkhead.model.UserPriority;
import ru.girchev.aibot.bulkhead.service.IUserPriorityService;

/**
 * NoOp реализация IUserPriorityService.
 * Используется когда bulkhead выключен (ai-bot.common.bulkhead.enabled=false).
 * Всегда возвращает приоритет REGULAR для любого пользователя.
 */
public class NoOpUserPriorityService implements IUserPriorityService {

    @Override
    public UserPriority getUserPriority(Long userId) {
        return UserPriority.REGULAR;
    }
}
