package io.github.ngirchev.opendaimon.ai.springai.rest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;

/**
 * WebClient filter that normalizes unknown {@code finish_reason} values in the OpenRouter SSE stream.
 * <p>
 * Spring AI's {@code ChatCompletionFinishReason} enum does not include {@code "error"},
 * so Jackson fails with {@code InvalidFormatException} when a model returns:
 * <pre>
 *   "finish_reason":"error", "native_finish_reason":"MALFORMED_FUNCTION_CALL"
 * </pre>
 * This filter replaces {@code "finish_reason":"error"} with {@code "finish_reason":"stop"}
 * before Spring AI parses the SSE chunk, allowing the stream to complete normally.
 * The original {@code native_finish_reason} is preserved for debugging.
 * <p>
 * Root cause: when tool calls are sent to Gemini-family models via OpenRouter,
 * the model may fail to generate a valid function call JSON, resulting in
 * {@code MALFORMED_FUNCTION_CALL} from the provider. After this normalisation the
 * stream will produce an empty response, triggering {@link io.github.ngirchev.opendaimon.ai.springai.retry.OpenRouterEmptyStreamException}
 * and model rotation to a fallback candidate.
 */
@Slf4j
public class OpenRouterSseNormalizingCustomizer implements WebClientCustomizer {

    static final String FINISH_REASON_ERROR = "\"finish_reason\":\"error\"";
    static final String FINISH_REASON_STOP = "\"finish_reason\":\"stop\"";

    @Override
    public void customize(WebClient.Builder builder) {
        builder.filter(normalizeFinishReason());
    }

    private ExchangeFilterFunction normalizeFinishReason() {
        return (request, next) -> next.exchange(request)
                .map(response -> response.mutate()
                        .body(dataBuffers -> dataBuffers.map(this::normalizeSseChunk))
                        .build());
    }

    DataBuffer normalizeSseChunk(DataBuffer dataBuffer) {
        if (dataBuffer.readableByteCount() == 0) {
            return dataBuffer;
        }
        byte[] bytes = new byte[dataBuffer.readableByteCount()];
        dataBuffer.read(bytes);
        DataBufferUtils.release(dataBuffer);

        String chunk = new String(bytes, StandardCharsets.UTF_8);
        if (!chunk.contains(FINISH_REASON_ERROR)) {
            return DefaultDataBufferFactory.sharedInstance.wrap(bytes);
        }

        String normalized = chunk.replace(FINISH_REASON_ERROR, FINISH_REASON_STOP);
        log.warn("OpenRouter SSE: replaced finish_reason=error with stop. chunk={}",
                chunk.substring(0, Math.min(chunk.length(), 500)));
        return DefaultDataBufferFactory.sharedInstance.wrap(normalized.getBytes(StandardCharsets.UTF_8));
    }
}
