package code.filipesz.docshelper.services;

import code.filipesz.docshelper.repositories.KnowledgeBaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final DocumentAssistantService documentAssistant;
    private final VectorStore vectorStore;
    private final KnowledgeBaseRepository knowledgeBase;
    private final ChatService chatService;
    private final Set<String> changingFiles = new HashSet<>();

    public KnowledgeBaseService(DocumentAssistantService documentAssistant, VectorStore vectorStore,
                                KnowledgeBaseRepository knowledgeBase, ChatService chatService) {
        this.documentAssistant = documentAssistant;
        this.vectorStore = vectorStore;
        this.knowledgeBase = knowledgeBase;
        this.chatService = chatService;
    }

    public void addFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || !StringUtils.hasText(file.getOriginalFilename())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wybierz plik, który nie jest pusty.");
        }
        String fileName = file.getOriginalFilename();
        reserveFileChange(fileName);
        try {
            if (knowledgeBase.containsAllFiles(List.of(fileName))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Plik o tej nazwie już istnieje. Usuń go lub zmień nazwę nowego pliku.");
            }
            indexFile(file, fileName);
        } finally {
            releaseFileChange(fileName);
        }
    }

    public List<String> getFiles() {
        return knowledgeBase.findFileNames();
    }

    public void deleteFile(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wybierz plik do usunięcia.");
        }
        reserveFileChange(fileName);
        try {
            knowledgeBase.deleteFile(fileName);
        } finally {
            releaseFileChange(fileName);
        }
    }

    void reserveFilesForReply(List<String> activeFiles, Runnable reserveChatAction) {
        synchronized (changingFiles) {
            if (activeFiles.stream().anyMatch(changingFiles::contains)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Wybrany plik jest w trakcie zmiany. Spróbuj ponownie za chwilę.");
            }
            reserveChatAction.run();
        }
    }

    boolean containsAllFiles(List<String> files) {
        return knowledgeBase.containsAllFiles(files);
    }

    List<Document> fetchDocsForExam(String topic, List<String> files) {
        if (StringUtils.hasText(topic)) {
            return fetchDocs(topic, files);
        }
        return knowledgeBase.findChunksByFiles(files, 10);
    }

    List<Document> fetchDocs(String query, List<String> files) {
        var filters = new FilterExpressionBuilder();
        var selectedFiles = files.stream()
                .map(file -> filters.eq("file_name", file))
                .reduce(filters::or)
                .orElseThrow();
        return vectorStore.similaritySearch(SearchRequest.query(query).withTopK(5)
                .withFilterExpression(selectedFiles.build()));
    }

    private void indexFile(MultipartFile file, String fileName) throws IOException {
        String contentType = file.getContentType();
        if (contentType != null && contentType.startsWith("image/")) {
            processImageFile(file, fileName);
            return;
        }

        var reader = new TikaDocumentReader(new InputStreamResource(file.getInputStream()));
        var docs = reader.read().stream()
                .peek(document -> document.getMetadata().put("file_name", fileName))
                .toList();
        var chunks = new TokenTextSplitter().apply(docs);
        if (chunks.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nie udało się odczytać tekstu z pliku.");
        }
        vectorStore.add(chunks);
    }

    private void processImageFile(MultipartFile file, String fileName) throws IOException {
        var media = ImageScalerService.prepare(file);
        String imageDescription;
        try {
            imageDescription = documentAssistant.describeImage(media);
        } catch (RuntimeException e) {
            log.error("Nie udało się opisać obrazu {} przez model AI", fileName, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Nie udało się przeanalizować obrazu przez model AI.");
        }

        Document imageDoc = new Document(
                "Zawartość i opis pliku graficznego [" + fileName + "]:\n" + imageDescription,
                Map.of("file_name", fileName)
        );
        vectorStore.add(List.of(imageDoc));
    }

    private void reserveFileChange(String fileName) {
        synchronized (changingFiles) {
            if (changingFiles.contains(fileName)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Trwa zmiana tego pliku. Spróbuj ponownie za chwilę.");
            }
            if (chatService.isFileInUse(fileName)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ten plik jest używany w trwającej rozmowie.");
            }
            changingFiles.add(fileName);
        }
    }

    private void releaseFileChange(String fileName) {
        synchronized (changingFiles) {
            changingFiles.remove(fileName);
        }
    }
}
