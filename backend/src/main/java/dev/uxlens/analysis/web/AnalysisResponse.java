package dev.uxlens.analysis.web;

import dev.uxlens.analysis.Analysis;
import dev.uxlens.analysis.AnalysisStatus;
import dev.uxlens.analysis.BoundingBox;
import dev.uxlens.analysis.Issue;
import dev.uxlens.analysis.IssueCategory;
import dev.uxlens.analysis.Severity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public JSON shape of an analysis. Kept separate from the entity so the API doesn't change with the schema. */
public record AnalysisResponse(
        UUID id,
        AnalysisStatus status,
        Instant createdAt,
        ImageInfo image,
        String summary,
        Scores scores,
        List<IssueResponse> issues,
        String error) {

    public record ImageInfo(String url, String mediaType, int width, int height) {
    }

    public record Scores(Short accessibility, Short visualHierarchy, Short consistency) {
    }

    public record IssueResponse(
            UUID id,
            String title,
            String description,
            Severity severity,
            IssueCategory category,
            String criterionId,
            String suggestion,
            BoundingBox boundingBox) {
    }

    static AnalysisResponse from(Analysis a) {
        Scores scores = a.getStatus() == AnalysisStatus.DONE
                ? new Scores(a.getScoreAccessibility(), a.getScoreHierarchy(), a.getScoreConsistency())
                : null;
        List<IssueResponse> issues = a.getIssues().stream()
                .map(i -> new IssueResponse(i.getId(), i.getTitle(), i.getDescription(), i.getSeverity(),
                        i.getCategory(), i.getCriterionId(), i.getSuggestion(), i.getBoundingBox()))
                .toList();
        return new AnalysisResponse(
                a.getId(),
                a.getStatus(),
                a.getCreatedAt(),
                new ImageInfo("/api/analyses/" + a.getId() + "/image", a.getImageMediaType(),
                        a.getImageWidth(), a.getImageHeight()),
                a.getSummary(),
                scores,
                issues,
                a.getErrorMessage());
    }
}
