package dev.uxlens.ai;

import java.util.UUID;

/** A normalized PNG screenshot (longest side at most 1568 px) to audit. */
public record AnalysisRequest(UUID analysisId, byte[] png, int width, int height) {
}
