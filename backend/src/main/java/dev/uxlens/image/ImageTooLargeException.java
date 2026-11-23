package dev.uxlens.image;

/** The upload exceeds the file size limit. The message is safe to show to the user. */
public class ImageTooLargeException extends RuntimeException {

    public ImageTooLargeException(String message) {
        super(message);
    }
}
