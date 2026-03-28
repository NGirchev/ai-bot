# Image-Only PDF: Vision Cache Sequence Diagram

> **Fixture test:** `ImagePdfVisionCacheFixtureIT` — run with `./mvnw clean verify -pl opendaimon-app -am -Pfixture`

When a user uploads an image-only PDF (scan, certificate, etc.), the system extracts text
via a vision-capable model and caches it in VectorStore for follow-up queries.

## First Message (PDF Upload)

```mermaid
sequenceDiagram
    actor User
    participant TG as TelegramFileService
    participant GW as SpringAIGateway
    participant DPS as DocumentProcessingService
    participant CS as SpringAIChatService
    participant VS as VectorStore
    participant LLM as Vision Model
    participant Chat as Chat Model

    User->>TG: Send image-only PDF + question
    TG->>TG: Download file from Telegram, save to MinIO
    TG->>GW: ChatAICommand(text, attachments)

    GW->>GW: processRagIfEnabled()
    GW->>DPS: processPdf(pdfBytes, filename)
    DPS->>DPS: PDFBox: extract text
    DPS-->>GW: throw DocumentContentNotExtractableException

    Note over GW: Fallback: render PDF pages as JPEG images

    GW->>GW: renderPdfToImageAttachments(pdfBytes)
    GW->>GW: Add JPEG images to attachments list

    Note over GW,LLM: Vision Cache: extract text via vision model

    GW->>GW: extractTextFromImagesViaVision()
    GW->>GW: Select VISION+CHAT model from registry
    GW->>CS: callSimpleVision(visionModel, [UserMessage + Media])
    CS->>LLM: Send images + extraction prompt
    LLM-->>CS: Extracted text content
    CS-->>GW: extractedText

    GW->>DPS: processExtractedText(extractedText, filename)
    DPS->>DPS: TokenTextSplitter: split into chunks
    DPS->>VS: add(chunks) with metadata type="pdf-vision"
    DPS-->>GW: documentId

    GW->>VS: findRelevantContext(userQuery, documentId)
    VS-->>GW: relevantChunks

    GW->>GW: createAugmentedPrompt(userQuery, relevantChunks)

    Note over GW: Build UserMessage: augmented prompt + JPEG images

    GW->>Chat: Send augmented prompt + images
    Chat-->>GW: Response
    GW-->>User: Answer (with both visual and RAG context)
```

## Follow-Up Message (No Attachments)

```mermaid
sequenceDiagram
    actor User
    participant GW as SpringAIGateway
    participant Mem as SummarizingChatMemory
    participant VS as VectorStore
    participant Chat as Chat Model

    User->>GW: Follow-up question (no attachments)

    GW->>GW: processRagIfEnabled()
    Note over GW: No attachments — skip embedding

    GW->>Mem: get(conversationId)
    Mem-->>GW: Chat history (includes "[Attached files: ...]" annotation)

    Note over GW,VS: VectorStore still has chunks from vision extraction

    GW->>Chat: Send chat history + user question
    Chat->>Chat: Model sees previous context in history
    Chat-->>GW: Response
    GW-->>User: Answer based on chat history

    Note over User,VS: If RAG re-query is needed (e.g. new document question),<br/>VectorStore chunks with type="pdf-vision" are available
```

## Key Design Decisions

1. **Vision extraction is a separate internal call** — uses `callSimpleVision()` without
   ChatMemory, web tools, or conversationId to avoid polluting chat history.
   Internal token budget is resolved safely even when command chat options are absent.

2. **Extracted text stored as regular RAG chunks** — `type="pdf-vision"` in metadata
   distinguishes them from PDFBox-extracted chunks, but they are searchable via the
   same `FileRAGService.findRelevantContext()`.

3. **First message gets both visual and RAG context** — JPEG images are still sent as
   Media objects for the current message, so the model can "see" the document.
   The augmented prompt with extracted text provides searchable context.
   If final payload contains media, model selection requires `VISION`.

4. **Follow-up messages use RAG** — the vision-extracted text persists in VectorStore,
   so subsequent questions can find relevant chunks via semantic search.
