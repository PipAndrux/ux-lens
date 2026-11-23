package dev.uxlens.ai;

/** The provider could not produce a result. The message is meant for end users, so it never holds internals. */
public class AnalysisProviderException extends RuntimeException {

    public AnalysisProviderException(String message) {
        super(message);
    }

    public AnalysisProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
