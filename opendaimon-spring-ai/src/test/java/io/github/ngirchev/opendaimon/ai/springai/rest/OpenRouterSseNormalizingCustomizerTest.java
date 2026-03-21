package io.github.ngirchev.opendaimon.ai.springai.rest;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class OpenRouterSseNormalizingCustomizerTest {

    private MockWebServer server;
    private WebClient webClient;
    private final OpenRouterSseNormalizingCustomizer customizer = new OpenRouterSseNormalizingCustomizer();

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        WebClient.Builder builder = WebClient.builder().baseUrl(server.url("/").toString());
        customizer.customize(builder);
        webClient = builder.build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    // --- normalizeSseChunk unit tests ---

    @Test
    void normalizeSseChunk_replacesFinishReasonError() {
        String input = "data: {\"choices\":[{\"finish_reason\":\"error\",\"native_finish_reason\":\"MALFORMED_FUNCTION_CALL\"}]}\n";
        DataBuffer buf = DefaultDataBufferFactory.sharedInstance.wrap(input.getBytes(StandardCharsets.UTF_8));
        DataBuffer result = customizer.normalizeSseChunk(buf);
        String output = result.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("\"finish_reason\":\"stop\""), "Should replace error with stop");
        assertTrue(output.contains("MALFORMED_FUNCTION_CALL"), "Should preserve native_finish_reason");
    }

    @Test
    void normalizeSseChunk_noChangeWhenNoError() {
        String input = "data: {\"choices\":[{\"finish_reason\":\"stop\"}]}\n";
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        DataBuffer buf = DefaultDataBufferFactory.sharedInstance.wrap(bytes);
        DataBuffer result = customizer.normalizeSseChunk(buf);
        String output = result.toString(StandardCharsets.UTF_8);
        assertEquals(input, output);
    }

    @Test
    void normalizeSseChunk_emptyBufferReturnsAsIs() {
        DataBuffer buf = DefaultDataBufferFactory.sharedInstance.allocateBuffer(0);
        DataBuffer result = customizer.normalizeSseChunk(buf);
        assertEquals(0, result.readableByteCount());
    }

    @Test
    void normalizeSseChunk_multipleOccurrencesReplaced() {
        String input = "data: {\"finish_reason\":\"error\"}\ndata: {\"finish_reason\":\"error\"}\n";
        DataBuffer buf = DefaultDataBufferFactory.sharedInstance.wrap(input.getBytes(StandardCharsets.UTF_8));
        DataBuffer result = customizer.normalizeSseChunk(buf);
        String output = result.toString(StandardCharsets.UTF_8);
        assertFalse(output.contains("\"finish_reason\":\"error\""));
        assertEquals(2, countOccurrences(output, "\"finish_reason\":\"stop\""));
    }

    // --- integration test via MockWebServer ---

    @Test
    void webClientFilter_normalizesFinishReasonErrorInResponse() {
        String sseBody = "data: {\"choices\":[{\"finish_reason\":\"error\",\"delta\":{\"content\":\"hi\"}}]}\n\ndata: [DONE]\n\n";
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody(sseBody));

        String result = webClient.get().uri("/stream")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        assertNotNull(result);
        assertTrue(result.contains("\"finish_reason\":\"stop\""), "finish_reason=error should be replaced with stop");
        assertFalse(result.contains("\"finish_reason\":\"error\""), "finish_reason=error should not be present");
    }

    private static int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
