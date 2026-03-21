package io.github.ngirchev.opendaimon.ai.springai.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.lang.Nullable;

/**
 * Fallback {@link ToolCallbackResolver} that handles tool calls for unknown tools.
 * <p>
 * Some models (e.g. Gemini via OpenRouter) invoke built-in provider-side tools
 * (such as {@code run} for Code Execution) that are not registered in Spring AI.
 * Without this resolver, Spring AI throws {@code IllegalStateException: No ToolCallback found}
 * and the entire stream fails.
 * <p>
 * This resolver catches any unknown tool name and returns a {@link ToolCallback} that
 * responds with an "unavailable" message, allowing the model to continue and produce
 * a text answer without relying on the tool.
 */
@Slf4j
public class UnknownToolFallbackResolver implements ToolCallbackResolver {

    @Override
    public ToolCallback resolve(String toolName) {
        log.warn("Unknown tool '{}' called by model — returning unavailable response (possible built-in provider tool, e.g. Gemini Code Execution)", toolName);
        return new UnavailableToolCallback(toolName);
    }

    private static final class UnavailableToolCallback implements ToolCallback {

        private final ToolDefinition definition;

        UnavailableToolCallback(String toolName) {
            this.definition = ToolDefinition.builder()
                    .name(toolName)
                    .description("Unavailable fallback tool")
                    .inputSchema("{\"type\":\"object\"}")
                    .build();
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String call(String toolInput) {
            return "{\"error\": \"Tool '" + definition.name() + "' is not available in this context. Please answer without using this tool.\"}";
        }

        @Override
        public String call(String toolInput, @Nullable ToolContext toolContext) {
            return call(toolInput);
        }
    }
}
