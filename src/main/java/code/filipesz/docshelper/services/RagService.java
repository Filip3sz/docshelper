package code.filipesz.docshelper.services;

import code.filipesz.docshelper.dto.RagResponse;
import code.filipesz.docshelper.entities.Chat;
import code.filipesz.docshelper.entities.Message;
import code.filipesz.docshelper.repositories.ChatRepository;
import code.filipesz.docshelper.repositories.VectorDocumentRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RagService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final PromptFactory promptFactory;
    private final ChatRepository chatRepository;
    private final VectorDocumentRepository vectorDocumentRepository;

    // defaultChunkSize: 800 tokenów (odpowiednia długość na akapit)
    // minChunkSizeChars: 350
    // minChunkLength: 5
    // maxNumChunks: 10000
    // keepSeparator: true (zachowuje ciągłość zdań)
    private final DocumentTransformer textSplitter = new TokenTextSplitter(800, 350, 5, 10000, true);

    public RagService(ChatClient.Builder chatClientBuilder,
                      VectorStore vectorStore,
                      PromptFactory promptFactory,
                      ChatRepository chatRepository, VectorDocumentRepository vectorDocumentRepository) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
        this.promptFactory = promptFactory;
        this.chatRepository = chatRepository;
        this.vectorDocumentRepository = vectorDocumentRepository;
    }

    // ZARZĄDZANIE WIEDZĄ BAZA PGVECTOR, KTÓRA SIEDZI W POSTGRESQL

    public void addFileToKnowledgeBase(MultipartFile file) throws IOException {
        TikaDocumentReader tikaReader = new TikaDocumentReader(new InputStreamResource(file.getInputStream()));

        List<Document> docsWithMetadata = tikaReader.read().stream()
                .map(doc -> {
                    // Czyszczenie tekstu z nadmiarowych pustych linii i spacji
                    String cleanedContent = doc.getFormattedContent()
                            .replaceAll("\r\n|\r", "\n")
                            .replaceAll("\n{3,}", "\n\n") // Zamienia wielokrotne entery na maksymalnie dwa
                            .trim();

                    // Tworzymy nowy dokument z wyczyszczonym tekstem i metadanymi
                    Document cleanedDoc = new Document(cleanedContent, doc.getMetadata());
                    cleanedDoc.getMetadata().put("file_name", file.getOriginalFilename());
                    return cleanedDoc;
                })
                .toList();

        // Podział wyczyszczonego tekstu na inteligentne fragmenty z overlapem
        List<Document> splitDocuments = textSplitter.apply(docsWithMetadata);

        // Zapis do PostgreSQL
        vectorStore.add(splitDocuments);
    }

    public List<String> getListOfUploadedFiles() {
        List<Document> allDocs = vectorStore.similaritySearch(
                SearchRequest.query("a").withTopK(1000)
        );

        return allDocs.stream()
                .map(doc -> (String) doc.getMetadata().get("file_name"))
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    public synchronized void deleteFileFromKnowledgeBase(String fileName) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();

        List<Document> docsToDelete = vectorStore.similaritySearch(
                SearchRequest.query(fileName)
                        .withTopK(10000)
                        .withFilterExpression(b.eq("file_name", fileName).build())
        );

        if (!docsToDelete.isEmpty()) {
            List<String> docIds = docsToDelete.stream().map(Document::getId).toList();
            vectorStore.delete(docIds);
        }
    }

    // CAŁY SILNIK RAG I GENEROWANIE ODPOWIEDZI

    @Transactional
    public RagResponse askWithContextDTO(String chatId, String userQuery, List<String> selectedFiles, String action) {
        String lowerQuery = userQuery != null ? userQuery.toLowerCase() : "";
        String safeAction = action != null ? action.trim().toLowerCase() : "";

        List<Document> similarDocuments = fetchDocumentsFromVectorStore(userQuery, lowerQuery, safeAction, selectedFiles);

        String context = similarDocuments.stream()
                .map(Document::getFormattedContent)
                .collect(Collectors.joining("\n\n--- KONTEKST ---\n\n"));

        if ((promptFactory.isQuizRequest(safeAction, lowerQuery) || promptFactory.isSummaryRequest(safeAction, lowerQuery)) && context.isBlank()) {
            return new RagResponse("Nie znaleziono odpowiedniej treści w wybranych plikach.", Collections.emptyList());
        }

        String systemPrompt = promptFactory.createSystemPrompt(safeAction, lowerQuery, context);

        List<Message> dbHistory = loadHistoryFromDb(chatId);
        String fullUserPrompt = buildPromptWithHistory(dbHistory, userQuery);

        String responseText = chatClient.prompt()
                .system(systemPrompt)
                .user(fullUserPrompt)
                .call()
                .content();

        saveMessageToDb(chatId, "user", userQuery);
        saveMessageToDb(chatId, "ai", responseText);

        List<RagResponse.SourceDocument> sources = similarDocuments.stream()
                .map(doc -> new RagResponse.SourceDocument(
                        (String) doc.getMetadata().getOrDefault("file_name", "Nieznany plik"),
                        doc.getFormattedContent()
                ))
                .toList();

        return new RagResponse(responseText, sources);
    }

    @Transactional
    public Flux<String> askWithContextStream(String chatId, String userQuery, List<String> selectedFiles, String action) {
        String lowerQuery = userQuery != null ? userQuery.toLowerCase() : "";
        String safeAction = action != null ? action.trim().toLowerCase() : "";

        List<Document> similarDocuments = fetchDocumentsFromVectorStore(userQuery, lowerQuery, safeAction, selectedFiles);

        String context = similarDocuments.stream()
                .map(Document::getFormattedContent)
                .collect(Collectors.joining("\n\n--- KONTEKST ---\n\n"));

        if ((promptFactory.isQuizRequest(safeAction, lowerQuery) || promptFactory.isSummaryRequest(safeAction, lowerQuery)) && context.isBlank()) {
            return Flux.just("Nie znaleziono odpowiedniej treści w wybranych plikach.");
        }

        String systemPrompt = promptFactory.createSystemPrompt(safeAction, lowerQuery, context);

        List<Message> dbHistory = loadHistoryFromDb(chatId);
        String fullUserPrompt = buildPromptWithHistory(dbHistory, userQuery);

        StringBuilder fullResponseBuilder = new StringBuilder();

        return chatClient.prompt()
                .system(systemPrompt)
                .user(fullUserPrompt)
                .stream()
                .content()
                .doOnNext(fullResponseBuilder::append)
                .doOnComplete(() -> {
                    saveMessageToDb(chatId, "user", userQuery);
                    saveMessageToDb(chatId, "ai", fullResponseBuilder.toString());
                });
    }

    private List<Document> fetchDocumentsFromVectorStore(String userQuery, String lowerQuery, String safeAction, List<String> selectedFiles) {
        boolean isQuiz = promptFactory.isQuizRequest(safeAction, lowerQuery);
        boolean isSummary = promptFactory.isSummaryRequest(safeAction, lowerQuery);

        String searchQuery = isQuiz ? "główne pojęcia i kluczowe zagadnienia: " + userQuery :
                (isSummary ? "główne informacje, wprowadzenie, kluczowe fakty, opisy i wnioski" : userQuery);

        SearchRequest request = SearchRequest.query(searchQuery)
                .withTopK(isQuiz || isSummary ? 12 : 5);

        // Odrzucamy słabe dopasowania
        if (!isQuiz && !isSummary) {
            request = request.withSimilarityThreshold(0.55);
        }

        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            FilterExpressionBuilder.Op combinedOp = null;

            for (String file : selectedFiles) {
                FilterExpressionBuilder.Op eqOp = b.eq("file_name", file);
                combinedOp = (combinedOp == null) ? eqOp : b.or(combinedOp, eqOp);
            }

            if (combinedOp != null) {
                request = request.withFilterExpression(combinedOp.build());
            }
        }

        // Wyniki z wyszukiwania wektorowego semantycznego
        List<Document> vectorResults = vectorStore.similaritySearch(request);

        // Wyniki z wyszukiwania tekstowego
        List<Document> keywordResults = new ArrayList<>();
        if (userQuery != null && userQuery.trim().length() > 3) {
            String[] keywords = userQuery.split("\\s+");
            for (String word : keywords) {
                String cleanWord = word.replaceAll("[^a-zA-Z0-9ąowiećśźżŁÓĘĆŚŹŻ]", "").toLowerCase();
                // Przeszukujemy słowa kluczowe tylko dla konkretnych i dłuższych fraz (> 4 litery)
                if (cleanWord.length() > 4) {
                    List<Object[]> rawRows = vectorDocumentRepository.findByKeyword(cleanWord);
                    for (Object[] row : rawRows) {
                        String content = (String) row[1];
                        keywordResults.add(new Document(content));
                    }
                }
            }
        }

        // Połączenie wyników i limitowanie do maksymalnie 5 najbardziej trafnych
        Map<String, Document> combinedDocs = new LinkedHashMap<>();

        for (Document doc : vectorResults) {
            combinedDocs.put(doc.getFormattedContent(), doc);
        }
        for (Document doc : keywordResults) {
            combinedDocs.putIfAbsent(doc.getFormattedContent(), doc);
        }

        // Ograniczamy ostateczny kontekst do max 5 najlepiej dopasowanych kawałków
        return combinedDocs.values().stream()
                .limit(isQuiz || isSummary ? 12 : 5)
                .toList();
    }

    private String buildPromptWithHistory(List<Message> history, String userQuery) {
        StringBuilder fullUserPrompt = new StringBuilder();
        int maxHistoryMessages = 6;

        List<Message> recentHistory = history.size() > maxHistoryMessages
                ? new ArrayList<>(history.subList(history.size() - maxHistoryMessages, history.size()))
                : new ArrayList<>(history);

        if (!recentHistory.isEmpty()) {
            fullUserPrompt.append("Dotychczasowy przebieg rozmowy:\n");
            for (Message msg : recentHistory) {
                String text = msg.getText();
                if (text != null && text.startsWith("[") && text.endsWith("]")) {
                    text = "[Wygenerowano quiz]";
                }
                fullUserPrompt.append(msg.getSender()).append(": ").append(text).append("\n");
            }
            fullUserPrompt.append("\nNowe pytanie: ");
        }
        fullUserPrompt.append(userQuery);
        return fullUserPrompt.toString();
    }

    // 3. HISTORIA CZATÓW W BAZIE DANYCH POSTGRESQL

    @Transactional(readOnly = true)
    public Map<String, Object> getFullHistory() {
        List<Chat> chats = chatRepository.findAll();
        Map<String, Object> result = new HashMap<>();

        for (Chat chat : chats) {
            Map<String, Object> chatMap = new HashMap<>();
            chatMap.put("chatId", chat.getId());
            chatMap.put("title", chat.getTitle());

            List<Map<String, String>> messagesList = new ArrayList<>();
            for (Message msg : chat.getMessages()) {
                Map<String, String> msgMap = new HashMap<>();
                msgMap.put("sender", msg.getSender());
                msgMap.put("text", msg.getText());
                messagesList.add(msgMap);
            }
            chatMap.put("messages", messagesList);
            result.put(chat.getId(), chatMap);
        }

        return result;
    }

    @Transactional
    public void saveChatFromFrontend(Map<String, Object> payload) {
        String chatId = (String) payload.get("chatId");
        if (chatId == null) return;

        String title = (String) payload.getOrDefault("title", "Nowa rozmowa");

        Chat chat = chatRepository.findById(chatId)
                .orElseGet(() -> new Chat(chatId, title));

        if (payload.containsKey("title")) {
            chat.setTitle(title);
        }

        if (payload.get("messages") instanceof List<?> msgList) {
            chat.getMessages().clear();
            for (Object obj : msgList) {
                if (obj instanceof Map<?, ?> msgMap) {
                    String sender = (String) msgMap.get("sender");
                    String text = (String) msgMap.get("text");
                    chat.addMessage(new Message(sender, text));
                }
            }
        }

        chatRepository.save(chat);
    }

    @Transactional
    public void deleteChatFromBackend(String chatId) {
        if (chatId == null) return;
        chatRepository.deleteById(chatId);
    }

    private void saveMessageToDb(String chatId, String sender, String text) {
        Chat chat = chatRepository.findById(chatId)
                .orElseGet(() -> chatRepository.save(new Chat(chatId, "Nowa rozmowa")));

        chat.addMessage(new Message(sender, text));
        chatRepository.save(chat);
    }

    private List<Message> loadHistoryFromDb(String chatId) {
        return chatRepository.findById(chatId)
                .map(Chat::getMessages)
                .orElseGet(ArrayList::new);
    }
}