# RAG (Retrieval-Augmented Generation) Logic

## Architecture Overview

RAG in open-daimon is **prompt-based** (not function calling). Documents are split into chunks, indexed in VectorStore, and relevant chunks are inserted into an augmented prompt at query time.

### Components

| Component | File | Role |
|-----------|------|------|
| `DocumentProcessingService` | `opendaimon-spring-ai/.../service/DocumentProcessingService.java` | ETL pipeline: Extract → Transform → Load into VectorStore |
| `FileRAGService` | `opendaimon-spring-ai/.../rag/FileRAGService.java` | VectorStore search + augmented prompt construction |
| `SpringAIGateway` | `opendaimon-spring-ai/.../service/SpringAIGateway.java` | Orchestration: decides when and how to invoke RAG |
| `SummarizingChatMemory` | `opendaimon-spring-ai/.../memory/SummarizingChatMemory.java` | Loads message history from DB into Spring AI Messages |
| `RAGProperties` | `opendaimon-spring-ai/.../config/RAGProperties.java` | Configuration: chunk-size, top-k, similarity-threshold, prompts |
| `SimpleVectorStore` | Spring AI (in-memory) | Embedding storage (lost on restart) |

### Configuration (application.yml)

```yaml
open-daimon.ai.spring-ai.rag:
  enabled: true
  chunk-size: 800        # tokens per chunk
  chunk-overlap: 100     # overlap between chunks
  top-k: 5               # number of chunks to return
  similarity-threshold: 0.7
  prompts:
    augmented-prompt-template: |
      Based on the following context from the document, answer the user's question.
      If the context doesn't contain relevant information to answer the question,
      say that you couldn't find the answer in the provided documents.

      Context:
      %s

      Question: %s
```

---

## Document Processing Flow (first message with attachment)

```
Telegram: user sends PDF without caption text
    │
    ▼
TelegramBot.mapToTelegramDocumentCommand()
    │  empty caption → default localized prompt is used for `userText`
    │  example prompt sent to model:
    │  "Analyze this document and provide a brief summary."
    ▼
TelegramFileService.processDocument() → Attachment (key, mimeType, data)
    │
    ▼
TelegramMessageService.saveUserMessage()
    │  Saves to OpenDaimonMessage with attachments JSONB:
    │  [{"storageKey": "document/uuid.pdf", "mimeType": "application/pdf",
    │    "filename": "report.pdf", "expiresAt": "..."}]
    ▼
DefaultAICommandFactory → ChatAICommand (with attachments)
    │
    ▼
SpringAIGateway.generateResponse()
    ▼
addSystemAndUserMessagesIfNeeded()
    │
    ├── processRagIfEnabled(userQuery, attachments)
    │       │
    │       ├── documentAttachments not empty → process document
    │       │       │
    │       │       ▼
    │       │   processOneDocumentForRag()
    │       │       │
    │       │       ├── PDF with text → DocumentProcessingService.processPdf()
    │       │       │       │  1. PagePdfDocumentReader (PDFBox) → pages
    │       │       │       │  2. TokenTextSplitter → chunks (800 tokens, 100 overlap)
    │       │       │       │  3. Metadata: documentId (UUID), originalName, type
    │       │       │       │  4. VectorStore.add(chunks)
    │       │       │       ▼
    │       │       │   FileRAGService.findRelevantContext(query, documentId)
    │       │       │       │  → top-5 chunks with similarity > 0.7
    │       │       │       ▼
    │       │       │   FileRAGService.createAugmentedPrompt(query, chunks)
    │       │       │       → "Context:\n...\n\nQuestion: ..."
    │       │       │
    │       │       └── PDF without text (scan) → DocumentContentNotExtractableException
    │       │               │  renderPdfToImageAttachments() → JPEG images
    │       │               │  pdfAsImageFilenames += filename
    │       │               ▼
    │       │           Vision fallback (images sent to model)
    │       │
    │       └── documentAttachments empty → searchPreviousDocumentsIfAvailable()
    │               (see "Follow-up Queries" below)
    │
    ├── addAttachmentContextToMessagesAndMemory()
    │       │  Creates SystemMessage: "User attached PDF document \"file.pdf\" represented as images."
    │       │  Adds to messages + saves to ChatMemory
    │       ▼
    └── createUserMessage(finalUserRole, attachments)
            │  If IMAGE attachments present → UserMessage with Media (multimodal)
            │  Otherwise → plain text UserMessage
            ▼
        SpringAIChatService.streamChat() → model response
```

---

## Follow-up Query Flow (no attachment)

This is a key change. Previously, follow-up queries without attachments completely bypassed RAG.

### Problem (before changes)

1. User sends PDF → RAG processes it, model responds
2. User asks "what's in the file?" → RAG is skipped (`documentAttachments.isEmpty()` → return)
3. Model has no knowledge of the file because:
   - History is loaded as plain text (no attachment info)
   - VectorStore is not queried
   - Attachment context SystemMessage may be lost during summarization

### Solution (after changes)

Two mechanisms work together:

#### 1. History enrichment with attachment metadata (`SummarizingChatMemory`)

When loading messages from DB during summarization, `convertToSpringMessage()` now calls `enrichWithAttachmentInfo()`:

```
Before: USER → "Analyze this document and provide a brief summary."
After:  USER → "Analyze this document and provide a brief summary.\n[Attached files: \"report.pdf\" (application/pdf)]"
```

The model now sees in conversation history which files the user previously uploaded.

#### 2. Search across previously indexed documents (`SpringAIGateway`)

```
processRagIfEnabled(userQuery="what's in the file?", attachments=[])
    │
    ▼
documentAttachments is empty
    │
    ▼
searchPreviousDocumentsIfAvailable(userQuery, fileRagService)
    │
    ├── fileRagService == null or query is blank → return userQuery as-is
    │
    ├── fileRagService.findRelevantContext(userQuery)  ← WITHOUT documentId filter
    │       → searches ALL documents in VectorStore
    │       → top-5 chunks with similarity > 0.7
    │
    ├── no chunks found → return userQuery as-is
    │
    └── chunks found → createAugmentedPrompt(userQuery, chunks)
            → augmented prompt with context from previously uploaded documents
```

---

## Defensive Fallback for Empty Queries

If userQuery is empty/blank but a document is attached:

```java
// SpringAIGateway.processRagIfEnabled()
if (userQuery == null || userQuery.isBlank()) {
    userQuery = "Summarize this document and provide key points.";
}
```

In practice, with the default prompt fix (point 1), this case is unlikely — this is defence-in-depth.

---

## Localized Default Prompt for Documents

### Problem

Photos had a localized fallback prompt (`telegram.photo.default.prompt`), but documents used an empty string.

### Solution

`TelegramBot.mapToTelegramDocumentCommand()` now uses the same logic as `mapToTelegramPhotoCommand()`:

```java
String caption = message.getCaption();
String userText = caption != null && !caption.isBlank()
        ? caption
        : messageLocalizationService != null
                ? messageLocalizationService.getMessage("telegram.document.default.prompt", telegramUser.getLanguageCode())
                : "Analyze this document and provide a brief summary.";
```

`telegram.document.default.prompt` and `telegram.photo.default.prompt` are locale keys.
The final value is selected by `telegramUser.languageCode` through `MessageLocalizationService`.
The English literal in code is an emergency fallback only (when localization service is not wired).

---

## Data Storage

### OpenDaimonMessage.attachments (JSONB)

```json
[
  {
    "storageKey": "document/5e7a0879-10c5-4279-9106-ac805461e620.pdf",
    "mimeType": "application/pdf",
    "filename": "udemy_multithreading_cert.pdf",
    "expiresAt": "2026-03-27T19:38:27.130134794Z"
  }
]
```

**Important**: `documentId` (UUID used for VectorStore filtering) is **not persisted** here. It is generated in `DocumentProcessingService` and used only within the current request. Follow-up searches run without a documentId filter.

### VectorStore Chunk Metadata

```json
{
  "documentId": "a3f2c1d4-...",
  "originalName": "report.pdf",
  "type": "pdf"
}
```

---

## Known Limitations

| Limitation | Cause | Possible Solution |
|------------|-------|-------------------|
| VectorStore data lost on restart | SimpleVectorStore is in-memory | Migrate to PGVector or Elasticsearch |
| Follow-up search covers ALL documents from all users | No userId/threadId filtering | Persist documentId in attachments JSONB, filter by it |
| documentId is not persisted | Generated on-the-fly in DocumentProcessingService | Add documentId to OpenDaimonMessage.attachments |
| Attachment context SystemMessage may be lost during summarization | SummarizingChatMemory evicts old messages | `enrichWithAttachmentInfo` partially compensates |

---

## Changed Files

| File | Change |
|------|--------|
| `telegram_en.properties` | + `telegram.document.default.prompt` |
| `telegram_ru.properties` | + `telegram.document.default.prompt` |
| `TelegramBot.java` | Localized fallback prompt for documents (mirrors photo logic) |
| `TelegramBotTest.java` | +4 tests: EN/RU without caption, with caption, blank caption |
| `SpringAIGateway.java` | + `searchPreviousDocumentsIfAvailable()` for follow-up RAG; + defensive fallback for empty query |
| `SummarizingChatMemory.java` | + `enrichWithAttachmentInfo()` — attachment metadata from JSONB appended to history messages |

## Supported Document Formats

PDF, DOCX, DOC, XLSX, XLS, PPTX, PPT, TXT, RTF, ODT, ODS, ODP, CSV, HTML, MD, JSON, XML, EPUB.

PDF is processed via PDFBox (`PagePdfDocumentReader`), all others via Apache Tika (`TikaDocumentReader`).
