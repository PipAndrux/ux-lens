package dev.uxlens.ai;

import dev.uxlens.analysis.BoundingBox;
import dev.uxlens.analysis.IssueCategory;
import dev.uxlens.analysis.Severity;
import java.util.List;

/** What a provider found. Scores are 0..100; findings keep the order the provider ranked them in. */
public record AnalysisResult(
        String provider,
        String model,
        String summary,
        Scores scores,
        List<Finding> findings) {

    public AnalysisResult {
        findings = List.copyOf(findings);
    }

    public record Scores(int accessibility, int visualHierarchy, int consistency) {
    }

    /**
     * One problem on the screen.
     *
     * @param criterionId guideline id such as "WCAG-1.4.3" or "NIELSEN-4", or null when none applies
     * @param boundingBox where the problem is, or null when it concerns the whole screen
     */
    public record Finding(
            String title,
            String description,
            Severity severity,
            IssueCategory category,
            String criterionId,
            String suggestion,
            BoundingBox boundingBox) {
    }
}
