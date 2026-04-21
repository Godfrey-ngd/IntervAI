package interview.guide.modules.voiceinterview.service;

import interview.guide.modules.voiceinterview.config.VoiceInterviewProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DidClipClient {

    private final VoiceInterviewProperties voiceInterviewProperties;

    public record CreateClipResponse(String id, String status, String object) {
    }

    public record ClipStatusResponse(String id, String status, String result_url) {
    }

    public CreateClipResponse createClipWithText(String presenterId, String text) {
        VoiceInterviewProperties.DidConfig did = voiceInterviewProperties.getDid();
        if (did == null || !did.isEnabled()) {
            return null;
        }
        String key = did.getApiKey();
        if (key == null || key.isBlank()) {
            return null;
        }
        String pid = presenterId != null && !presenterId.isBlank() ? presenterId : did.getPresenterId();
        if (pid == null || pid.isBlank()) {
            return null;
        }
        String input = text == null ? "" : text.trim();
        if (input.isEmpty()) {
            return null;
        }

        org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory =
            new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(60).toMillis());

        RestClient client = RestClient.builder()
            .baseUrl(did.getBaseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, buildAuthorizationHeaderValue(key))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .requestFactory(requestFactory)
            .build();

        Map<String, Object> payload = Map.of(
            "presenter_id", pid,
            "script", Map.of(
                "type", "text",
                "input", input
            ),
            "config", Map.of(
                "result_format", "mp4"
            )
        );

        log.info("[D-ID] Creating clip: presenterId={}, textLen={}", pid, input.length());
        return client.post()
            .uri("/clips")
            .body(payload)
            .retrieve()
            .body(CreateClipResponse.class);
    }

    public ClipStatusResponse getClipStatus(String clipId) {
        VoiceInterviewProperties.DidConfig did = voiceInterviewProperties.getDid();
        if (did == null || !did.isEnabled()) {
            return null;
        }
        String key = did.getApiKey();
        if (key == null || key.isBlank()) {
            return null;
        }
        if (clipId == null || clipId.isBlank()) {
            return null;
        }

        org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory =
            new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());

        RestClient client = RestClient.builder()
            .baseUrl(did.getBaseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, buildAuthorizationHeaderValue(key))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .requestFactory(requestFactory)
            .build();

        return client.get()
            .uri("/clips/{id}", clipId)
            .retrieve()
            .body(ClipStatusResponse.class);
    }

    private static String buildAuthorizationHeaderValue(String apiKeyUserColonPass) {
        String encoded = Base64.getEncoder()
            .encodeToString(apiKeyUserColonPass.getBytes(StandardCharsets.US_ASCII));
        return "Basic " + encoded;
    }
}
