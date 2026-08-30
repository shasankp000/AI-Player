package net.shasankp000.ServiceLLMClients;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericOpenAIClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void usesChatCompletionsForBaseUrlsThatSupportIt() throws Exception {
        AtomicInteger chatRequests = new AtomicInteger();
        AtomicInteger responsesRequests = new AtomicInteger();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> chatRequestBody = new AtomicReference<>();

        server = newServer();
        server.createContext("/v1/chat/completions", exchange -> {
            chatRequests.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            chatRequestBody.set(readBody(exchange));
            respond(exchange, 200, """
                    {"choices":[{"message":{"content":"chat reply"}}]}
                    """);
        });
        server.createContext("/v1/responses", exchange -> {
            responsesRequests.incrementAndGet();
            respond(exchange, 200, responsesBody("responses reply"));
        });
        server.start();

        GenericOpenAIClient client = new GenericOpenAIClient("secret", "chat-model", baseUrl());

        assertEquals("chat reply", client.sendPrompt("system", "hello"));
        assertEquals(1, chatRequests.get());
        assertEquals(0, responsesRequests.get());
        assertEquals("Bearer secret", authorization.get());
        assertFalse(JsonParser.parseString(chatRequestBody.get())
                .getAsJsonObject().get("stream").getAsBoolean());
    }

    @Test
    void fallsBackToResponsesAndRemembersItForTheModel() throws Exception {
        AtomicInteger chatRequests = new AtomicInteger();
        AtomicInteger responsesRequests = new AtomicInteger();
        AtomicReference<String> responsesRequestBody = new AtomicReference<>();

        server = newServer();
        server.createContext("/v1/chat/completions", exchange -> {
            chatRequests.incrementAndGet();
            respond(exchange, 400, "{\"error\":{\"message\":\"model requires Responses API\"}}");
        });
        server.createContext("/v1/responses", exchange -> {
            responsesRequests.incrementAndGet();
            responsesRequestBody.set(readBody(exchange));
            respond(exchange, 200, responsesBody("responses reply"));
        });
        server.start();

        String model = "responses-only-model";
        GenericOpenAIClient firstClient = new GenericOpenAIClient("secret", model, baseUrl());
        assertEquals("responses reply", firstClient.sendPrompt("system instructions", "hello"));

        JsonObject request = JsonParser.parseString(responsesRequestBody.get()).getAsJsonObject();
        assertEquals(model, request.get("model").getAsString());
        JsonArray input = request.getAsJsonArray("input");
        assertEquals(2, input.size());
        assertEquals("system", input.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("system instructions", input.get(0).getAsJsonObject().get("content").getAsString());
        assertEquals("user", input.get(1).getAsJsonObject().get("role").getAsString());
        assertEquals("hello", input.get(1).getAsJsonObject().get("content").getAsString());
        assertEquals(1024, request.get("max_output_tokens").getAsInt());
        assertFalse(request.get("store").getAsBoolean());
        assertFalse(request.get("stream").getAsBoolean());

        GenericOpenAIClient secondClient = new GenericOpenAIClient("secret", model, baseUrl());
        assertEquals("responses reply", secondClient.sendPrompt("system instructions", "again"));

        assertEquals(1, chatRequests.get());
        assertEquals(2, responsesRequests.get());
    }

    @Test
    void doesNotCacheFallbackForPromptSpecificBadRequests() throws Exception {
        AtomicInteger chatRequests = new AtomicInteger();
        AtomicInteger responsesRequests = new AtomicInteger();

        server = newServer();
        server.createContext("/v1/chat/completions", exchange -> {
            chatRequests.incrementAndGet();
            respond(exchange, 400, "{\"error\":{\"code\":\"context_length_exceeded\"}}");
        });
        server.createContext("/v1/responses", exchange -> {
            responsesRequests.incrementAndGet();
            respond(exchange, 200, responsesBody("responses reply"));
        });
        server.start();

        String model = "prompt-error-model";
        assertEquals("responses reply",
                new GenericOpenAIClient("secret", model, baseUrl()).sendPrompt("system", "first"));
        assertEquals("responses reply",
                new GenericOpenAIClient("secret", model, baseUrl()).sendPrompt("system", "second"));

        assertEquals(2, chatRequests.get());
        assertEquals(2, responsesRequests.get());
    }

    @Test
    void fullResponsesUrlSelectsResponsesApiImmediately() throws Exception {
        AtomicInteger chatRequests = new AtomicInteger();
        AtomicInteger responsesRequests = new AtomicInteger();
        AtomicInteger modelRequests = new AtomicInteger();

        server = newServer();
        server.createContext("/v1/chat/completions", exchange -> {
            chatRequests.incrementAndGet();
            respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"chat reply\"}}]}");
        });
        server.createContext("/v1/responses", exchange -> {
            responsesRequests.incrementAndGet();
            respond(exchange, 200, responsesBody("first ", "second"));
        });
        server.createContext("/v1/models", exchange -> {
            modelRequests.incrementAndGet();
            respond(exchange, 200, "{\"data\":[{\"id\":\"explicit-responses-model\"}]}");
        });
        server.start();

        String responsesUrl = baseUrl() + "/responses";
        GenericOpenAIClient client = new GenericOpenAIClient(
                "secret", "explicit-responses-model", responsesUrl);

        assertTrue(client.isReachable());
        assertEquals(List.of("explicit-responses-model"),
                new GenericOpenAIModelFetcher(responsesUrl).fetchModels("secret"));
        assertEquals("first second", client.sendPrompt("system", "hello"));
        assertEquals(0, chatRequests.get());
        assertEquals(1, responsesRequests.get());
        assertEquals(2, modelRequests.get());
    }

    @Test
    void parsesResponsesEventStreamWhenGatewayStreamsAnyway() throws Exception {
        server = newServer();
        server.createContext("/v1/responses", exchange -> respond(
                exchange,
                200,
                """
                        event: response.output_text.delta
                        data: {"type":"response.output_text.delta","delta":"Hello"}

                        event: response.output_text.delta
                        data: {"type":"response.output_text.delta","delta":" world"}

                        data: [DONE]

                        """,
                "text/event-stream"));
        server.start();

        GenericOpenAIClient client = new GenericOpenAIClient(
                "secret", "streaming-responses-model", baseUrl() + "/responses");

        assertEquals("Hello world", client.sendPrompt("system", "hello"));
    }

    @Test
    void extractsTextFromCompletedEventAfterLifecycleEvents() throws Exception {
        JsonObject completedEvent = new JsonObject();
        completedEvent.addProperty("type", "response.completed");
        completedEvent.add("response", JsonParser.parseString(responsesBody("completed reply")));

        server = newServer();
        server.createContext("/v1/responses", exchange -> respond(
                exchange,
                200,
                """
                        data: {"type":"response.created","response":{"output":[]}}

                        data: {"type":"response.in_progress","response":{"output":[]}}

                        data: {"type":"response.output_item.added","item":{"type":"message","content":[]}}

                        data: %s

                        data: [DONE]

                        """.formatted(completedEvent),
                "text/event-stream"));
        server.start();

        GenericOpenAIClient client = new GenericOpenAIClient(
                "secret", "completed-event-model", baseUrl() + "/responses");

        assertEquals("completed reply", client.sendPrompt("system", "hello"));
    }

    @Test
    void remembersChatFallbackForExplicitResponsesUrls() throws Exception {
        AtomicInteger chatRequests = new AtomicInteger();
        AtomicInteger responsesRequests = new AtomicInteger();

        server = newServer();
        server.createContext("/v1/responses", exchange -> {
            responsesRequests.incrementAndGet();
            respond(exchange, 400, "{\"error\":{\"message\":\"model only supports Chat Completions\"}}");
        });
        server.createContext("/v1/chat/completions", exchange -> {
            chatRequests.incrementAndGet();
            respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"chat reply\"}}]}");
        });
        server.start();

        String responsesUrl = baseUrl() + "/responses";
        String model = "chat-only-model";
        assertEquals("chat reply",
                new GenericOpenAIClient("secret", model, responsesUrl).sendPrompt("system", "first"));
        assertEquals("chat reply",
                new GenericOpenAIClient("secret", model, responsesUrl).sendPrompt("system", "second"));

        assertEquals(1, responsesRequests.get());
        assertEquals(2, chatRequests.get());
    }

    @Test
    void doesNotRetryAuthenticationErrorsAgainstAnotherEndpoint() throws Exception {
        AtomicInteger responsesRequests = new AtomicInteger();

        server = newServer();
        server.createContext("/v1/chat/completions", exchange ->
                respond(exchange, 401, "{\"error\":{\"message\":\"invalid key\"}}"));
        server.createContext("/v1/responses", exchange -> {
            responsesRequests.incrementAndGet();
            respond(exchange, 200, responsesBody("should not be called"));
        });
        server.start();

        GenericOpenAIClient client = new GenericOpenAIClient("bad-key", "auth-model", baseUrl());
        String result = client.sendPrompt("system", "hello");

        assertTrue(result.contains("HTTP 401"));
        assertEquals(0, responsesRequests.get());
    }

    private HttpServer newServer() throws IOException {
        return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    private static String responsesBody(String... textParts) {
        JsonArray content = new JsonArray();
        for (String text : textParts) {
            JsonObject outputText = new JsonObject();
            outputText.addProperty("type", "output_text");
            outputText.addProperty("text", text);
            content.add(outputText);
        }

        JsonObject reasoning = new JsonObject();
        reasoning.addProperty("type", "reasoning");
        reasoning.add("content", new JsonArray());

        JsonObject message = new JsonObject();
        message.addProperty("type", "message");
        message.add("content", content);

        JsonArray output = new JsonArray();
        output.add(reasoning);
        output.add(message);

        JsonObject response = new JsonObject();
        response.add("output", output);
        return response.toString();
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        respond(exchange, statusCode, body, "application/json");
    }

    private static void respond(HttpExchange exchange, int statusCode, String body, String contentType)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (exchange; var responseBody = exchange.getResponseBody()) {
            responseBody.write(bytes);
        }
    }
}
