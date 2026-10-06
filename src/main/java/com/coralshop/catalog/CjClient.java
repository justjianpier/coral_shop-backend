package com.coralshop.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CjClient {

    private static final String BASE_URL = "https://developers.cjdropshipping.com/api2.0/v1";
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json;
    private final String apiKey;
    private String accessToken;
    private Instant expiresAt = Instant.EPOCH;
    private long lastRequestNanos;

    public CjClient(ObjectMapper json, @Value("${CJ_API_KEY:}") String apiKey) {
        this.json = json;
        this.apiKey = apiKey;
    }

    public JsonNode search(String keyword, int page) {
        String url = BASE_URL + "/product/listV2?page=" + page + "&size=20&keyWord=" +
                URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        return get(url);
    }

    public JsonNode product(String pid) {
        return get(BASE_URL + "/product/query?pid=" + URLEncoder.encode(pid, StandardCharsets.UTF_8));
    }

    private JsonNode get(String url) {
        String token = token();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15)).header("CJ-Access-Token", token).GET().build();
        JsonNode response = send(request);
        if (!response.path("result").asBoolean(false)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "CJ could not complete the request; please try again later");
        }
        return response.path("data");
    }

    private synchronized String token() {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "CJ is not configured on the backend (CJ_API_KEY)");
        }
        if (accessToken != null && Instant.now().isBefore(expiresAt.minus(Duration.ofMinutes(5)))) {
            return accessToken;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/authentication/getAccessToken"))
                    .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                            java.util.Map.of("apiKey", apiKey)))).build();
            JsonNode response = send(request);
            JsonNode data = response.path("data");
            if (!response.path("result").asBoolean(false) || data.path("accessToken").asText().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "CJ authentication failed; check the backend API key");
            }
            accessToken = data.path("accessToken").asText();
            expiresAt = OffsetDateTime.parse(data.path("accessTokenExpiryDate").asText()).toInstant();
            return accessToken;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "CJ authentication failed", exception);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "CJ returned an invalid token expiry", exception);
        }
    }

    private synchronized JsonNode send(HttpRequest request) {
        try {
            // CJ limita las cuentas gratuitas a una petición por segundo.
            long remaining = Duration.ofMillis(1100).toNanos() - (System.nanoTime() - lastRequestNanos);
            if (lastRequestNanos != 0 && remaining > 0) {
                java.util.concurrent.TimeUnit.NANOSECONDS.sleep(remaining);
            }
            lastRequestNanos = System.nanoTime();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "CJ rate limit reached; wait and retry");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "CJ is unavailable (HTTP " + response.statusCode() + ")");
            }
            return json.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "CJ request interrupted", exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "CJ could not be reached", exception);
        }
    }
}
