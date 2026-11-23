package dev.uxlens.ai;

import dev.uxlens.analysis.BoundingBox;
import dev.uxlens.analysis.IssueCategory;
import dev.uxlens.analysis.Severity;
import dev.uxlens.ai.AnalysisResult.Finding;
import dev.uxlens.ai.AnalysisResult.Scores;
import java.util.List;

/**
 * Free default provider: returns a fixed, realistic audit without calling any AI. Used for local development, tests
 * and the public demo, so the app costs nothing to run (ADR 0002). The summary says plainly that it is sample data.
 */
public class MockAnalysisProvider implements AnalysisProvider {

    public static final String NAME = "mock";
    static final String MODEL = "fixtures-v1";

    private static final List<Finding> FINDINGS = List.of(
            new Finding(
                    "Low contrast on the primary button label",
                    "The white label on the light-blue button has a contrast ratio of about 2.6:1, below the 4.5:1 "
                            + "that normal-size text needs.",
                    Severity.CRITICAL, IssueCategory.ACCESSIBILITY, "WCAG-1.4.3",
                    "Darken the button background (for example to #1D4ED8) or use a darker label color.",
                    new BoundingBox(0.62f, 0.78f, 0.26f, 0.08f)),
            new Finding(
                    "Form fields rely on placeholder text as labels",
                    "The email and password fields have no visible label; the placeholder disappears as soon as "
                            + "the user starts typing.",
                    Severity.MAJOR, IssueCategory.ACCESSIBILITY, "WCAG-3.3.2",
                    "Add a persistent visible label above each field and keep the placeholder for examples only.",
                    new BoundingBox(0.12f, 0.40f, 0.76f, 0.22f)),
            new Finding(
                    "Two competing primary actions",
                    "\"Sign up\" and \"Start free trial\" share the same size, color and weight, so neither reads as "
                            + "the main action.",
                    Severity.MAJOR, IssueCategory.VISUAL_HIERARCHY, "NIELSEN-8",
                    "Keep one filled primary button and turn the other into a secondary (outline or text) button.",
                    new BoundingBox(0.12f, 0.78f, 0.76f, 0.08f)),
            new Finding(
                    "Inconsistent corner radius",
                    "Cards use 16 px rounded corners while inputs and buttons use 4 px, which makes the screen look "
                            + "assembled from different kits.",
                    Severity.MINOR, IssueCategory.CONSISTENCY, "NIELSEN-4",
                    "Pick one radius scale (for example 8 px for controls, 16 px for containers) and apply it "
                            + "everywhere.",
                    new BoundingBox(0.08f, 0.18f, 0.84f, 0.70f)),
            new Finding(
                    "No visible way to recover a forgotten password",
                    "The login form offers no \"Forgot password?\" link, so users who cannot remember their "
                            + "password are stuck.",
                    Severity.MINOR, IssueCategory.USABILITY, "NIELSEN-9",
                    "Add a \"Forgot password?\" link right below the password field.",
                    null));

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public AnalysisResult analyze(AnalysisRequest request) {
        return new AnalysisResult(
                NAME,
                MODEL,
                "Sample result from the mock provider: no AI was used, so the issues below are examples rather "
                        + "than an audit of this screenshot.",
                new Scores(58, 66, 74),
                FINDINGS);
    }
}
