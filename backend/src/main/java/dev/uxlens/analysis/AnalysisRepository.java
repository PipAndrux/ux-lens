package dev.uxlens.analysis;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRepository extends JpaRepository<Analysis, UUID> {

    /** Most recent finished analysis of the same image, so it can be reused instead of re-analyzed. */
    Optional<Analysis> findFirstByImageSha256AndStatusOrderByCreatedAtDesc(String imageSha256, AnalysisStatus status);
}
