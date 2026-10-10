package code.filipesz.docshelper.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.autoconfigure.ollama.OllamaConnectionDetails;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
class OllamaConfig {

    private static final Set<String> MODELS_WITH_THINKING = Set.of("qwen3.5:9b");

    @Bean
    OllamaApi ollamaApi(OllamaConnectionDetails connection, RestClient.Builder builder, ObjectMapper mapper,
                        @Value("${docshelper.ai.response-timeout:60s}") Duration responseTimeout) {
        String baseUrl = connection.getBaseUrl();
        WebClient streamingClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeaders(headers -> {
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
                })
                .build();

        return new OllamaApi(baseUrl, ollamaClientBuilder(builder, mapper, responseTimeout)) {
            @Override
            public Flux<ChatResponse> streamingChat(ChatRequest request) {
                Assert.notNull(request, "The request body can not be null.");
                Assert.isTrue(Boolean.TRUE.equals(request.stream()), "Request must set the stream property to true.");
                ObjectNode payload = mapper.valueToTree(request);
                disableThinkingIfNeeded(payload);
                return streamingClient.post()
                        .uri("/api/chat")
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToFlux(ChatResponse.class);
            }
        };
    }

    RestClient.Builder ollamaClientBuilder(RestClient.Builder builder, ObjectMapper mapper, Duration responseTimeout) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(responseTimeout);
        return builder.clone().requestFactory(requestFactory).requestInterceptor((request, body, execution) -> {
            // Spring AI 1.0.0-M1 nie udostępnia pola think w żądaniu Ollamy.
            if (request.getURI().getPath().endsWith("/api/chat")
                    && mapper.readTree(body) instanceof ObjectNode payload
                    && disableThinkingIfNeeded(payload)) {
                body = mapper.writeValueAsBytes(payload);
                request.getHeaders().setContentLength(body.length);
            }
            return execution.execute(request, body);
        });
    }

    private boolean disableThinkingIfNeeded(ObjectNode payload) {
        if (MODELS_WITH_THINKING.contains(payload.path("model").asText()) && !payload.has("think")) {
            payload.put("think", false);
            return true;
        }
        return false;
    }
}
