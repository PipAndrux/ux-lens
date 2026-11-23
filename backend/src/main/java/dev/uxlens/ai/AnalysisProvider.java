package dev.uxlens.ai;

/**
 * Port to whatever produces the UX/accessibility audit of a screenshot. The app ships with a free mock
 * implementation as the default and a Claude implementation that is only used when configured (ADR 0002).
 */
public interface AnalysisProvider {

    /** Short id stored with every analysis, e.g. "mock" or "claude". */
    String name();

    /**
     * Audits one screenshot. Blocks until the result is available.
     *
     * @throws AnalysisProviderException when no result can be produced; its message is safe to show to users
     */
    AnalysisResult analyze(AnalysisRequest request);
}
