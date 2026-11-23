package dev.uxlens.image;

/** The upload is not an image we can analyze. The message is safe to show to the user. */
public class InvalidImageException extends RuntimeException {

    public InvalidImageException(String message) {
        super(message);
    }

    public InvalidImageException(String message, Throwable cause) {
        super(message, cause);
    }
}
