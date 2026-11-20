package dev.uxlens.analysis;

import dev.uxlens.image.ImageProcessor;
import dev.uxlens.image.ProcessedImage;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisService {

    private final AnalysisRepository analyses;
    private final ImageProcessor imageProcessor;

    public AnalysisService(AnalysisRepository analyses, ImageProcessor imageProcessor) {
        this.analyses = analyses;
        this.imageProcessor = imageProcessor;
    }

    /**
     * Validates and stores an uploaded screenshot. If the exact same file was already analyzed successfully, that
     * result is returned instead of creating a new analysis, which saves an API call.
     */
    @Transactional
    public Submission submit(byte[] upload) {
        ProcessedImage image = imageProcessor.process(upload);

        Optional<Analysis> previous =
                analyses.findFirstByImageSha256AndStatusOrderByCreatedAtDesc(image.sha256(), AnalysisStatus.DONE);
        if (previous.isPresent()) {
            return new Submission(previous.get(), true);
        }

        Analysis analysis = new Analysis(image.sha256(), image.mediaType(), image.width(), image.height(), image.png());
        return new Submission(analyses.save(analysis), false);
    }

    @Transactional(readOnly = true)
    public Optional<Analysis> find(UUID id) {
        return analyses.findWithIssuesById(id);
    }

    /** {@code reused} is true when an earlier result for the same image was returned. */
    public record Submission(Analysis analysis, boolean reused) {
    }
}
