package com.example.sample.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import com.example.sample.application.TriageWorkflow;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/triage/{triageId}")
public class TriageEndpoint {

    private final ComponentClient client;

    public TriageEndpoint(ComponentClient client) {
        this.client = client;
    }

    public record StartRequest(String incident) {}

    @Post
    public HttpResponse start(String triageId, StartRequest req) {
        var res = client.forWorkflow(triageId)
            .method(TriageWorkflow::start)
            .invoke(new TriageWorkflow.StartTriage(req.incident()));
        return HttpResponses.ok(res);
    }

    @Get
    public HttpResponse conversations(String triageId) {
        var res = client.forWorkflow(triageId)
            .method(TriageWorkflow::getConversations)
            .invoke();
        return HttpResponses.ok(res);
    }

    @Get("/state")
    public HttpResponse state(String triageId) {
        var res = client.forWorkflow(triageId)
                .method(TriageWorkflow::getState)
                .invoke();
        return HttpResponses.ok(res);
    }
}
