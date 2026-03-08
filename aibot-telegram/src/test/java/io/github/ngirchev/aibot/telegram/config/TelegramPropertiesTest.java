package io.github.ngirchev.aibot.telegram.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TelegramProperties.
 * Verifies whitelist parsing and optional fields.
 */
class TelegramPropertiesTest {

    private TelegramProperties properties;

    @BeforeEach
    void setUp() {
        properties = new TelegramProperties();
        properties.setToken("test-token");
        properties.setUsername("test-bot");
        properties.setMaxMessageLength(4096);
    }

    @Test
    void parseWhitelistExceptions_whenEmpty_thenSetIsEmpty() {
        properties.setWhitelistExceptions(null);
        properties.parseWhitelistExceptions();
        assertTrue(properties.getWhitelistExceptionsSet().isEmpty());

        properties.setWhitelistExceptions("   ");
        properties.parseWhitelistExceptions();
        assertTrue(properties.getWhitelistExceptionsSet().isEmpty());
    }

    @Test
    void parseWhitelistExceptions_whenValidNumbers_thenParsed() {
        properties.setWhitelistExceptions("350001752, 123456789 , 999");
        properties.parseWhitelistExceptions();
        assertEquals(Set.of(350001752L, 123456789L, 999L), properties.getWhitelistExceptionsSet());
    }

    @Test
    void parseWhitelistExceptions_whenInvalidNumber_thenSetIsEmpty() {
        properties.setWhitelistExceptions("123,abc,456");
        properties.parseWhitelistExceptions();
        assertTrue(properties.getWhitelistExceptionsSet().isEmpty());
    }

    @Test
    void parseWhitelistChannelIdExceptions_whenEmpty_thenSetIsEmpty() {
        properties.setWhitelistChannelIdExceptions(null);
        properties.parseWhitelistExceptions();
        assertTrue(properties.getWhitelistChannelIdExceptionsSet().isEmpty());

        properties.setWhitelistChannelIdExceptions("");
        properties.parseWhitelistExceptions();
        assertTrue(properties.getWhitelistChannelIdExceptionsSet().isEmpty());
    }

    @Test
    void parseWhitelistChannelIdExceptions_whenValid_thenParsed() {
        properties.setWhitelistChannelIdExceptions("-1000000000000, @mygroup , channel");
        properties.parseWhitelistExceptions();
        assertEquals(Set.of("-1000000000000", "@mygroup", "channel"), properties.getWhitelistChannelIdExceptionsSet());
    }

    @Test
    void commands_gettersSetters_work() {
        TelegramProperties.Commands commands = new TelegramProperties.Commands();
        commands.setStartEnabled(true);
        commands.setRoleEnabled(true);
        commands.setMessageEnabled(false);
        commands.setBugreportEnabled(true);
        commands.setNewthreadEnabled(true);
        commands.setHistoryEnabled(true);
        commands.setThreadsEnabled(true);
        commands.setLanguageEnabled(false);

        assertTrue(commands.isStartEnabled());
        assertTrue(commands.isRoleEnabled());
        assertFalse(commands.isMessageEnabled());
        assertTrue(commands.isBugreportEnabled());
        assertTrue(commands.isNewthreadEnabled());
        assertTrue(commands.isHistoryEnabled());
        assertTrue(commands.isThreadsEnabled());
        assertFalse(commands.isLanguageEnabled());
    }

    @Test
    void optionalNumericFields_canBeSet() {
        properties.setLongPollingSocketTimeoutSeconds(50);
        properties.setGetUpdatesTimeoutSeconds(25);
        assertEquals(50, properties.getLongPollingSocketTimeoutSeconds());
        assertEquals(25, properties.getGetUpdatesTimeoutSeconds());
    }
}
