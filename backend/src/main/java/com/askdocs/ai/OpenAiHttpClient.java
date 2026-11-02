package com.askdocs.ai;

import com.askdocs.config.AskDocsProperties;
import java.net.http.HttpClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Sends JSON to any OpenAI-compatible server, such as Ollama. */
@Component
public class OpenAiHttpClient {

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    public OpenAiHttpClient(AskDocsProperties properties, JsonMapper jsonMapper) {
        AskDocsProperties.Ai ai = properties.ai();
        var requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(ai.timeout()).build());
        requestFactory.setReadTimeout(ai.timeout());

        RestClient.Builder builder = RestClient.builder().baseUrl(ai.baseUrl()).requestFactory(requestFactory);
        if (StringUtils.hasText(ai.apiKey())) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + ai.apiKey());
        }
        this.restClient = builder.build();
        this.jsonMapper = jsonMapper;
    }

    JsonNode post(String path, Object request) {
        String response = restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                // A string has a known length, so the body is not sent in chunks, which some servers reject.
                .body(jsonMapper.writeValueAsString(request))
                .retrieve()
                .body(String.class);
        return jsonMapper.readTree(response);
    }
}
