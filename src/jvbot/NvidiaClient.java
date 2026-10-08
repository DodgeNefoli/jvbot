package jvbot;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Thin wrapper around the free NVIDIA API (build.nvidia.com).
 * The endpoint is OpenAI-compatible; we call it in streaming (SSE) mode
 * so the reply prints word-by-word like Claude Code.
 */
public class NvidiaClient {

    private static final String ENDPOINT = "https://integrate.api.nvidia.com/v1/chat/completions";
    private static final String MODELS_ENDPOINT = "https://integrate.api.nvidia.com/v1/models";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final Settings settings;

    public NvidiaClient(Settings settings) {
        this.settings = settings;
    }

    /**
     * Sends the whole conversation and returns the complete reply text.
     * Each piece of the reply is passed to onPiece as it arrives, so the
     * caller (console or GUI) can show it streaming in.
     */
    public String chat(List<Message> messages, Consumer<String> onPiece) throws ApiException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(Duration.ofMinutes(2))
                .header("Authorization", "Bearer " + settings.getKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(buildBody(messages)))
                .build();

        HttpResponse<Stream<String>> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofLines());
        } catch (IOException e) {
            throw new ApiException("Network problem: " + e.getMessage() + " - check your internet connection.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("Request was interrupted.");
        }

        if (response.statusCode() != 200) {
            String errorBody = response.body().collect(Collectors.joining("\n"));
            throw new ApiException(friendlyError(response.statusCode(), errorBody));
        }

        // SSE stream: lines look like "data: {json}", finished by "data: [DONE]".
        StringBuilder full = new StringBuilder();
        Iterator<String> lines = response.body().iterator();
        while (lines.hasNext()) {
            String line = lines.next();
            if (!line.startsWith("data:")) continue;
            String data = line.substring(5).trim();
            if (data.isEmpty() || data.equals("[DONE]")) continue;
            try {
                Object chunk = Json.parse(data);
                Object piece = Json.get(chunk, "choices", 0, "delta", "content");
                if (piece instanceof String) {
                    onPiece.accept((String) piece);
                    full.append(piece);
                }
            } catch (RuntimeException malformedChunk) {
                // skip a bad chunk instead of killing the whole reply
            }
        }
        return full.toString();
    }

    /** Fetches every model id available on the API, sorted alphabetically. */
    public List<String> listModels() throws ApiException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MODELS_ENDPOINT))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + settings.getKey())
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ApiException("Network problem: " + e.getMessage() + " - check your internet connection.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("Request was interrupted.");
        }

        if (response.statusCode() != 200) {
            throw new ApiException(friendlyError(response.statusCode(), response.body()));
        }

        List<String> models = new ArrayList<>();
        Object root = Json.parse(response.body());
        Object data = Json.get(root, "data");
        if (data instanceof List) {
            for (Object item : (List<?>) data) {
                Object id = Json.get(item, "id");
                if (id instanceof String) models.add((String) id);
            }
        }
        models.sort(String.CASE_INSENSITIVE_ORDER);
        return models;
    }

    private String buildBody(List<Message> messages) {
        List<Object> msgs = new ArrayList<>();
        for (Message m : messages) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("role", m.role);
            mm.put("content", m.content);
            msgs.add(mm);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", settings.getModel());
        body.put("messages", msgs);
        body.put("temperature", 0.7);
        body.put("max_tokens", 1024);
        body.put("stream", true);
        return Json.write(body);
    }

    private String friendlyError(int status, String body) {
        String detail = "";
        try {
            Object parsed = Json.parse(body.trim());
            Object msg = Json.get(parsed, "error", "message");
            if (msg == null) msg = Json.get(parsed, "detail");
            if (msg == null) msg = Json.get(parsed, "message");
            if (msg instanceof String) detail = " (" + msg + ")";
        } catch (RuntimeException notJson) {
            // error body was not JSON - just report the status code
        }
        switch (status) {
            case 401:
            case 403:
                return "Invalid API key - set a valid key with /key <your-key>" + detail;
            case 404:
                return "Model '" + settings.getModel() + "' not found - pick another with /model <name>" + detail;
            case 429:
                return "Rate limit reached - wait a moment and try again" + detail;
            default:
                return "API error (HTTP " + status + ")" + detail;
        }
    }

    /** Thrown for any API/network failure, always with a user-friendly message. */
    public static class ApiException extends Exception {
        public ApiException(String message) {
            super(message);
        }
    }
}
