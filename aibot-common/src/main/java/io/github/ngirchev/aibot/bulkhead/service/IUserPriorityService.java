package io.github.ngirchev.aibot.bulkhead.service;

import io.github.ngirchev.aibot.bulkhead.model.UserPriority;

/**
 * Interface for resolving user priority.
 * Isolated from chat business logic and reusable in other services.
 */
public interface IUserPriorityService {
    
    /**
     * Resolves user priority by user id (Telegram).
     *
     * @param userId user identifier (Telegram user ID)
     * @return user priority (ADMIN, VIP, REGULAR or BLOCKED)
     */
    UserPriority getUserPriority(Long userId);

    /**
     * Resolves user priority by email (REST).
     *
     * @param email user email
     * @return user priority (ADMIN, VIP, REGULAR or BLOCKED)
     */
    default UserPriority getUserPriorityByEmail(String email) {
        return UserPriority.REGULAR;
    }
}