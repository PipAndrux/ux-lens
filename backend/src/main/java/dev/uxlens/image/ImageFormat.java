package dev.uxlens.image;

import java.util.Optional;

/** Accepted upload formats, recognized by their magic bytes rather than the client's Content-Type. */
public enum ImageFormat {
    PNG("image/png"),
    JPEG("image/jpeg"),
    WEBP("image/webp");

    private final String mediaType;

    ImageFormat(String mediaType) {
        this.mediaType = mediaType;
    }

    public String mediaType() {
        return mediaType;
    }

    public static Optional<ImageFormat> detect(byte[] data) {
        if (startsWith(data, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (startsWith(data, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(data, 0, 'R', 'I', 'F', 'F') && startsWith(data, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int offset, int... expected) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
