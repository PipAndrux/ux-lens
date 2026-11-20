package dev.uxlens.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Issue location on the screenshot, normalized to the image size: (0,0) is the top-left corner,
 * (1,1) the bottom-right. Independent of the size the image is displayed at.
 */
@Embeddable
public record BoundingBox(
        @Column(name = "bbox_x") float x,
        @Column(name = "bbox_y") float y,
        @Column(name = "bbox_w") float width,
        @Column(name = "bbox_h") float height) {

    private static final float EPSILON = 1e-4f;

    public BoundingBox {
        if (x < 0 || y < 0 || width <= 0 || height <= 0
                || x + width > 1 + EPSILON || y + height > 1 + EPSILON) {
            throw new IllegalArgumentException(
                    "Bounding box must lie inside the normalized image: " + x + "," + y + "," + width + "," + height);
        }
    }
}
