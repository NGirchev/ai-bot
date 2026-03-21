package io.github.ngirchev.opendaimon.common.service;

import java.util.List;

/**
 * Utility for estimating token count in text.
 * Uses heuristic: 1 token ≈ 4 characters.
 *
 * TODO: Can integrate tiktoken or other library for exact count in future
 */
public class TokenCounter {

    private static final int CHARS_PER_TOKEN = 4;

    /**
     * Estimates token count in text.
     *
     * @param text text to estimate
     * @return approximate token count
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil((double) text.length() / CHARS_PER_TOKEN);
    }

    /**
     * Estimates total token count in a list of texts.
     *
     * @param texts list of texts to estimate
     * @return total approximate token count
     */
    public int estimateTokens(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return 0;
        }
        return texts.stream()
            .mapToInt(this::estimateTokens)
            .sum();
    }
}
