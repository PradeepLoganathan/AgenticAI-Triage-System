package com.example.sample.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.http.HttpResponses;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/mcp")
public class McpMockEndpoint {

    @Post
    public HttpResponse call(String body) {
        // Minimal echo JSON-RPC response: extract id if present and wrap a fake result
        String id = extractJsonId(body);
        String result = "{\"output\":\"mocked tool output\"}";
        String response = "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":" + result + "}";
        return HttpResponses.ok(response);
    }

    private static String extractJsonId(String body) {
        // naive parse for \"id\":\"...\" or \"id\":123
        try {
            int idx = body.indexOf("\"id\"");
            if (idx >= 0) {
                int colon = body.indexOf(':', idx);
                if (colon > 0) {
                    int start = colon + 1;
                    // skip whitespace
                    while (start < body.length() && Character.isWhitespace(body.charAt(start))) start++;
                    if (start < body.length() && body.charAt(start) == '"') {
                        int end = body.indexOf('"', start + 1);
                        if (end > start) return '"' + body.substring(start + 1, end) + '"';
                    } else {
                        // number or other literal up to comma/brace
                        int end = start;
                        while (end < body.length() && ",}\n \t".indexOf(body.charAt(end)) == -1) end++;
                        return body.substring(start, end);
                    }
                }
            }
        } catch (Exception ignored) {}
        return "null";
    }
}

