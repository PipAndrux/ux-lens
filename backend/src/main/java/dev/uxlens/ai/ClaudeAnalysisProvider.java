package dev.uxlens.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.OverloadedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.beta.messages.BetaBase64ImageSource;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaUsage;
import com.anthropic.models.beta.messages.StructuredMessage;
import com.anthropic.models.beta.messages.StructuredMessageCreateParams;
import com.anthropic.models.beta.messages.StructuredOutputConfig;
import com.anthropic.models.beta.messages.StructuredTextBlock;
import dev.uxlens.ai.AnalysisResult.Finding;
import dev.uxlens.ai.AnalysisResult.Scores;
import dev.uxlens.analysis.BoundingBox;
import dev.uxlens.usage.ApiUsage;
import dev.uxlens.usage.ApiUsageRepository;
import dev.uxlens.usage.ModelPricing;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Audits a screenshot with Claude (vision + structured output) through the official Anthropic Java SDK. Every call
 * that reaches the API is logged to {@code api_usage} with its cost, including refused or truncated ones, because
 * those are billed too. Only created when {@code uxlens.ai.provider=claude} (see {@link AiProviderConfiguration}).
 */
public class ClaudeAnalysisProvider implements AnalysisProvider {

    public static final String NAME = "claude";

    /** Beta that lets the API retry a refused request on another model by itself ({@code fallbacks: "default"}). */
    static final String REFUSAL_FALLBACK_BETA = "server-side-fallback-2026-07-01";
    /** Thinking tokens count toward this too; 16k keeps a non-streaming call well inside the SDK timeout. */
    static final long MAX_TOKENS = 16_000;
    static final int MAX_FINDINGS = 15;
    static final int MAX_TITLE_LENGTH = 200;

    private static final Pattern CRITERION_ID = Pattern.compile("WCAG-\\d\\.\\d{1,2}\\.\\d{1,2}|NIELSEN-(10|[1-9])");
    /** A box covering at least this share of both sides is treated as "the whole screen". */
    private static final double WHOLE_SCREEN = 0.95;

    static final String SYSTEM_PROMPT = """
            You are a senior UX and accessibility auditor. You review a single screenshot of a user interface \
            against Nielsen's 10 usability heuristics and WCAG 2.2 level AA.

            Report only problems you can see in the screenshot. Do not guess about behavior you cannot observe, \
            such as keyboard navigation or screen-reader output, unless the image gives clear evidence. Prefer \
            fewer, well-founded issues over a long list.

            Severity:
            - CRITICAL: blocks some users from completing the task, or fails a WCAG A/AA criterion outright.
            - MAJOR: causes real friction or confusion for many users.
            - MINOR: polish; noticeable but does not stop anyone.

            Category: ACCESSIBILITY, VISUAL_HIERARCHY, CONSISTENCY or USABILITY (anything else).

            Boxes use coordinates normalized to the image: (0, 0) is the top-left corner and (1, 1) the \
            bottom-right. Draw the box tightly around the affected element.

            Scores go from 0 (unusable) to 100 (no problems) and must be consistent with the issues you list.""";

    private static final Logger log = LoggerFactory.getLogger(ClaudeAnalysisProvider.class);

    private final AnthropicClient client;
    private final String model;
    private final ApiUsageRepository usage;

    public ClaudeAnalysisProvider(AnthropicClient client, String model, ApiUsageRepository usage) {
        this.client = client;
        this.model = model;
        this.usage = usage;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public AnalysisResult analyze(AnalysisRequest request) {
        StructuredMessage<ClaudeReport> response = send(buildParams(request));
        String servedModel = response.model().asString();
        recordUsage(request.analysisId(), servedModel, response.usage());

        BetaStopReason stopReason = response.stopReason().orElse(null);
        if (BetaStopReason.REFUSAL.equals(stopReason)) {
            log.info("Analysis {} refused: {}", request.analysisId(), response.stopDetails().orElse(null));
            throw new AnalysisProviderException("The AI declined to analyze this image.");
        }
        if (BetaStopReason.MAX_TOKENS.equals(stopReason)) {
            throw new AnalysisProviderException(
                    "The analysis ran out of space before it finished. Try a screenshot with less on it.");
        }
        return toResult(servedModel, readReport(response));
    }

    StructuredMessageCreateParams<ClaudeReport> buildParams(AnalysisRequest request) {
        BetaBase64ImageSource image = BetaBase64ImageSource.builder()
                .mediaType(BetaBase64ImageSource.MediaType.IMAGE_PNG)
                .data(Base64.getEncoder().encodeToString(request.png()))
                .build();
        String instruction = "Audit this screenshot. It is %d x %d pixels.".formatted(request.width(), request.height());

        return com.anthropic.models.beta.messages.MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .system(SYSTEM_PROMPT)
                // Opus 5.5 always thinks; effort is the only dial and defaults to medium, so set it explicitly.
                .outputConfig(StructuredOutputConfig.<ClaudeReport>builder()
                        .format(ClaudeReport.class)
                        .effort(BetaOutputConfig.Effort.MEDIUM)
                        .build())
                .addBeta(REFUSAL_FALLBACK_BETA)
                .fallbacksDefault()
                .addUserMessageOfContentBlockParams(List.of(
                        BetaContentBlockParam.ofImage(image),
                        BetaContentBlockParam.ofText(instruction)))
                .build();
    }

    private StructuredMessage<ClaudeReport> send(StructuredMessageCreateParams<ClaudeReport> params) {
        try {
            return client.beta().messages().create(params);
        } catch (RateLimitException | OverloadedException e) {
            throw new AnalysisProviderException("The AI service is busy right now. Try again in a minute.", e);
        } catch (AnthropicServiceException e) {
            log.warn("Claude API error {}: {}", e.statusCode(), e.getMessage());
            throw new AnalysisProviderException("The AI service returned an error. Try again later.", e);
        } catch (AnthropicIoException e) {
            log.warn("Could not reach the Claude API: {}", e.getMessage());
            throw new AnalysisProviderException("The AI service could not be reached. Try again later.", e);
        }
    }

    private static ClaudeReport readReport(StructuredMessage<ClaudeReport> response) {
        try {
            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(StructuredTextBlock::text)
                    .findFirst()
                    .orElseThrow(() -> new AnalysisProviderException("The AI returned no result."));
        } catch (AnthropicException e) {
            log.warn("Claude answered with JSON that does not match the schema: {}", e.getMessage());
            throw new AnalysisProviderException("The AI returned a result that could not be read.", e);
        }
    }

    private void recordUsage(UUID analysisId, String servedModel, BetaUsage u) {
        long cacheWrite = u.cacheCreationInputTokens().orElse(0L);
        long cacheRead = u.cacheReadInputTokens().orElse(0L);
        try {
            usage.save(new ApiUsage(analysisId, NAME, servedModel, Math.toIntExact(u.inputTokens()),
                    Math.toIntExact(u.outputTokens()), Math.toIntExact(cacheRead),
                    ModelPricing.costUsd(servedModel, u.inputTokens(), u.outputTokens(), cacheWrite, cacheRead)));
        } catch (RuntimeException e) {
            // Losing a usage row is better than losing a result that has already been paid for.
            log.error("Could not record API usage for analysis {}", analysisId, e);
        }
    }

    /** Turns the model's answer into a result the domain accepts, repairing or dropping what is out of range. */
    static AnalysisResult toResult(String servedModel, ClaudeReport report) {
        List<Finding> findings = new ArrayList<>();
        for (ClaudeReport.Issue issue : report.issues() == null ? List.<ClaudeReport.Issue>of() : report.issues()) {
            if (findings.size() == MAX_FINDINGS) {
                break;
            }
            if (issue == null || isBlank(issue.title()) || issue.severity() == null || issue.category() == null) {
                continue;
            }
            findings.add(new Finding(
                    truncate(issue.title().strip(), MAX_TITLE_LENGTH),
                    orEmpty(issue.description()),
                    issue.severity(),
                    issue.category(),
                    criterionId(issue.criterionId()),
                    orEmpty(issue.suggestion()),
                    boundingBox(issue.box())));
        }
        Scores scores = new Scores(score(report.accessibilityScore()), score(report.visualHierarchyScore()),
                score(report.consistencyScore()));
        return new AnalysisResult(NAME, servedModel, orEmpty(report.summary()), scores, findings);
    }

    /** Keeps only ids in the expected format; anything else (including "NONE") becomes null. */
    static String criterionId(String raw) {
        if (raw == null) {
            return null;
        }
        String id = raw.strip().toUpperCase(Locale.ROOT);
        return CRITERION_ID.matcher(id).matches() ? id : null;
    }

    /** Clamps the box into the image; returns null for a degenerate box or one that covers the whole screen. */
    static BoundingBox boundingBox(ClaudeReport.Box box) {
        if (box == null) {
            return null;
        }
        double x = clamp(box.x());
        double y = clamp(box.y());
        double width = Math.min(box.width(), 1 - x);
        double height = Math.min(box.height(), 1 - y);
        if (!(width > 0.001 && height > 0.001)) {
            return null;
        }
        if (width >= WHOLE_SCREEN && height >= WHOLE_SCREEN) {
            return null;
        }
        return new BoundingBox((float) x, (float) y, (float) width, (float) height);
    }

    private static double clamp(double v) {
        return Double.isNaN(v) ? 0 : Math.max(0, Math.min(1, v));
    }

    private static int score(int raw) {
        return Math.max(0, Math.min(100, raw));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s.strip();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
