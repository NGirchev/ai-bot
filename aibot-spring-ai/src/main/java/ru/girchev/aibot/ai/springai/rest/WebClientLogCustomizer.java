package ru.girchev.aibot.ai.springai.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpRequestDecorator;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.reactivestreams.Publisher;

@Slf4j
public class WebClientLogCustomizer implements WebClientCustomizer {

    private static final int MAX_ERROR_BODY_CHARS = 4_000;
    private static final int MAX_CALLSITE_STACKTRACE_FRAMES = 60;
    private static final AtomicBoolean FIRST_AI_HTTP_STACKTRACE_LOGGED = new AtomicBoolean(false);

    private final boolean callsiteStacktraceEnabled;
    private final ObjectMapper objectMapper;

    public WebClientLogCustomizer() {
        this(new ObjectMapper(), false);
    }

    public WebClientLogCustomizer(ObjectMapper objectMapper) {
        this(objectMapper, false);
    }

    public WebClientLogCustomizer(ObjectMapper objectMapper, boolean callsiteStacktraceEnabled) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.callsiteStacktraceEnabled = callsiteStacktraceEnabled;
    }

    @Override
    public void customize(org.springframework.web.reactive.function.client.WebClient.Builder builder) {
        builder.filter(logRequestsToKnownAiBackends());
    }

    private ExchangeFilterFunction logRequestsToKnownAiBackends() {
        return (request, next) -> {
            if (!shouldLog(request)) {
                return next.exchange(request);
            }

            long startNs = System.nanoTime();
            if (callsiteStacktraceEnabled
                    && log.isDebugEnabled()
                    && FIRST_AI_HTTP_STACKTRACE_LOGGED.compareAndSet(false, true)) {
                log.debug("AI HTTP call-site stacktrace (first request only)\n{}",
                        captureCallsiteStacktrace());
            }
            log.info("HTTP -> {} {} (authHeaderPresent={}, contentType={})",
                    request.method(),
                    request.url(),
                    request.headers().containsKey("Authorization"),
                    request.headers().getFirst("Content-Type"));

            ClientRequest requestWithBodyLogging = decorateRequestBodyLoggingIfDebug(request);
            return next.exchange(requestWithBodyLogging)
                    .flatMap(response -> logAndBufferErrorsIfNeeded(requestWithBodyLogging, response, startNs));
        };
    }

    private Mono<ClientResponse> logAndBufferErrorsIfNeeded(ClientRequest request, ClientResponse response, long startNs) {
        long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
        int status = response.statusCode().value();

        if (!response.statusCode().isError()) {
            log.info("HTTP <- {} {} status={} latencyMs={}", request.method(), request.url(), status, latencyMs);
            return Mono.just(response);
        }

        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> {
                    log.error("HTTP <- {} {} status={} latencyMs={} body={}",
                            request.method(),
                            request.url(),
                            status,
                            latencyMs,
                            truncate(body));
                    return ClientResponse.from(response).body(body).build();
                });
    }

    private boolean shouldLog(ClientRequest request) {
        if (request == null || request.url() == null) {
            return false;
        }
        String host = request.url().getHost();
        if (host == null) {
            return false;
        }
        return host.contains("openrouter.ai") || host.equals("localhost") || host.equals("127.0.0.1");
    }

    private ClientRequest decorateRequestBodyLoggingIfDebug(ClientRequest request) {
        if (!log.isDebugEnabled()) {
            return request;
        }
        if (request == null) {
            return null;
        }

        var originalInserter = request.body();

	        var loggingInserter = new org.springframework.web.reactive.function.BodyInserter<Object, ClientHttpRequest>() {
	            @Override
	            public Mono<Void> insert(ClientHttpRequest outputMessage, Context context) {
	                ByteArrayOutputStream captured = new ByteArrayOutputStream();

	                ClientHttpRequestDecorator decorator = new ClientHttpRequestDecorator(outputMessage) {
	                    @Override
	                    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
	                        Flux<? extends DataBuffer> flux = Flux.from(body)
	                                .doOnNext(dataBuffer -> captureChunk(captured, dataBuffer));
	                        return super.writeWith(flux);
	                    }

	                    @Override
	                    public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
	                        Flux<? extends Publisher<? extends DataBuffer>> outer = Flux.from(body)
	                                .map(inner -> Flux.from(inner)
	                                        .doOnNext(dataBuffer -> captureChunk(captured, dataBuffer)));
	                        return super.writeAndFlushWith(outer);
	                    }
	                };

                @SuppressWarnings("unchecked")
                org.springframework.web.reactive.function.BodyInserter<Object, ClientHttpRequest> typedOriginal =
                        (org.springframework.web.reactive.function.BodyInserter<Object, ClientHttpRequest>) originalInserter;

	                return typedOriginal.insert(decorator, context)
	                        .doFinally(signal -> logRequestBodyIfAny(request, captured));
	            }
	        };

        return ClientRequest.from(request).body(loggingInserter).build();
    }

    private String captureCallsiteStacktrace() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames
                        .dropWhile(frame -> shouldSkipFromCallsiteStacktrace(frame.getClassName()))
                        .limit(MAX_CALLSITE_STACKTRACE_FRAMES)
                        .map(frame -> "at %s.%s(%s:%d)".formatted(
                                frame.getClassName(),
                                frame.getMethodName(),
                                frame.getFileName(),
                                frame.getLineNumber()
                        ))
                        .collect(Collectors.joining("\n")));
    }

    private boolean shouldSkipFromCallsiteStacktrace(String className) {
        if (className == null) {
            return true;
        }
        if (className.equals(WebClientLogCustomizer.class.getName())) {
            return true;
        }
        return className.startsWith("java.")
                || className.startsWith("jdk.")
                || className.startsWith("reactor.")
                || className.startsWith("org.springframework.web.reactive.function.client.")
                || className.startsWith("org.springframework.http.client.reactive.")
                || className.startsWith("org.springframework.core.io.buffer.")
                || className.startsWith("io.netty.");
    }

	    private void captureChunk(ByteArrayOutputStream captured, DataBuffer dataBuffer) {
	        if (dataBuffer == null) {
	            return;
	        }
	        ByteBuffer byteBuffer = dataBuffer.asByteBuffer().asReadOnlyBuffer();
	        int toCopy = byteBuffer.remaining();
	        if (toCopy <= 0) {
	            return;
	        }
	        byte[] chunk = new byte[toCopy];
	        byteBuffer.get(chunk);
	        captured.writeBytes(chunk);
	    }

	    private void logRequestBodyIfAny(ClientRequest request, ByteArrayOutputStream captured) {
	        if (!log.isDebugEnabled()) {
	            return;
	        }
	        if (captured == null || captured.size() == 0) {
	            return;
	        }
	        String body = new String(captured.toByteArray(), StandardCharsets.UTF_8);
	        String formattedBody = formatBodyForLogs(body, request.headers().getFirst("Content-Type"));
	        log.debug("HTTP -> {} {} REQUEST BODY:\n{}",
	                request.method(),
	                request.url(),
	                formattedBody);
	    }

	    private String formatBodyForLogs(String body, String contentType) {
	        if (body == null || body.isBlank()) {
	            return body;
	        }

	        String trimmed = body.trim();
	        boolean looksLikeJson = trimmed.startsWith("{") || trimmed.startsWith("[");
	        boolean isJsonContentType = contentType != null && contentType.toLowerCase().contains("json");
	        if (!looksLikeJson && !isJsonContentType) {
	            return body;
	        }

	        try {
	            JsonNode root = objectMapper.readTree(body);
	            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
	        } catch (JsonProcessingException e) {
	            return body;
	        }
	    }

    private String truncate(String body) {
        if (body == null) {
            return null;
        }
        if (body.length() <= MAX_ERROR_BODY_CHARS) {
            return body;
        }
        return body.substring(0, MAX_ERROR_BODY_CHARS) + "...(truncated)";
    }
}
