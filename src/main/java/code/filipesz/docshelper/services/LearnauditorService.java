package code.filipesz.docshelper.services;

import code.filipesz.docshelper.dto.ExamDraftRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;

@Service
public class LearnauditorService {

    private static final Logger log = LoggerFactory.getLogger(LearnauditorService.class);

    private final RestClient restClient;

    public LearnauditorService(RestClient.Builder builder,
                               @Value("${docshelper.learnauditor-api-url:http://127.0.0.1:8080}") String apiUrl) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = builder.clone()
                .baseUrl(apiUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public String sendExamDraft(ExamDraftRequest draft) {
        try {
            DraftCreatedResponse response = restClient.post()
                    .uri("/api/docshelper/drafts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(draft)
                    .retrieve()
                    .body(DraftCreatedResponse.class);

            if (response == null || !StringUtils.hasText(response.importUrl())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "LearnAuditor nie zwrócił linku do utworzenia testu.");
            }
            return response.importUrl();
        } catch (RestClientException e) {
            log.error("Nie udało się przesłać szkicu egzaminu do LearnAuditora", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Nie udało się połączyć z LearnAuditorem. Upewnij się, że aplikacja działa na porcie 8080.");
        }
    }

    private record DraftCreatedResponse(UUID draftId, String importUrl) {}
}
