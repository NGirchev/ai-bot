package io.github.ngirchev.opendaimon.ai.springai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import io.github.ngirchev.opendaimon.ai.springai.service.DocumentProcessingService;
import io.github.ngirchev.opendaimon.ai.springai.rag.FileRAGService;
import io.github.ngirchev.opendaimon.ai.springai.service.SpringAIModelType;
import io.github.ngirchev.opendaimon.common.ai.ModelCapabilities;

import java.util.Comparator;

/**
 * Auto-configuration for RAG (Retrieval-Augmented Generation).
 *
 * <p>Enabled when {@code open-daimon.ai.spring-ai.rag.enabled=true}
 *
 * <p>Uses SimpleVectorStore (in-memory):
 * <ul>
 *   <li><b>Pros:</b> No external deps (PostgreSQL pgvector, Elasticsearch)</li>
 *   <li><b>Cons:</b> Data not persistent — lost on restart</li>
 *   <li><b>Recommendation:</b> For production use PGVector or Elasticsearch</li>
 * </ul>
 *
 * <p>Requires EmbeddingModel (e.g. Ollama or OpenAI).
 *
 * <p><b>Embedding model selection:</b> filters models with EMBEDDING capability
 * to only those whose API provider is actually available, then prefers the same
 * provider as the primary (AUTO) model. This ensures that if the user runs on
 * OpenRouter, embedding also goes through OpenRouter (not Ollama).
 *
 * @see DocumentProcessingService for PDF processing
 * @see FileRAGService for relevant chunk search
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(name = "open-daimon.ai.spring-ai.rag.enabled", havingValue = "true")
@EnableConfigurationProperties(RAGProperties.class)
public class RAGAutoConfig {

    /**
     * Creates SimpleVectorStore for embeddings.
     *
     * <p>EmbeddingModel is created programmatically with the model name from
     * {@code open-daimon.ai.spring-ai.models.list} (capability EMBEDDING).
     *
     * <p><b>Selection algorithm:</b>
     * <ol>
     *   <li>Filter models with EMBEDDING capability whose API provider is available</li>
     *   <li>Prefer same provider as the primary (AUTO) model — so OpenRouter users get OpenAI embedding</li>
     *   <li>Tie-break by priority (lower number = higher priority)</li>
     * </ol>
     *
     * @param springAIModelType service to select model by capabilities
     * @param ollamaApiProvider Ollama API client (available when Ollama is configured)
     * @param openAiApiProvider OpenAI API client (available when OpenAI is configured)
     * @return VectorStore instance
     */
    @Bean
    @ConditionalOnMissingBean
    public VectorStore simpleVectorStore(
            SpringAIModelType springAIModelType,
            ObjectProvider<OllamaApi> ollamaApiProvider,
            ObjectProvider<OpenAiApi> openAiApiProvider) {

        boolean ollamaAvailable = ollamaApiProvider.getIfAvailable() != null;
        boolean openAiAvailable = openAiApiProvider.getIfAvailable() != null;

        // Primary provider = provider of the AUTO (main) model
        SpringAIModelConfig.ProviderType primaryProvider = springAIModelType
                .getByCapability(ModelCapabilities.AUTO)
                .map(SpringAIModelConfig::getProviderType)
                .orElse(null);

        log.debug("RAG: Selecting embedding model. Primary provider: {}, ollamaAvailable={}, openAiAvailable={}",
                primaryProvider, ollamaAvailable, openAiAvailable);

        // Filter: EMBEDDING capability + available API provider
        // Prefer: same provider as primary model → then lower priority number
        SpringAIModelConfig modelConfig = springAIModelType.getModels().stream()
                .filter(m -> m.getCapabilities() != null && m.getCapabilities().contains(ModelCapabilities.EMBEDDING))
                .filter(m -> switch (m.getProviderType()) {
                    case OLLAMA -> ollamaAvailable;
                    case OPENAI -> openAiAvailable;
                })
                .min(Comparator.<SpringAIModelConfig, Integer>comparing(m ->
                                m.getProviderType() == primaryProvider ? 0 : 1)
                        .thenComparing(SpringAIModelConfig::getPriority))
                .orElseThrow(() -> new IllegalStateException(
                        "No model with EMBEDDING capability and available API provider found in " +
                        "open-daimon.ai.spring-ai.models.list. Ollama available: " + ollamaAvailable +
                        ", OpenAI available: " + openAiAvailable));

        log.info("RAG: Selected embedding model '{}' (provider={}, priority={}). Primary provider: {}",
                modelConfig.getName(), modelConfig.getProviderType(), modelConfig.getPriority(), primaryProvider);

        String modelName = modelConfig.getName();
        EmbeddingModel embeddingModel = switch (modelConfig.getProviderType()) {
            case OLLAMA -> {
                OllamaApi api = ollamaApiProvider.getIfAvailable();
                yield OllamaEmbeddingModel.builder()
                        .ollamaApi(api)
                        .defaultOptions(OllamaEmbeddingOptions.builder().model(modelName).build())
                        .build();
            }
            case OPENAI -> {
                OpenAiApi api = openAiApiProvider.getIfAvailable();
                yield new OpenAiEmbeddingModel(api, MetadataMode.EMBED,
                        OpenAiEmbeddingOptions.builder().model(modelName).build());
            }
        };

        log.info("Creating SimpleVectorStore (in-memory) for RAG with model '{}' (provider: {})",
                modelName, modelConfig.getProviderType());
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    /**
     * Creates PDF document processing service.
     *
     * @param vectorStore store for embeddings
     * @param ragProperties RAG config
     * @return DocumentProcessingService instance
     */
    @Bean
    @ConditionalOnMissingBean
    public DocumentProcessingService documentProcessingService(
            VectorStore vectorStore,
            RAGProperties ragProperties) {
        log.info("Creating DocumentProcessingService with chunkSize={}, chunkOverlap={}",
                ragProperties.getChunkSize(), ragProperties.getChunkOverlap());
        return new DocumentProcessingService(vectorStore, ragProperties);
    }

    /**
     * Creates RAG search service.
     *
     * @param vectorStore store for embeddings
     * @param ragProperties RAG config
     * @return RAGService instance
     */
    @Bean
    @ConditionalOnMissingBean
    public FileRAGService ragService(
            VectorStore vectorStore,
            RAGProperties ragProperties) {
        log.info("Creating RAGService with topK={}, similarityThreshold={}",
                ragProperties.getTopK(), ragProperties.getSimilarityThreshold());
        return new FileRAGService(vectorStore, ragProperties);
    }
}
