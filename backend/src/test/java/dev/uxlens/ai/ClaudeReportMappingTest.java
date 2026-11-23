package dev.uxlens.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.uxlens.analysis.BoundingBox;
import dev.uxlens.analysis.IssueCategory;
import dev.uxlens.analysis.Severity;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The model's answer is untrusted input: anything out of range is repaired or dropped before it reaches the DB. */
class ClaudeReportMappingTest {

    @Test
    void keepsOnlyWellFormedCriterionIds() {
        assertThat(ClaudeAnalysisProvider.criterionId(" wcag-2.5.8 ")).isEqualTo("WCAG-2.5.8");
        assertThat(ClaudeAnalysisProvider.criterionId("NIELSEN-10")).isEqualTo("NIELSEN-10");
        assertThat(ClaudeAnalysisProvider.criterionId("NONE")).isNull();
        assertThat(ClaudeAnalysisProvider.criterionId("NIELSEN-11")).isNull();
        assertThat(ClaudeAnalysisProvider.criterionId("WCAG 1.4.3")).isNull();
        assertThat(ClaudeAnalysisProvider.criterionId(null)).isNull();
    }

    @Test
    void clampsBoxesIntoTheImage() {
        assertThat(ClaudeAnalysisProvider.boundingBox(new ClaudeReport.Box(0.8, -0.1, 0.5, 0.3)))
                .isEqualTo(new BoundingBox(0.8f, 0f, 0.2f, 0.3f));
    }

    @Test
    void dropsWholeScreenAndDegenerateBoxes() {
        assertThat(ClaudeAnalysisProvider.boundingBox(new ClaudeReport.Box(0, 0, 1, 1))).isNull();
        assertThat(ClaudeAnalysisProvider.boundingBox(new ClaudeReport.Box(0.5, 0.5, 0, 0.2))).isNull();
        assertThat(ClaudeAnalysisProvider.boundingBox(new ClaudeReport.Box(1.2, 0.5, 0.2, 0.2))).isNull();
        assertThat(ClaudeAnalysisProvider.boundingBox(new ClaudeReport.Box(Double.NaN, 0.1, 0.2, 0.2)))
                .isEqualTo(new BoundingBox(0f, 0.1f, 0.2f, 0.2f));
        assertThat(ClaudeAnalysisProvider.boundingBox(null)).isNull();
    }

    @Test
    void clampsScoresSkipsIncompleteIssuesAndCapsTheList() {
        List<ClaudeReport.Issue> issues = new ArrayList<>();
        issues.add(new ClaudeReport.Issue(" ", "no title", Severity.MINOR, IssueCategory.USABILITY, "NONE", "",
                null));
        issues.add(new ClaudeReport.Issue("No severity", "", null, IssueCategory.USABILITY, "NONE", "", null));
        for (int i = 0; i < 20; i++) {
            issues.add(new ClaudeReport.Issue("x".repeat(300), null, Severity.MINOR, IssueCategory.CONSISTENCY,
                    "NIELSEN-4", null, null));
        }

        AnalysisResult result = ClaudeAnalysisProvider.toResult("claude-opus-5-5",
                new ClaudeReport(null, 140, -3, 50, issues));

        assertThat(result.scores()).isEqualTo(new AnalysisResult.Scores(100, 0, 50));
        assertThat(result.summary()).isEmpty();
        assertThat(result.findings()).hasSize(ClaudeAnalysisProvider.MAX_FINDINGS).allSatisfy(f -> {
            assertThat(f.title()).hasSize(ClaudeAnalysisProvider.MAX_TITLE_LENGTH);
            assertThat(f.description()).isEmpty();
        });
    }

    @Test
    void acceptsAReportWithoutIssues() {
        AnalysisResult result = ClaudeAnalysisProvider.toResult("claude-opus-5-5",
                new ClaudeReport("Clean.", 95, 92, 97, null));

        assertThat(result.findings()).isEmpty();
    }
}
