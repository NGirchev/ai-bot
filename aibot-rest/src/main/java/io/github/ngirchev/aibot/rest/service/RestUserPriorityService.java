package io.github.ngirchev.aibot.rest.service;

import io.github.ngirchev.aibot.bulkhead.model.UserPriority;
import io.github.ngirchev.aibot.bulkhead.service.IUserPriorityService;
import io.github.ngirchev.aibot.rest.config.RestProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;


@Slf4j
@RequiredArgsConstructor
public class RestUserPriorityService implements IUserPriorityService {

    private final IUserPriorityService delegate;
    private final RestProperties restProperties;

    @Override
    public UserPriority getUserPriority(Long userId) {
        return delegate.getUserPriority(userId);
    }

    @Override
    public UserPriority getUserPriorityByEmail(String email) {
        if (email == null || email.isBlank()) {
            return UserPriority.BLOCKED;
        }

        RestProperties.AccessConfig access = restProperties.getAccess();
        Set<String> adminEmails = access.getAdmin().getEmails();
        Set<String> vipEmails = access.getVip().getEmails();
        Set<String> regularEmails = access.getRegular().getEmails();

        if (adminEmails.contains(email)) {
            return UserPriority.ADMIN;
        }

        if (vipEmails.contains(email)) {
            return UserPriority.VIP;
        }

        if (regularEmails.contains(email)) {
            return UserPriority.REGULAR;
        }

        return UserPriority.REGULAR;
    }
}
