package code.filipesz.docshelper.services;

import code.filipesz.docshelper.entities.Chat;
import code.filipesz.docshelper.entities.Message;
import code.filipesz.docshelper.repositories.ChatRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Media;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RagService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ChatRepository chatRepository;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.vision.options.model:llama3.2-vision}")
    private String visionModelName;

    public RagService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore, ChatRepository chatRepository) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.chatRepository = chatRepository;
    }

    public void addFile(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        String contentType = file.getContentType();

        if (contentType != null && contentType.startsWith("image/")) {
            // PLIKI ZDJĘCIOWE
            processImageFile(file);
        } else {
            // PLIKI TEKSTOWE
            var reader = new TikaDocumentReader(new InputStreamResource(file.getInputStream()));
            var docs = reader.read().stream()
                    .peek(d -> d.getMetadata().put("file_name", fileName))
                    .toList();
            vectorStore.add(new TokenTextSplitter().apply(docs));
        }
    }

    private void processImageFile(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();

        OllamaApi ollamaApi = new OllamaApi(ollamaBaseUrl);
        OllamaOptions options = new OllamaOptions();
        options.setModel(visionModelName);

        ChatModel visionModel = new OllamaChatModel(ollamaApi, options);

        var media = new Media(MimeTypeUtils.parseMimeType(file.getContentType()), new ByteArrayResource(file.getBytes()));
        var userMessage = new UserMessage(
                """
                Twoim zadaniem jest dokładna ekstrakcja informacji z tego obrazu na potrzeby bazy wiedzy systemów RAG.
                
                Przeanalizuj obraz i wykonaj następujące kroki:
                1. DOKŁADNY OCR: Przeczytaj i przepisz DOSŁOWNIE cały widoczny tekst, cyfry, nagłówki, etykiety, daty, godziny oraz tabele (z zachowaniem powiązań między wierszami a kolumnami). Nie opuszczaj żadnych drobnych napisów.
                2. OPIS WIZUALNY: Jeśli obraz zawiera elementy graficzne, wykresy, schematy lub ikony, opisz krótko co przedstawiają.
                3. STRUKTURA: Przedstaw odczytane dane w sposób ustrukturyzowany, przejrzysty i czytelny.
                
                Odpowiadaj wyłącznie na podstawie tego, co faktycznie widzisz na obrazie. Nie zgaduj i nie dodawaj informacji z zewnątrz.
                """,
                List.of(media)
        );

        String imageDescription = visionModel.call(userMessage);

        Document imageDoc = new Document(
                "Zawartość i opis pliku graficznego [" + fileName + "]:\n" + imageDescription,
                Map.of("file_name", fileName)
        );

        vectorStore.add(List.of(imageDoc));
    }

    public List<String> getFiles() {
        return vectorStore.similaritySearch(SearchRequest.query("a").withTopK(1000))
                .stream()
                .map(d -> (String) d.getMetadata().get("file_name"))
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    @Transactional
    public void updateSelectedFiles(String chatId, List<String> files) {
        if (chatId == null || chatId.isBlank()) return;

        Chat chat = chatRepository.findById(chatId).orElseGet(() -> {
            Chat newChat = new Chat(chatId, "Nowa rozmowa");
            return chatRepository.save(newChat);
        });

        chat.getSelectedFiles().clear();
        if (files != null && !files.isEmpty()) {
            chat.getSelectedFiles().addAll(files);
        }

        chatRepository.save(chat);
    }

    public void deleteFile(String fileName) {
        var expr = new FilterExpressionBuilder().eq("file_name", fileName).build();
        var docs = vectorStore.similaritySearch(SearchRequest.query(fileName).withTopK(10000).withFilterExpression(expr));
        if (!docs.isEmpty()) {
            vectorStore.delete(docs.stream().map(Document::getId).toList());
        }
    }

    @Transactional
    public Flux<String> askStream(String chatId, String question, List<String> files) {
        List<String> activeFiles = (files != null)
                ? files.stream().filter(f -> f != null && !f.isBlank()).toList()
                : List.of();

        List<Document> docs = fetchDocs(question, activeFiles);
        String context = buildContext(docs);
        StringBuilder fullResponse = new StringBuilder();

        String systemPrompt = """
                Jesteś interaktywnym asystentem edukacyjnym DocsHelper.
                Twój główny cel to pomaganie użytkownikowi w nauce na podstawie dostarczonego KONTEKSTU.
                
                ZASADY GŁÓWNE:
                1. Odpowiadaj wyłącznie w języku polskim.
                2. Stosuj przejrzyste formatowanie Markdown (pogrubienia, nagłówki, listy).
                3. Bazuj WYŁĄCZNIE na podanym KONTEKŚCIE. Jeśli brakuje informacji, poinformuj o tym użytkownika.
                
                OBSŁUGA QUIZÓW:
                - Jeśli użytkownik prosi o "quiz", "test" lub "pytanie z dokumentu", wygeneruj pytanie jednokrotnego wyboru na podstawie KONTEKSTU.
                - Formatuj quiz dokładnie w poniższy sposób:
                
                  **Pytanie:** [Treść pytania]
                  A) [Opcja 1]
                  B) [Opcja 2]
                  C) [Opcja 3]
                  D) [Opcja 4]
                  
                  *Wskazówka: Wybierz literę A, B, C lub D!*
                  
                - Kiedy użytkownik odpowie (np. "A"), sprawdź jego odpowiedź z KONTEKSTEM i napisz, czy odpowiedział poprawnie oraz podaj krótkie wyjaśnienie.
                
                KONTEKST Z DOKUMENTÓW:
                """ + context;

        return chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .stream()
                .content()
                .map(chunk -> chunk.replace(" ", "\u00A0"))
                .doOnNext(chunk -> fullResponse.append(chunk.replace("\u00A0", " ")))
                .doOnComplete(() -> saveMessages(chatId, question, fullResponse.toString(), activeFiles));
    }

    private List<Document> fetchDocs(String query, List<String> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        SearchRequest req = SearchRequest.query(query).withTopK(5);
        var b = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op combined = null;
        for (String file : files) {
            FilterExpressionBuilder.Op eq = b.eq("file_name", file);
            combined = (combined == null) ? eq : b.or(combined, eq);
        }
        if (combined != null) {
            req = req.withFilterExpression(combined.build());
        }

        return vectorStore.similaritySearch(req);
    }

    private String buildContext(List<Document> docs) {
        if (docs.isEmpty()) return "Brak kontekstu z dokumentów.";
        StringBuilder sb = new StringBuilder();
        for (Document doc : docs) {
            sb.append(doc.getFormattedContent()).append("\n\n---\n\n");
        }
        return sb.toString();
    }

    @Transactional
    public void cleanupEmptyChats() {
        List<Chat> emptyChats = chatRepository.findAll().stream()
                .filter(chat -> (chat.getMessages() == null || chat.getMessages().isEmpty())
                        && ("Nowa rozmowa".equals(chat.getTitle()) || chat.getTitle() == null))
                .toList();
        if (!emptyChats.isEmpty()) {
            chatRepository.deleteAll(emptyChats);
        }
    }

    @Transactional(readOnly = true)
    public List<Chat> getHistory() {
        return chatRepository.findAll().stream()
                .filter(chat -> chat.getMessages() != null && !chat.getMessages().isEmpty())
                .toList();
    }

    @Transactional(readOnly = true)
    public Chat getChatById(String chatId) {
        return chatRepository.findById(chatId).orElse(null);
    }

    @Transactional
    public void deleteChat(String chatId) {
        if (chatRepository.existsById(chatId)) {
            chatRepository.deleteById(chatId);
        }
    }

    private void saveMessages(String chatId, String userQuestion, String aiAnswer, List<String> files) {
        String title = userQuestion.length() > 25 ? userQuestion.substring(0, 25) + "..." : userQuestion;

        Chat chat = chatRepository.findById(chatId)
                .orElseGet(() -> chatRepository.save(new Chat(chatId, title)));

        chat.setTitle(title);

        if (files != null) {
            chat.getSelectedFiles().clear();
            chat.getSelectedFiles().addAll(files);
        }

        chat.addMessage(new Message("user", userQuestion));
        chat.addMessage(new Message("ai", aiAnswer));

        chatRepository.save(chat);
    }
}