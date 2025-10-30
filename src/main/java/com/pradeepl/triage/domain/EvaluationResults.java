package com.pradeepl.triage.domain;

/**
 * EvaluationResults aggregates all evaluation results for a triage workflow.
 *
 * Tracks toxicity and hallucination checks across different workflow stages.
 */
public record EvaluationResults(
    ToxicityResult summaryToxicity,
    ToxicityResult remediationToxicity,
    HallucinationResult evidenceHallucination,
    HallucinationResult triageHallucination,
    HallucinationResult summaryHallucination
) {

    /**
     * Create empty evaluation results.
     */
    public static EvaluationResults empty() {
        return new EvaluationResults(null, null, null, null, null);
    }

    /**
     * Check if all evaluations passed (ignoring null/unevaluated items).
     */
    public boolean allPassed() {
        boolean summaryToxPass = summaryToxicity == null || summaryToxicity.passed();
        boolean remediationToxPass = remediationToxicity == null || remediationToxicity.passed();
        boolean evidenceHallPass = evidenceHallucination == null || evidenceHallucination.passed();
        boolean triageHallPass = triageHallucination == null || triageHallucination.passed();
        boolean summaryHallPass = summaryHallucination == null || summaryHallucination.passed();

        return summaryToxPass && remediationToxPass && evidenceHallPass &&
               triageHallPass && summaryHallPass;
    }

    /**
     * Count number of failed evaluations.
     */
    public int failureCount() {
        int count = 0;
        if (summaryToxicity != null && !summaryToxicity.passed()) count++;
        if (remediationToxicity != null && !remediationToxicity.passed()) count++;
        if (evidenceHallucination != null && !evidenceHallucination.passed()) count++;
        if (triageHallucination != null && !triageHallucination.passed()) count++;
        if (summaryHallucination != null && !summaryHallucination.passed()) count++;
        return count;
    }

    /**
     * Result from toxicity evaluation.
     */
    public record ToxicityResult(
        String explanation,
        boolean passed
    ) {}

    /**
     * Result from hallucination evaluation.
     */
    public record HallucinationResult(
        String explanation,
        boolean passed
    ) {}

    // Builder-style methods for updating individual results

    public EvaluationResults withSummaryToxicity(ToxicityResult result) {
        return new EvaluationResults(
            result,
            remediationToxicity,
            evidenceHallucination,
            triageHallucination,
            summaryHallucination
        );
    }

    public EvaluationResults withRemediationToxicity(ToxicityResult result) {
        return new EvaluationResults(
            summaryToxicity,
            result,
            evidenceHallucination,
            triageHallucination,
            summaryHallucination
        );
    }

    public EvaluationResults withEvidenceHallucination(HallucinationResult result) {
        return new EvaluationResults(
            summaryToxicity,
            remediationToxicity,
            result,
            triageHallucination,
            summaryHallucination
        );
    }

    public EvaluationResults withTriageHallucination(HallucinationResult result) {
        return new EvaluationResults(
            summaryToxicity,
            remediationToxicity,
            evidenceHallucination,
            result,
            summaryHallucination
        );
    }

    public EvaluationResults withSummaryHallucination(HallucinationResult result) {
        return new EvaluationResults(
            summaryToxicity,
            remediationToxicity,
            evidenceHallucination,
            triageHallucination,
            result
        );
    }
}
