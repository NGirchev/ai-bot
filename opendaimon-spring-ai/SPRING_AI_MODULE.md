# opendaimon-spring-ai — Internal Behavior Reference

## Overview

This module provides LLM provider integration (OpenAI/OpenRouter, Ollama) via Spring AI.
Key features: multi-model support, auto-rotation with retry, RAG, conversation memory, web tools.

---

## Entry Point

```
AICommand
  └─ SpringAIGateway.generateResponse(AICommand)
       ├─ model selection
       ├─ RAG augmentation (if documents present)
       ├─ message assembly (text + images + context)
       └─ SpringAIChatService.callChat() / .streamChat()
            └─ @RotateOpenRouterModels (AOP) → retry loop
```

---

## Commands (AICommand)

### ChatAICommand
Standard chat request. Model is selected automatically based on the capability set.

| Field                | Type                        | Description |
|----------------------|-----------------------------|-------------|
| `modelCapabilities`  | `Set<ModelCapabilities>`    | Required capabilities (CHAT, VISION, WEB, etc.) |
| `temperature`        | `double`                    | Generation temperature (default: 0.35) |
| `maxOutputTokens`    | `int`                       | Maximum tokens in response |
| `maxReasoningTokens` | `Integer`                   | Tokens for reasoning models (null = unlimited) |
| `systemRole`         | `String`                    | System prompt |
| `userText`           | `String`                    | User message text |
| `stream`             | `boolean`                   | Streaming mode |
| `attachments`        | `List<Attachment>`          | Files (images, documents) |
| `metadata`           | `Map<String, String>`       | USER_PRIORITY, ROLE, etc. |
| `body`               | `Map<String, Object>`       | Extra parameters (`max_price`, etc.) |

### FixedModelChatAICommand
Same as `ChatAICommand` but with an explicitly selected model. Model is looked up by name in the registry; capability-based filtering is not applied.

| Field          | Type     | Description |
|----------------|----------|-------------|
| `fixedModelId` | `String` | Model name (`google/gemma-3-27b-it:free`) |
| others         | —        | Same as `ChatAICommand` |

---

## Input Data Formats

### Attachment
```
Attachment
  ├─ type: IMAGE | PDF | DOCUMENT | AUDIO | VIDEO
  ├─ fileName: String
  ├─ mimeType: String
  └─ data: byte[]
```

### Request body (OpenDaimonChatOptions / Map body)
When called via `generateResponse(Map<String,Object>)` — raw OpenAI-compatible format:
```json
{
  "model": "openai/gpt-4o",
  "messages": [
    { "role": "system", "content": "You are helpful" },
    { "role": "user",   "content": "What is in the picture?" },
    { "role": "user",   "content": [
        { "type": "text",      "text": "Describe it" },
        { "type": "image_url", "image_url": { "url": "data:image/jpeg;base64,..." } }
      ]
    }
  ],
  "temperature": 0.7,
  "max_tokens": 1024,
  "max_price": { "prompt": 0, "completion": 0 }
}
```

---

## Model Selection

### AUTO mode (ChatAICommand)
1. `SpringAIModelRegistry.getCandidatesByCapabilities(capabilities, maxPrice, userPriority)`
2. Filter: model must support **all** requested capabilities
3. Filter by `allowedRoles` (if set in config — only for specified roles)
4. Sort candidates:
   - By `maxIndexOfCapability` (how specialized the model is)
   - By `priority` (higher = first)
   - By `ewmaLatencyMs` (for free models — lower = first)
5. Use first candidate

### Fixed mode (FixedModelChatAICommand)
1. Direct lookup by name: `springAIModelRegistry.getByModelName(fixedModelId)`
2. Capability check when cache is available:
   - `AUTO` is excluded from required capabilities (it is a routing hint, not a model attribute)
   - If the model is missing any required capability → `UnsupportedModelCapabilityException`
3. Explicit VISION check: if IMAGE attachments are present and model does not support VISION → exception

**Edge cases:**
- `VISION` in `modelCapabilities` but no image attachments → request succeeds; VISION is a model selector hint, not a gate on attachment presence. The model handles a text-only request normally.
- `VISION` in `modelCapabilities` and model supports VISION but no attachments → same: passes through, no error.

---

## Prompt Assembly (SpringAIPromptFactory)

### ChatOptions
- **OpenAI/OpenRouter**: `OpenAiChatOptions` with `temperature`, `maxTokens`, `frequencyPenalty`, `topP`
  - For free models: `extraBody.max_price = {prompt:0, completion:0}` is added
- **Ollama**: `OllamaChatOptions` (without `think` to avoid HTTP 400)

### Advisors
Application order:
1. `MessageChatMemoryAdvisor` — injects history from ChatMemory
2. `MessageOrderingAdvisor` — sorts messages: System → Summary → History → User

`MessageOrderingAdvisor` is activated only when the ChatMemory advisor is enabled.

### Web tools
If `WEB` is in capabilities → `WebTools` (web_search + fetch_url) are added to the request.

---

## Attachments and Multimodality

### Images
- Added as `Media` objects inside `UserMessage`
- Supports both URLs (`http://...`) and Data URIs (`data:image/jpeg;base64,...`)

### Documents (PDF, DOCX, etc.)
1. RAG pipeline (if enabled): text extraction, chunking, vector search, context injection into prompt
2. If RAG is disabled or document is empty → file name is added to a system message
3. Image-only PDF (scanned): converted to JPEG images (up to 10 pages) → processed by a vision model

---

## RAG (Retrieval-Augmented Generation)

### ETL Pipeline
```
File
  ├─ PDF     → PagePdfDocumentReader (PDFBox)
  └─ Other   → TikaDocumentReader (DOCX, XLSX, PPT, TXT...)
       └─ TokenTextSplitter(chunkSize, chunkOverlap)
            └─ EmbeddingModel → SimpleVectorStore (in-memory)
```

### Search
- `VectorStore.similaritySearch(query, topK, threshold, filterByDocumentId)`
- Found chunks are joined and inserted into the prompt using a template:
  ```
  Context:
  <retrieved text>

  Question: <user question>
  ```

---

## Conversation Memory (SummarizingChatMemory)

Wrapper around Spring AI `MessageWindowChatMemory`.

### Behavior on `get(conversationId)`
1. If message count < `historyWindowSize` → return as-is
2. If >= `historyWindowSize`:
   - Calls `summarizationService.summarizeThread()`
   - Clears `ChatMemory` (table `SPRING_AI_CHAT_MEMORY`)
   - Adds summary as `SystemMessage`
   - Returns only the summary

> Summarization is applied only for free models (to save tokens).

---

## Model Rotation (@RotateOpenRouterModels)

Aspect `OpenRouterModelRotationAspect` intercepts `callChat()` and `streamChat()`.

### Algorithm (sync)
```
candidates = getCandidatesByCapabilities(command.capabilities)
for each candidate:
  try:
    result = originalMethod(candidate, ...)
    recordSuccess(candidate, latencyMs)
    return result
  catch (retryable error):
    recordFailure(candidate, status, latencyMs)
    continue
  catch (non-retryable):
    throw
throw last error
```

### Algorithm (stream)
Same logic, but via `Flux.onErrorResume` — on stream error switches to the next candidate.

### Retryable errors
| Condition | Description |
|-----------|-------------|
| HTTP 429  | Rate limit |
| HTTP 402  | Insufficient credits |
| HTTP 404  | Model not found |
| HTTP 5xx  | Server error |
| HTTP 400 + "Conversation roles must alternate" | Wrong message role order |
| `OpenRouterEmptyStreamException` | Response contains only reasoning, no content |
| Transport timeout | Network timeout |

### AUTO mode with streaming
If the only candidate is `AUTO`, a `CHAT` fallback is added for the second attempt
(to handle `OpenRouterEmptyStreamException` from reasoning models).

---

## Model Statistics and Ranking

Each model has `ModelStats`:
- `ewmaLatencyMs` — EWMA latency (alpha from config, default 0.2)
- `lastStatus` — last HTTP status code
- `cooldownUntilEpochMs` — model is skipped until this timestamp

**Score** (for free model sorting):
```
score = 100 - (ewmaMs / 200) - cooldown_penalty
```

Models in cooldown get score < 0 → pushed to the end of the list.

---

## Dynamic Free Model Updates

`SpringAIModelRegistryRefreshScheduler` periodically:
1. GET `/v1/models` → list of OpenRouter models
2. Filters: `pricing.prompt == 0 && pricing.completion == 0`
3. Applies `includeModelIds`, `includeContains`, `excludeModelIds`, `excludeContains`
4. Maps capabilities via `OpenRouterModelCapabilitiesMapper`:
   - `TOOL_CALLING` — if `tools`/`tool_choice` in `supported_parameters`
   - `VISION` — if `image` in `input_modalities` or `vision` in `capabilities`
   - `STRUCTURED_OUTPUT` — if `response_format`/`json_schema` in `supported_parameters`
5. Removes yml models that have disappeared from the API (deprecated)
6. Adds new models with `priority=100` and `FREE` in capabilities

---

## Web Tools (WebTools)

Available for models with `WEB` in capabilities. Passed as function tools.

### `web_search(query: String) → String`
- Calls Serper API, returns top 6 results (title, url, snippet ≤ 300 chars)
- On error: empty string

### `fetch_url(url: String) → String`
- Fetches HTML, extracts plain text (removes script/style/nav/footer/header)
- Truncates to 6000 chars
- On error: empty string

## Providers

| `provider-type` | Client       | Notes |
|-----------------|--------------|-------|
| `OPENAI`        | OpenAI SDK   | Supports `extraBody`, `max_price`, streaming |
| `OLLAMA`        | Ollama client| No `think` parameter, local inference |

---

## ModelCapabilities Reference

| Capability          | Meaning |
|---------------------|---------|
| `AUTO`              | Routing hint: tools selected automatically. **Not checked against model capabilities.** |
| `RAW_TYPE`          | Model name passed as-is (for OpenRouter) |
| `CHAT`              | Text generation / dialog |
| `EMBEDDING`         | Text vectorization |
| `RERANK`            | Reranking (after vector search) |
| `MODERATION`        | Content moderation |
| `SUMMARIZATION`     | Summarization |
| `STRUCTURED_OUTPUT` | JSON by schema |
| `TOOL_CALLING`      | Function calling |
| `WEB`               | Web search + fetch URL |
| `VISION`            | Image understanding |
| `FREE`              | Free-tier model (OpenRouter free) |

---

## User Priorities and Base Capabilities

| `UserPriority` | `baseModelCapabilities`                         | Extra behavior |
|----------------|-------------------------------------------------|----------------|
| `ADMIN`        | `{AUTO}`                                        | — |
| `VIP`          | `{CHAT, MODERATION, TOOL_CALLING, WEB}`         | `max_price=0` |
| `REGULAR`      | `{CHAT}`                                        | — |

> `AUTO` → selects the best available model without restrictions.
> If IMAGE attachments are present → `VISION` is added to any capability set.
> If `fixedModelId` is set → `AUTO` is excluded from capability compatibility checks.
