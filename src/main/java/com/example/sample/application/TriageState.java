package com.example.sample.application;

import com.example.sample.domain.Conversation;

import java.util.ArrayList;
import java.util.List;

public record TriageState(
        List<Conversation> context,
        Status status,
        String incident,
        String classificationJson,
        String evidenceLogs,
        String evidenceMetrics,
        String triageText,
        String remediationText,
        String summaryText
) {

    public enum Status { INITIATED, PREPARED, CLASSIFIED, EVIDENCE_COLLECTED, TRIAGED, REMEDIATION_PROPOSED, SUMMARY_READY, COMPLETED }

    public static TriageState empty() {
        return new TriageState(new ArrayList<>(), Status.INITIATED, null, null, null, null, null, null, null);
    }

    public TriageState addConversation(Conversation c) {
        List<Conversation> list = new ArrayList<>(context);
        list.add(c);
        return new TriageState(list, status, incident, classificationJson, evidenceLogs, evidenceMetrics, triageText, remediationText, summaryText);
    }

    public TriageState withStatus(Status s) {
        return new TriageState(context, s, incident, classificationJson, evidenceLogs, evidenceMetrics, triageText, remediationText, summaryText);
    }

    public TriageState withIncident(String i) {
        return new TriageState(context, status, i, classificationJson, evidenceLogs, evidenceMetrics, triageText, remediationText, summaryText);
    }

    public TriageState withClassificationJson(String json) {
        return new TriageState(context, status, incident, json, evidenceLogs, evidenceMetrics, triageText, remediationText, summaryText);
    }

    public TriageState withEvidence(String logs, String metrics) {
        return new TriageState(context, status, incident, classificationJson, logs, metrics, triageText, remediationText, summaryText);
    }

    public TriageState withTriageText(String txt) {
        return new TriageState(context, status, incident, classificationJson, evidenceLogs, evidenceMetrics, txt, remediationText, summaryText);
    }

    public TriageState withRemediationText(String txt) {
        return new TriageState(context, status, incident, classificationJson, evidenceLogs, evidenceMetrics, triageText, txt, summaryText);
    }

    public TriageState withSummaryText(String txt) {
        return new TriageState(context, status, incident, classificationJson, evidenceLogs, evidenceMetrics, triageText, remediationText, txt);
    }
}
