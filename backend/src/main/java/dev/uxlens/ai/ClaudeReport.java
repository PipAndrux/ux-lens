package dev.uxlens.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.uxlens.analysis.IssueCategory;
import dev.uxlens.analysis.Severity;
import java.util.List;

/**
 * JSON shape Claude must answer with. The SDK derives the structured-output schema from these records, so the
 * descriptions below are part of the prompt.
 */
public record ClaudeReport(
        @JsonPropertyDescription("Two or three sentences on the overall quality of the screen and its biggest problem.")
        String summary,
        @JsonPropertyDescription("0-100. Accessibility: contrast, text size, labels, touch targets, focus visibility.")
        int accessibilityScore,
        @JsonPropertyDescription("0-100. Visual hierarchy: is the main action and the reading order obvious?")
        int visualHierarchyScore,
        @JsonPropertyDescription("0-100. Consistency: spacing, alignment, typography, colors and components.")
        int consistencyScore,
        @JsonPropertyDescription("Problems found, most severe first. At most 15.")
        List<Issue> issues) {

    public record Issue(
            @JsonPropertyDescription("Short headline of the problem, at most 80 characters.")
            String title,
            @JsonPropertyDescription("What is wrong and why it matters to users, citing what is visible on screen.")
            String description,
            Severity severity,
            IssueCategory category,
            @JsonPropertyDescription("The guideline violated: WCAG-<success criterion>, e.g. WCAG-1.4.3, or "
                    + "NIELSEN-<1..10> for Nielsen's heuristics. NONE when no single guideline applies.")
            String criterionId,
            @JsonPropertyDescription("A concrete fix a designer can apply.")
            String suggestion,
            @JsonPropertyDescription("Box around the affected element, as fractions of the image width and height. "
                    + "Use x=0, y=0, width=1, height=1 when the problem concerns the whole screen.")
            Box box) {
    }

    public record Box(
            @JsonPropertyDescription("Left edge, 0-1 of the image width.") double x,
            @JsonPropertyDescription("Top edge, 0-1 of the image height.") double y,
            @JsonPropertyDescription("Width, 0-1 of the image width.") double width,
            @JsonPropertyDescription("Height, 0-1 of the image height.") double height) {
    }
}
