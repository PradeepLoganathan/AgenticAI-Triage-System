package com.example.sample.application;

import com.example.sample.domain.Conversation;

import java.util.ArrayList;
import java.util.List;

public record TriageState(List<Conversation> context, Status status) {

    public enum Status { INITIATED, PREPARED, THINKING, COMPLETED }

    public static TriageState empty() {
        return new TriageState(new ArrayList<>(), Status.INITIATED);
    }

    public TriageState addConversation(Conversation c) {
        List<Conversation> list = new ArrayList<>(context);
        list.add(c);
        return new TriageState(list, status);
    }

    public TriageState withStatus(Status s) {
        return new TriageState(context, s);
    }
}

