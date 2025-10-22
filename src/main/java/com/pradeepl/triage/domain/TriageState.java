package com.pradeepl.triage.domain;

import java.util.ArrayList;
import java.util.List;

public record TriageState(
        String workflowId,
        List<Conversation> context,
        Status status,
        String incident,
        String classificationJson,
        String evidenceLogs,
        String evidenceMetrics,
        String triageText,
        String remediationText,
        String summaryText,
        String knowledgeBaseResult
) {

    public enum Status { INITIATED, PREPARED, CLASSIFIED, EVIDENCE_COLLECTED, TRIAGED, KNOWLEDGE_BASE_SEARCHED, REMEDIATION_PROPOSED, SUMMARY_READY, COMPLETED }

    public static Builder builder() {
        return new Builder();
    }

    public static TriageState empty() {
        return new Builder().status(Status.INITIATED).build();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public TriageState addConversation(Conversation c) {
        List<Conversation> list = new ArrayList<>(context);
        list.add(c);
        return toBuilder().context(list).build();
    }

    public TriageState withStatus(Status s) {
        return toBuilder().status(s).build();
    }

    public TriageState withIncident(String i) {
        return toBuilder().incident(i).build();
    }

    public TriageState withClassificationJson(String json) {
        return toBuilder().classificationJson(json).build();
    }

    public TriageState withEvidence(String logs, String metrics) {
        return toBuilder().evidenceLogs(logs).evidenceMetrics(metrics).build();
    }

    public TriageState withTriageText(String txt) {
        return toBuilder().triageText(txt).build();
    }

    public TriageState withRemediationText(String txt) {
        return toBuilder().remediationText(txt).build();
    }

    public TriageState withSummaryText(String txt) {
        return toBuilder().summaryText(txt).build();
    }

    public TriageState withKnowledgeBaseResult(String result) {
        return toBuilder().knowledgeBaseResult(result).build();
    }

    public static class Builder {
        private String workflowId;
        private List<Conversation> context = new ArrayList<>();
        private Status status;
        private String incident;
        private String classificationJson;
        private String evidenceLogs;
        private String evidenceMetrics;
        private String triageText;
        private String remediationText;
        private String summaryText;
        private String knowledgeBaseResult;

        public Builder() {}

        public Builder(TriageState state) {
            this.workflowId = state.workflowId;
            this.context = state.context;
            this.status = state.status;
            this.incident = state.incident;
            this.classificationJson = state.classificationJson;
            this.evidenceLogs = state.evidenceLogs;
            this.evidenceMetrics = state.evidenceMetrics;
            this.triageText = state.triageText;
            this.remediationText = state.remediationText;
            this.summaryText = state.summaryText;
            this.knowledgeBaseResult = state.knowledgeBaseResult;
        }

        public Builder workflowId(String workflowId) {
            this.workflowId = workflowId;
            return this;
        }

        public Builder context(List<Conversation> context) {
            this.context = context;
            return this;
        }

        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        public Builder incident(String incident) {
            this.incident = incident;
            return this;
        }

        public Builder classificationJson(String classificationJson) {
            this.classificationJson = classificationJson;
            return this;
        }

        public Builder evidenceLogs(String evidenceLogs) {
            this.evidenceLogs = evidenceLogs;
            return this;
        }

        public Builder evidenceMetrics(String evidenceMetrics) {
            this.evidenceMetrics = evidenceMetrics;
            return this;
        }

        public Builder triageText(String triageText) {
            this.triageText = triageText;
            return this;
        }

        public Builder remediationText(String remediationText) {
            this.remediationText = remediationText;
            return this;
        }

        public Builder summaryText(String summaryText) {
            this.summaryText = summaryText;
            return this;
        }

        public Builder knowledgeBaseResult(String knowledgeBaseResult) {
            this.knowledgeBaseResult = knowledgeBaseResult;
            return this;
        }

        public TriageState build() {
            return new TriageState(
                workflowId,
                context,
                status,
                incident,
                classificationJson,
                evidenceLogs,
                evidenceMetrics,
                triageText,
                remediationText,
                summaryText,
                knowledgeBaseResult
            );
        }
    }
}
