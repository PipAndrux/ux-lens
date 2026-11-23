package dev.uxlens.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaUsage;
import com.anthropic.models.beta.messages.StructuredContentBlock;
import com.anthropic.models.beta.messages.StructuredMessage;
import com.anthropic.models.beta.messages.StructuredMessageCreateParams;
import com.anthropic.models.beta.messages.StructuredTextBlock;
import com.anthropic.models.messages.Model;
import dev.uxlens.analysis.BoundingBox;
import dev.uxlens.analysis.IssueCategory;
import dev.uxlens.analysis.Severity;
import dev.uxlens.usage.ApiUsage;
import dev.uxlens.usage.ApiUsageRepository;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** The SDK client is mocked: these tests never call the real API and cost nothing. */
class ClaudeAnalysisProviderTest {

    private static final String MODEL = "claude-opus-5-5";
    private static final AnalysisRequest REQUEST =
            new AnalysisRequest(UUID.randomUUID(), new byte[] {1, 2, 3}, 1568, 980);

    private AnthropicClient client;
    private ApiUsageRepository usage;
    private ClaudeAnalysisProvider provider;

    @BeforeEach
    void setUp() {
        client = mock(AnthropicClient.class, RETURNS_DEEP_STUBS);
        usage = mock(ApiUsageRepository.class);
        provider = new ClaudeAnalysisProvider(client, MODEL, usage);
    }

    @Test
    void buildsAVisionRequestWithStructuredOutputAndTheRefusalFallback() {
        StructuredMessageCreateParams<ClaudeReport> params = provider.buildParams(REQUEST);

        assertThat(params.outputType()).isEqualTo(ClaudeReport.class);
        var raw = params.rawParams();
        assertThat(raw.model().asString()).isEqualTo(MODEL);
        assertThat(raw.maxTokens()).isEqualTo(ClaudeAnalysisProvider.MAX_TOKENS);
        assertThat(raw.betas().orElseThrow().toString()).contains(ClaudeAnalysisProvider.REFUSAL_FALLBACK_BETA);
        assertThat(raw.fallbacks()).isPresent();
        assertThat(raw.thinking()).isEmpty();
        BetaOutputConfig output = raw.outputConfig().orElseThrow();
        assertThat(output.effort()).contains(BetaOutputConfig.Effort.MEDIUM);
        assertThat(output.format().orElseThrow()._schema().toString()).contains("criterionId", "box", "severity");

        List<BetaContentBlockParam> content = raw.messages().getFirst().content().orElseThrow().asContentBlockParams();
        assertThat(content.get(0).isImage()).isTrue();
        assertThat(content.get(1).asText().text()).contains("1568 x 980");
    }

    @Test
    void mapsTheReportAndRecordsUsageWithCost() {
        ClaudeReport report = new ClaudeReport("Busy login screen.", 61, 70, 82, List.of(
                new ClaudeReport.Issue("Low contrast", "Grey on white.", Severity.CRITICAL,
                        IssueCategory.ACCESSIBILITY, "wcag-1.4.3", "Darken the text.",
                        new ClaudeReport.Box(0.1, 0.2, 0.3, 0.1))));
        stubResponse(BetaStopReason.END_TURN, report);

        AnalysisResult result = provider.analyze(REQUEST);

        assertThat(result.provider()).isEqualTo("claude");
        assertThat(result.model()).isEqualTo(MODEL);
        assertThat(result.summary()).isEqualTo("Busy login screen.");
        assertThat(result.scores()).isEqualTo(new AnalysisResult.Scores(61, 70, 82));
        assertThat(result.findings()).singleElement().satisfies(f -> {
            assertThat(f.criterionId()).isEqualTo("WCAG-1.4.3");
            assertThat(f.boundingBox()).isEqualTo(new BoundingBox(0.1f, 0.2f, 0.3f, 0.1f));
        });

        ApiUsage row = savedUsage();
        assertThat(row.getAnalysisId()).isEqualTo(REQUEST.analysisId());
        assertThat(row.getInputTokens()).isEqualTo(2_000);
        assertThat(row.getOutputTokens()).isEqualTo(1_000);
        // 2000 * $4/M + 1000 * $20/M
        assertThat(row.getCostUsd()).isEqualByComparingTo(new BigDecimal("0.028"));
    }

    @Test
    void aRefusalFailsTheAnalysisButIsStillBilled() {
        stubResponse(BetaStopReason.REFUSAL, null);

        assertThatThrownBy(() -> provider.analyze(REQUEST))
                .isInstanceOf(AnalysisProviderException.class)
                .hasMessage("The AI declined to analyze this image.");
        savedUsage();
    }

    @Test
    void aTruncatedAnswerFailsTheAnalysis() {
        stubResponse(BetaStopReason.MAX_TOKENS, null);

        assertThatThrownBy(() -> provider.analyze(REQUEST))
                .isInstanceOf(AnalysisProviderException.class)
                .hasMessageContaining("ran out of space");
        savedUsage();
    }

    @Test
    void anAnswerWithoutTextFails() {
        stubResponse(BetaStopReason.END_TURN, null);

        assertThatThrownBy(() -> provider.analyze(REQUEST))
                .isInstanceOf(AnalysisProviderException.class)
                .hasMessage("The AI returned no result.");
    }

    @Test
    void translatesSdkErrorsIntoMessagesForUsers() {
        when(client.beta().messages().create(any(StructuredMessageCreateParams.class)))
                .thenThrow(mock(RateLimitException.class))
                .thenThrow(new AnthropicIoException("connection reset"));

        assertThatThrownBy(() -> provider.analyze(REQUEST)).hasMessageContaining("busy");
        assertThatThrownBy(() -> provider.analyze(REQUEST)).hasMessageContaining("could not be reached");
    }

    @SuppressWarnings("unchecked")
    private void stubResponse(BetaStopReason stopReason, ClaudeReport report) {
        StructuredMessage<ClaudeReport> message = mock(StructuredMessage.class);
        Model model = mock(Model.class);
        when(model.asString()).thenReturn(MODEL);
        when(message.model()).thenReturn(model);
        when(message.stopReason()).thenReturn(Optional.of(stopReason));
        when(message.stopDetails()).thenReturn(Optional.empty());

        BetaUsage betaUsage = mock(BetaUsage.class);
        when(betaUsage.inputTokens()).thenReturn(2_000L);
        when(betaUsage.outputTokens()).thenReturn(1_000L);
        when(betaUsage.cacheCreationInputTokens()).thenReturn(Optional.empty());
        when(betaUsage.cacheReadInputTokens()).thenReturn(Optional.empty());
        when(message.usage()).thenReturn(betaUsage);

        if (report == null) {
            when(message.content()).thenReturn(Collections.emptyList());
        } else {
            StructuredTextBlock<ClaudeReport> text = mock(StructuredTextBlock.class);
            when(text.text()).thenReturn(report);
            StructuredContentBlock<ClaudeReport> block = mock(StructuredContentBlock.class);
            when(block.text()).thenReturn(Optional.of(text));
            when(message.content()).thenReturn(List.of(block));
        }
        when(client.beta().messages().create(any(StructuredMessageCreateParams.class))).thenReturn(message);
    }

    private ApiUsage savedUsage() {
        ArgumentCaptor<ApiUsage> captor = ArgumentCaptor.forClass(ApiUsage.class);
        verify(usage).save(captor.capture());
        return captor.getValue();
    }
}
