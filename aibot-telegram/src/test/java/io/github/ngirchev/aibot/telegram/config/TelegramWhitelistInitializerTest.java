package io.github.ngirchev.aibot.telegram.config;

import io.github.ngirchev.aibot.telegram.repository.TelegramWhitelistRepository;
import io.github.ngirchev.aibot.telegram.service.TelegramWhitelistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TelegramWhitelistInitializer.
 */
@ExtendWith(MockitoExtension.class)
class TelegramWhitelistInitializerTest {

    @Mock
    private TelegramWhitelistService whitelistService;
    @Mock
    private TelegramWhitelistRepository whitelistRepository;
    @Mock
    private TelegramProperties telegramProperties;

    private TelegramWhitelistInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new TelegramWhitelistInitializer(whitelistService, whitelistRepository, telegramProperties);
    }

    @Test
    void initWhitelistExceptions_whenEmpty_skipsAndDoesNotCallService() {
        when(telegramProperties.getWhitelistExceptionsSet()).thenReturn(Set.of());

        initializer.initWhitelistExceptions();

        verify(whitelistService, never()).addToWhitelist(anyLong());
        verify(whitelistRepository, never()).existsByUserId(anyLong());
    }

    @Test
    void initWhitelistExceptions_whenNullSet_skips() {
        when(telegramProperties.getWhitelistExceptionsSet()).thenReturn(null);

        initializer.initWhitelistExceptions();

        verify(whitelistService, never()).addToWhitelist(anyLong());
    }

    @Test
    void initWhitelistExceptions_whenUserNotInWhitelist_addsUser() {
        when(telegramProperties.getWhitelistExceptionsSet()).thenReturn(Set.of(1000L));
        when(whitelistRepository.existsByUserId(1000L)).thenReturn(false);

        initializer.initWhitelistExceptions();

        verify(whitelistRepository).existsByUserId(1000L);
        verify(whitelistService).addToWhitelist(1000L);
    }

    @Test
    void initWhitelistExceptions_whenUserAlreadyInWhitelist_skips() {
        when(telegramProperties.getWhitelistExceptionsSet()).thenReturn(Set.of(1000L));
        when(whitelistRepository.existsByUserId(1000L)).thenReturn(true);

        initializer.initWhitelistExceptions();

        verify(whitelistRepository).existsByUserId(1000L);
        verify(whitelistService, never()).addToWhitelist(anyLong());
    }

    @Test
    void initWhitelistExceptions_whenMultipleUsers_addsOnlyNewOnes() {
        when(telegramProperties.getWhitelistExceptionsSet()).thenReturn(Set.of(1000L, 2000L));
        when(whitelistRepository.existsByUserId(1000L)).thenReturn(false);
        when(whitelistRepository.existsByUserId(2000L)).thenReturn(true);

        initializer.initWhitelistExceptions();

        verify(whitelistService).addToWhitelist(1000L);
        verify(whitelistService, never()).addToWhitelist(eq(2000L));
    }
}
