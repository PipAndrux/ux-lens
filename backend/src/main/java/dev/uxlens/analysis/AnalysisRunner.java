package dev.uxlens.analysis;

import dev.uxlens.ai.AnalysisProvider;
import dev.uxlens.ai.AnalysisProviderException;
import dev.uxlens.ai.AnalysisRequest;
import dev.uxlens.ai.AnalysisResult;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs the provider on a pending analysis and stores the outcome. The provider call happens outside any database
 * transaction, since it can take a minute: one short transaction marks the analysis RUNNING, another stores the
 * result (DONE) or the error (FAILED). Called synchronously for now; the job queue will call it later.
 */
@Component
public class AnalysisRunner {

    private static final Logger log = LoggerFactory.getLogger(AnalysisRunner.class);

    private final AnalysisRepository analyses;
    private final AnalysisProvider provider;
    private final TransactionTemplate tx;

    public AnalysisRunner(AnalysisRepository analyses, AnalysisProvider provider,
                          PlatformTransactionManager transactionManager) {
        this.analyses = analyses;
        this.provider = provider;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public void run(UUID analysisId) {
        AnalysisRequest request = tx.execute(status -> {
            Analysis analysis = load(analysisId);
            analysis.markRunning();
            return new AnalysisRequest(analysis.getId(), analysis.getImageData(), analysis.getImageWidth(),
                    analysis.getImageHeight());
        });

        AnalysisResult result;
        try {
            result = provider.analyze(request);
        } catch (AnalysisProviderException e) {
            log.warn("Analysis {} failed: {}", analysisId, e.getMessage());
            fail(analysisId, e.getMessage());
            return;
        } catch (RuntimeException e) {
            log.error("Analysis {} failed unexpectedly", analysisId, e);
            fail(analysisId, "Something went wrong while analyzing the image.");
            return;
        }

        tx.executeWithoutResult(status -> {
            List<Issue> issues = result.findings().stream()
                    .map(f -> new Issue(f.title(), f.description(), f.severity(), f.category(), f.criterionId(),
                            f.suggestion(), f.boundingBox()))
                    .toList();
            load(analysisId).complete(result.provider(), result.model(), result.summary(),
                    (short) result.scores().accessibility(), (short) result.scores().visualHierarchy(),
                    (short) result.scores().consistency(), issues);
        });
    }

    private void fail(UUID analysisId, String message) {
        tx.executeWithoutResult(status -> load(analysisId).fail(message));
    }

    private Analysis load(UUID id) {
        return analyses.findById(id).orElseThrow(() -> new IllegalStateException("No analysis " + id));
    }
}
