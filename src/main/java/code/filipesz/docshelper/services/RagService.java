package code.filipesz.docshelper.services;

import jakarta.annotation.PreDestroy;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public RagService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
    }

    public void addDocumentToKnowledgeBase(String textContent) {
        Document document = new Document(textContent);
        vectorStore.add(List.of(document));
    }

    public String askWithContext(String userQuery) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(userQuery)
                .topK(2)
                .build();

        List<Document> similarDocuments = vectorStore.similaritySearch(searchRequest);

        String context = similarDocuments.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        return chatClient.prompt()
                .system("Odpowiedz na pytanie użytkownika wyłącznie na podstawie poniższego kontekstu:\n" + context)
                .user(userQuery)
                .call()
                .content();
    }

    public void addFileToKnowledgeBase(MultipartFile file) throws IOException {
        InputStreamResource resource = new InputStreamResource(file.getInputStream());

        TikaDocumentReader tikaReader = new TikaDocumentReader(resource);
        List<Document> documents = tikaReader.read();

        vectorStore.add(documents);
    }

    @PreDestroy
    public void saveDatabaseOnShutdown() {
        File databaseFile = new File("vector_database.json");

        if (this.vectorStore instanceof SimpleVectorStore) {
            ((SimpleVectorStore) this.vectorStore).save(databaseFile);
            System.out.println("💾 Baza wektorowa została pomyślnie zapisana do pliku vector_database.json!");
        }
    }
}