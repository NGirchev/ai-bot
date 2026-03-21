package io.github.ngirchev.opendaimon.common.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TokenCounterTest {

    private TokenCounter tokenCounter;

    @BeforeEach
    void setUp() {
        tokenCounter = new TokenCounter();
    }

    @Test
    void whenTextIsNull_thenReturnZero() {
        String nullText = null;
        int result = tokenCounter.estimateTokens(nullText);
        assertEquals(0, result);
    }

    @Test
    void whenTextIsEmpty_thenReturnZero() {
        int result = tokenCounter.estimateTokens("");
        assertEquals(0, result);
    }

    @Test
    void whenTextHasExactMultipleOfCharsPerToken_thenReturnCorrectCount() {
        // 8 chars, 4 chars per token = 2 tokens
        String text = "12345678";
        int result = tokenCounter.estimateTokens(text);
        assertEquals(2, result);
    }

    @Test
    void whenTextHasRemainder_thenRoundUp() {
        // 9 chars, 4 chars per token = 2.25 tokens, round up to 3
        String text = "123456789";
        int result = tokenCounter.estimateTokens(text);
        assertEquals(3, result);
    }

    @Test
    void whenTextListIsNull_thenReturnZero() {
        int result = tokenCounter.estimateTokens((List<String>) null);
        assertEquals(0, result);
    }

    @Test
    void whenTextListIsEmpty_thenReturnZero() {
        int result = tokenCounter.estimateTokens(Collections.emptyList());
        assertEquals(0, result);
    }

    @Test
    void whenTextListHasMultipleTexts_thenReturnSum() {
        // each text 4 chars = 1 token, total 3 tokens
        List<String> texts = Arrays.asList("1234", "5678", "9012");
        int result = tokenCounter.estimateTokens(texts);
        assertEquals(3, result);
    }
}
