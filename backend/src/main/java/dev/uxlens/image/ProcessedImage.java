package dev.uxlens.image;

/**
 * A screenshot ready for analysis: re-encoded as PNG and scaled down so its longest side is at most
 * {@link ImageProcessor#MAX_LONG_SIDE}. {@code sha256} is the hash of the original upload, so the same file
 * always maps to the same hash.
 */
public record ProcessedImage(byte[] png, int width, int height, String sha256, ImageFormat originalFormat) {

    public String mediaType() {
        return "image/png";
    }
}
