package com.pradeepl.triage.api;

import akka.http.javadsl.model.*;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.http.HttpResponses;
import java.io.IOException;

@HttpEndpoint("/")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class UiEndpoint {

    @Get
    public HttpResponse index() {
        return loadResource("static-resources/index.html", ContentTypes.TEXT_HTML_UTF8);
    }

    @Get("/app.js")
    public HttpResponse appJs() {
        return loadResource("static-resources/app.js", MediaTypes.APPLICATION_JAVASCRIPT.toContentType(HttpCharsets.UTF_8));
    }

    @Get("/styles.css")
    public HttpResponse stylesCss() {
        return loadResource("static-resources/styles.css", MediaTypes.TEXT_CSS.toContentType(HttpCharsets.UTF_8));
    }

    @Get("/dashboard.html")
    public HttpResponse dashboardHtml() {
        return loadResource("static-resources/dashboard.html", ContentTypes.TEXT_HTML_UTF8);
    }

    @Get("/dashboard.js")
    public HttpResponse dashboardJs() {
        return loadResource("static-resources/dashboard.js", MediaTypes.APPLICATION_JAVASCRIPT.toContentType(HttpCharsets.UTF_8));
    }

    @Get("/evaluations.html")
    public HttpResponse evaluationsHtml() {
        return loadResource("static-resources/evaluations.html", ContentTypes.TEXT_HTML_UTF8);
    }

    @Get("/evaluations.js")
    public HttpResponse evaluationsJs() {
        return loadResource("static-resources/evaluations.js", MediaTypes.APPLICATION_JAVASCRIPT.toContentType(HttpCharsets.UTF_8));
    }

    private HttpResponse loadResource(String path, ContentType contentType) {
        try (var is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                return HttpResponses.notFound();
            }
            byte[] content = is.readAllBytes();
            return HttpResponse.create()
                .withEntity(HttpEntities.create(contentType, content));
        } catch (IOException e) {
            return HttpResponses.internalServerError("Error loading resource: " + e.getMessage());
        }
    }
}

