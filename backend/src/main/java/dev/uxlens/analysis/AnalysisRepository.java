package dev.uxlens.analysis;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRepository extends JpaRepository<Analysis, UUID> {

    /**
     * Most recent finished analysis of the same image, so it can be reused instead of re-analyzed.
     * Issues are fetched in the same query because the result is returned to the client.
     */
    @EntityGraph(attributePaths = "issues")
    Optional<Analysis> findFirstByImageSha256AndStatusOrderByCreatedAtDesc(String imageSha256, AnalysisStatus status);

    /** An analysis together with its issues, ready to be turned into a response. */
    @EntityGraph(attributePaths = "issues")
    Optional<Analysis> findWithIssuesById(UUID id);
}
