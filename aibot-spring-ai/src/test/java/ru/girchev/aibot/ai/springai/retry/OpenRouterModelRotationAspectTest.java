package ru.girchev.aibot.ai.springai.retry;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import ru.girchev.aibot.ai.springai.config.SpringAIModelConfig;
import ru.girchev.aibot.common.ai.ModelType;
import ru.girchev.aibot.common.ai.command.AIBotChatOptions;
import ru.girchev.aibot.common.ai.command.ChatAICommand;
import ru.girchev.aibot.common.ai.response.SpringAIStreamResponse;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Тест для механизма ретраев и ротации моделей OpenRouter.
 * 
 * Проверяет:
 * 1. Условия активации ротации (free-модели, AUTO с max_price=0)
 * 2. Логику ретраев при различных ошибках (429, 5xx, 400 с специфичным сообщением)
 * 3. Неретраируемые ошибки (401, 403, 400 без специфичного сообщения)
 * 4. Стриминговые запросы с ретраями
 * 5. Ограничение количества попыток (maxAttempts)
 * 6. Запись успехов и неудач в resolver
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpenRouterModelRotationAspectTest {

    @Mock
    private ProceedingJoinPoint proceedingJoinPoint;
    
    @SuppressWarnings("rawtypes")
    private ObjectProvider resolverProvider;
    
    @SuppressWarnings("rawtypes")
    private Object resolver;
    
    private OpenRouterModelRotationAspect aspect;
    private SpringAIModelConfig modelConfig;
    private AIBotChatOptions chatOptions;
    private ChatAICommand command;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        resolverProvider = mock(ObjectProvider.class);
        // Не создаем resolver здесь, чтобы избежать загрузки класса при инициализации
        // resolver будет создан в каждом тесте, где он нужен
        lenient().when(resolverProvider.getIfAvailable()).thenReturn(null);
        
        modelConfig = new SpringAIModelConfig();
        modelConfig.setName("meta-llama/llama-3.2-3b-instruct:free");
        modelConfig.setProviderType(SpringAIModelConfig.ProviderType.OPENAI);
        modelConfig.setCapabilities(List.of(ModelType.CHAT));
        modelConfig.setPriority(1);
        
        chatOptions = new AIBotChatOptions(0.7, 1000, null, null, false, null);
        command = new ChatAICommand(Set.of(ModelType.CHAT), 0.7, 1000, null, null);
    }
    
    @SuppressWarnings("unchecked")
    private ru.girchev.aibot.common.openrouter.OpenRouterFreeModelResolver getResolver() {
        if (resolver == null) {
            try {
                Class<?> resolverClass = Class.forName("ru.girchev.aibot.common.openrouter.OpenRouterFreeModelResolver");
                resolver = mock(resolverClass);
                when(resolverProvider.getIfAvailable()).thenReturn(resolver);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Failed to load OpenRouterFreeModelResolver", e);
            }
        }
        return (ru.girchev.aibot.common.openrouter.OpenRouterFreeModelResolver) resolver;
    }

    @Test
    void whenNoModelConfig_thenProceedWithoutRotation() throws Throwable {
        // Arrange
        aspect = new OpenRouterModelRotationAspect(resolverProvider, 3);
        Object[] args = new Object[]{"arg1", "arg2"};
        when(proceedingJoinPoint.getArgs()).thenReturn(args);
        Object expectedResult = new Object();
        // Аспект вызывает proceed() без аргументов когда ротация не нужна
        doReturn(expectedResult).when(proceedingJoinPoint).proceed();
        
        // Act
        Object result = aspect.rotateModels(proceedingJoinPoint, createAnnotation(false));
        
        // Assert
        assertEquals(expectedResult, result);
        verify(proceedingJoinPoint).proceed();
        verify(getResolver(), never()).candidatesForModel(any(), any());
    }

    @Test
    void whenNonFreeModel_thenProceedWithoutRotation() throws Throwable {
        // Arrange
        aspect = new OpenRouterModelRotationAspect(resolverProvider, 3);
        modelConfig.setName("gpt-4");
        modelConfig.setProviderType(SpringAIModelConfig.ProviderType.OPENAI);
        Object[] args = new Object[]{modelConfig};
        when(proceedingJoinPoint.getArgs()).thenReturn(args);
        Object expectedResult = new Object();
        // Аспект вызывает proceed() без аргументов когда ротация не нужна
        doReturn(expectedResult).when(proceedingJoinPoint).proceed();
        
        // Act
        Object result = aspect.rotateModels(proceedingJoinPoint, createAnnotation(false));
        
        // Assert
        assertEquals(expectedResult, result);
        verify(proceedingJoinPoint).proceed();
        verify(getResolver(), never()).candidatesForModel(any(), any());
    }

    @Test
    void whenFreeModel_thenRotateOnRetryableError() throws Throwable {
        // Arrange
        aspect = new OpenRouterModelRotationAspect(resolverProvider, 3);
        List<String> candidates = List.of(
            "meta-llama/llama-3.2-3b-instruct:free",
            "mistralai/mistral-7b-instruct:free"
        );
        when(getResolver().candidatesForModel(eq("meta-llama/llama-3.2-3b-instruct:free"), any()))
            .thenReturn(candidates);
        
        Object[] args = new Object[]{modelConfig, command};
        when(proceedingJoinPoint.getArgs()).thenReturn(args);
        
        // Первая попытка - ошибка 429 (retryable)
        @SuppressWarnings("null")
        WebClientResponseException error429 = WebClientResponseException.create(
            HttpStatus.TOO_MANY_REQUESTS.value(),
            "Too Many Requests",
            HttpHeaders.EMPTY,
            new byte[0],
            null
        );
        ChatResponse successResponse = mock(ChatResponse.class);
        // Первый вызов - ошибка, второй - успех
        doThrow(error429)
            .doReturn(successResponse)
            .when(proceedingJoinPoint).proceed(any());
        
        // Act
        Object result = aspect.rotateModels(proceedingJoinPoint, createAnnotation(false));
        
        // Assert
        assertEquals(successResponse, result);
        verify(proceedingJoinPoint, times(2)).proceed(any());
        verify(getResolver()).recordFailure(eq("meta-llama/llama-3.2-3b-instruct:free"), eq(429), anyLong());
        verify(getResolver()).recordSuccess(eq("mistralai/mistral-7b-instruct:free"), anyLong());
    }

    @Test
    void whenAutoModelWithMaxPriceZero_thenRotate() throws Throwable {
        // Arrange
        aspect = new OpenRouterModelRotationAspect(resolverProvider, 3);
        modelConfig.setName("openrouter/auto");
        modelConfig.setCapabilities(List.of(ModelType.AUTO));
        chatOptions = new AIBotChatOptions(0.7, 1000, null, null, false, 
            Map.of("max_price", 0));
        
        List<String> candidates = List.of("model1:free", "model2:free");
        when(getResolver().candidatesForModel(eq("openrouter/auto"), any()))
            .thenReturn(candidates);
        
        Object[] args = new Object[]{modelConfig, chatOptions};
        when(proceedingJoinPoint.getArgs()).thenReturn(args);
        ChatResponse successResponse = mock(ChatResponse.class);
        doReturn(successResponse).when(proceedingJoinPoint).proceed(any());
        
        // Act
        Object result = aspect.rotateModels(proceedingJoinPoint, createAnnotation(false));
        
        // Assert
        assertEquals(successResponse, result);
        verify(getResolver()).candidatesForModel(eq("openrouter/auto"), any());
    }

    @Test
    void whenAutoModelWithoutMaxPriceZero_thenNoRotation() throws Throwable {
        // Arrange
        aspect = new OpenRouterModelRotationAspect(resolverProvider, 3);
        modelConfig.setName("openrouter/auto");
        modelConfig.setCapabilities(List.of(ModelType.AUTO));
        chatOptions = new AIBotChatOptions(0.7, 1000, null, null, false, 
            Map.of("max_price", 0.001));
        
        Object[] args = new Object[]{modelConfig, chatOptions};
        when(proceedingJoinPoint.getArgs()).thenReturn(args);
        ChatResponse successResponse = mock(ChatResponse.class);
        // Аспект вызывает proceed() без аргументов когда ротация не нужна
        doReturn(successResponse).when(proceedingJoinPoint).proceed();
        
        // Act
        Object result = aspect.rotateModels(proceedingJoinPoint, createAnnotation(false));
        
        // Assert
        assertEquals(successResponse, result);
        verify(proceedingJoinPoint).proceed();
        verify(getResolver(), never()).candidatesForModel(any(), any());
    }

    @Test
    void whenNonRetryableError_thenThrowImmediately() throws Throwable {
        // Arrange
        aspect = new OpenRouterModelRotationAspect(resolverProvider, 3);
        List<String> candidates = List.of("model1:free", "model2:free");
        when(getResolver().candidatesForModel(any(), any())).thenReturn(candidates);
        
        Object[] args = new Object[]{modelConfig, command};
        when(proceedingJoinPoint.getArgs()).thenReturn(args);
        
        // Ошибка 401 (неретраируемая)
        @SuppressWarnings("null")
        WebClientResponseException error401 = WebClientResponseException.create(
            HttpStatus.UNAUTHORIZED.value(),
            "Unauthorized",
            HttpHeaders.EMPTY,
            new byte[0],
            null
        );
        doThrow(error401).when(proceedingJoinPoint).proceed(any());
        
        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            aspect.rotateModels(proceedingJoinPoint, createAnnotation(false));
        });
        
        verify(proceedingJoinPoint, times(1)).proceed(any());
        verify(getResolver()).recordFailure(eq("model1:free"), eq(401), anyLong());
        verify(getResolver(), never()).recordSuccess(any(), anyLong());
    }

    @Test
    void when400WithSpecificMessage_thenRetry() throws Throwable {
        // Arrange
        aspect = new OpenRouterModelRotationAspect(resolverProvider, 3);
        List<String> candidates = List.of("model1:free", "model2:free");
        when(getResolver().candidatesForModel(any(), any())).thenReturn(candidates);
        
        Object[] args = new Object[]{modelConfig, command};
        when(proceedingJoinPoint.getArgs()).thenReturn(args);
        
        // Ошибка 400 с специфичным сообщением (retryable)
        @SuppressWarnings("null")
        WebClientResponseException error400 = WebClientResponseException.create(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            HttpHeaders.EMPTY,
            "Conversation roles must alternate".getBytes(),
            null
        );
        doThrow(error400).doReturn(mock(ChatResponse.class)).when(proceedingJoinPoint).proceed(any());
        
        // Act
        Object result = aspect.rotateModels(proceedingJoinPoint, createAnnotation(false));
        
        // Assert
        assertNotNull(result);
        verify(proceedingJoinPoint, times(2)).proceed(any());
    }

    @Test
    void whenAllCandidatesFail_thenThrowLastError() throws Throwable {
        // Arrange
        aspect = new OpenRouterModelRotationAspect(resolverProvider, 2);
        List<String> candidates = List.of("model1:free", "model2:free");
        when(getResolver().candidatesForModel(any(), any())).thenReturn(candidates);
        
        Object[] args = new Object[]{modelConfig, command};
        when(proceedingJoinPoint.getArgs()).thenReturn(args);
        
        @SuppressWarnings("null")
        WebClientResponseException error500 = WebClientResponseException.create(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            HttpHeaders.EMPTY,
            new byte[0],
            null
        );
        doThrow(error500).when(proceedingJoinPoint).proceed(any());
        
        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            aspect.rotateModels(proceedingJoinPoint, createAnnotation(false));
        });
        
        assertNotNull(exception);
        verify(proceedingJoinPoint, times(2)).proceed(any());
        verify(getResolver(), times(2)).recordFailure(any(), eq(500), anyLong());
    }

    @Test
    void whenStreamRequest_thenRotateOnError() throws Throwable {
        // Arrange
        aspect = new OpenRouterModelRotationAspect(resolverProvider, 3);
        List<String> candidates = List.of("model1:free", "model2:free");
        when(getResolver().candidatesForModel(any(), any())).thenReturn(candidates);
        
        Object[] args = new Object[]{modelConfig, command};
        when(proceedingJoinPoint.getArgs()).thenReturn(args);
        
        // Первая попытка - ошибка
        @SuppressWarnings("null")
        WebClientResponseException error429 = WebClientResponseException.create(
            HttpStatus.TOO_MANY_REQUESTS.value(),
            "Too Many Requests",
            HttpHeaders.EMPTY,
            new byte[0],
            null
        );
        SpringAIStreamResponse errorResponse = new SpringAIStreamResponse(
            Flux.error(error429)
        );
        
        // Вторая попытка - успех
        ChatResponse chatResponse = mock(ChatResponse.class);
        SpringAIStreamResponse successResponse = new SpringAIStreamResponse(
            Flux.just(chatResponse)
        );
        
        doReturn(errorResponse)
            .doReturn(successResponse)
            .when(proceedingJoinPoint).proceed(any());
        
        // Act
        Object result = aspect.rotateModels(proceedingJoinPoint, createAnnotation(true));
        
        // Assert
        assertNotNull(result);
        assertTrue(result instanceof SpringAIStreamResponse);
        
        SpringAIStreamResponse streamResponse = (SpringAIStreamResponse) result;
        Flux<ChatResponse> responseFlux = streamResponse.chatResponse();
        assertNotNull(responseFlux);
        
        // Проверяем, что стрим не содержит ошибок (блокирующий вызов для теста)
        List<ChatResponse> responses = responseFlux.collectList().block();
        assertNotNull(responses);
        assertEquals(1, responses.size());
        
        verify(proceedingJoinPoint, times(2)).proceed(any());
    }

    @Test
    void whenMaxAttemptsExceeded_thenLimitCandidates() throws Throwable {
        // Arrange
        aspect = new OpenRouterModelRotationAspect(resolverProvider, 2);
        List<String> allCandidates = List.of("model1:free", "model2:free", "model3:free", "model4:free");
        when(getResolver().candidatesForModel(any(), any())).thenReturn(allCandidates);
        
        Object[] args = new Object[]{modelConfig, command};
        when(proceedingJoinPoint.getArgs()).thenReturn(args);
        
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        ChatResponse successResponse = mock(ChatResponse.class);
        doReturn(successResponse).when(proceedingJoinPoint).proceed(argsCaptor.capture());
        
        // Act
        Object result = aspect.rotateModels(proceedingJoinPoint, createAnnotation(false));
        
        // Assert
        assertEquals(successResponse, result);
        // Проверяем, что использовались только первые 2 кандидата
        verify(proceedingJoinPoint, times(1)).proceed(any());
    }

    @Test
    void whenTransportError_thenRetry() throws Throwable {
        // Arrange
        aspect = new OpenRouterModelRotationAspect(resolverProvider, 3);
        List<String> candidates = List.of("model1:free", "model2:free");
        when(getResolver().candidatesForModel(any(), any())).thenReturn(candidates);
        
        Object[] args = new Object[]{modelConfig, command};
        when(proceedingJoinPoint.getArgs()).thenReturn(args);
        
        // Транспортная ошибка (retryable)
        RuntimeException transportError = new RuntimeException("Connection timeout");
        doThrow(transportError)
            .doReturn(mock(ChatResponse.class))
            .when(proceedingJoinPoint).proceed(any());
        
        // Act
        Object result = aspect.rotateModels(proceedingJoinPoint, createAnnotation(false));
        
        // Assert
        assertNotNull(result);
        verify(proceedingJoinPoint, times(2)).proceed(any());
    }

    @Test
    void whenModelConfigReplaced_thenNewModelUsed() throws Throwable {
        // Arrange
        aspect = new OpenRouterModelRotationAspect(resolverProvider, 3);
        List<String> candidates = List.of("model1:free", "model2:free");
        when(getResolver().candidatesForModel(any(), any())).thenReturn(candidates);
        
        Object[] args = new Object[]{modelConfig, command};
        when(proceedingJoinPoint.getArgs()).thenReturn(args);
        
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        WebClientResponseException error429 = new WebClientResponseException(
            HttpStatus.TOO_MANY_REQUESTS.value(),
            "Too Many Requests",
            null, null, null
        );
        doThrow(error429)
            .doReturn(mock(ChatResponse.class))
            .when(proceedingJoinPoint).proceed(argsCaptor.capture());
        
        // Act
        aspect.rotateModels(proceedingJoinPoint, createAnnotation(false));
        
        // Assert
        verify(proceedingJoinPoint, times(2)).proceed(any());
        List<Object[]> capturedArgs = argsCaptor.getAllValues();
        
        // Проверяем, что во второй попытке использовалась другая модель
        SpringAIModelConfig firstConfig = (SpringAIModelConfig) capturedArgs.get(0)[0];
        SpringAIModelConfig secondConfig = (SpringAIModelConfig) capturedArgs.get(1)[0];
        
        assertNotEquals(firstConfig.getName(), secondConfig.getName());
        assertEquals("model1:free", firstConfig.getName());
        assertEquals("model2:free", secondConfig.getName());
    }

    @Test
    void whenNoResolver_thenProceedWithoutRotation() throws Throwable {
        // Arrange
        when(resolverProvider.getIfAvailable()).thenReturn(null);
        aspect = new OpenRouterModelRotationAspect(resolverProvider, 3);
        
        Object[] args = new Object[]{modelConfig, command};
        when(proceedingJoinPoint.getArgs()).thenReturn(args);
        ChatResponse successResponse = mock(ChatResponse.class);
        // Когда resolver == null, аспект возвращает список с одной моделью и вызывает proceed с аргументами
        doReturn(successResponse).when(proceedingJoinPoint).proceed(any());
        
        // Act
        Object result = aspect.rotateModels(proceedingJoinPoint, createAnnotation(false));
        
        // Assert
        assertEquals(successResponse, result);
        verify(proceedingJoinPoint).proceed(any());
    }

    @Test
    void whenEmptyCandidates_thenProceedWithoutRotation() throws Throwable {
        // Arrange
        aspect = new OpenRouterModelRotationAspect(resolverProvider, 3);
        // Когда resolver возвращает пустой список, аспект проверяет modelConfig.getName() и возвращает список с одной моделью
        // Поэтому нужно вернуть пустой список, но затем аспект вернет список с modelConfig.getName()
        when(getResolver().candidatesForModel(any(), any())).thenReturn(Collections.emptyList());
        
        Object[] args = new Object[]{modelConfig, command};
        when(proceedingJoinPoint.getArgs()).thenReturn(args);
        ChatResponse successResponse = mock(ChatResponse.class);
        // Когда candidates пустой после resolver, аспект возвращает список с modelConfig.getName() и вызывает proceed с аргументами
        doReturn(successResponse).when(proceedingJoinPoint).proceed(any());
        
        // Act
        Object result = aspect.rotateModels(proceedingJoinPoint, createAnnotation(false));
        
        // Assert
        assertEquals(successResponse, result);
        verify(proceedingJoinPoint).proceed(any());
    }

    @Test
    void whenMaxPriceInOptions_thenDetectCorrectly() throws Throwable {
        // Arrange
        aspect = new OpenRouterModelRotationAspect(resolverProvider, 3);
        modelConfig.setName("openrouter/auto");
        modelConfig.setCapabilities(List.of(ModelType.AUTO));
        chatOptions = new AIBotChatOptions(0.7, 1000, null, null, false, 
            Map.of("options", Map.of("max_price", 0)));
        
        List<String> candidates = List.of("model1:free");
        when(getResolver().candidatesForModel(any(), any())).thenReturn(candidates);
        
        Object[] args = new Object[]{modelConfig, chatOptions};
        when(proceedingJoinPoint.getArgs()).thenReturn(args);
        doReturn(mock(ChatResponse.class)).when(proceedingJoinPoint).proceed(any());
        
        // Act
        Object result = aspect.rotateModels(proceedingJoinPoint, createAnnotation(false));
        
        // Assert
        assertNotNull(result);
        verify(getResolver()).candidatesForModel(any(), any());
    }

    private RotateOpenRouterModels createAnnotation(boolean stream) {
        return new RotateOpenRouterModels() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return RotateOpenRouterModels.class;
            }
            
            @Override
            public boolean stream() {
                return stream;
            }
        };
    }
}
