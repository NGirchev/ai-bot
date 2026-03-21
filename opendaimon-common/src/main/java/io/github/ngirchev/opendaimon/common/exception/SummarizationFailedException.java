package io.github.ngirchev.opendaimon.common.exception;

/**
 * Thrown when conversation summarization fails (e.g. AI call error).
 * The user should start a new session.
 */
public class SummarizationFailedException extends RuntimeException {

    public SummarizationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
