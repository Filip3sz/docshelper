package code.filipesz.docshelper.controllers;

import code.filipesz.docshelper.dto.RagResponse;
import code.filipesz.docshelper.services.RagService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @GetMapping("/history")
    public Map<String, Object> getHistory() {
        return ragService.getFullHistory();
    }

    @PostMapping("/history")
    public ResponseEntity<Void> saveHistory(@RequestBody Map<String, Object> payload) {
        ragService.saveChatFromFrontend(payload);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/history/{chatId}")
    public ResponseEntity<Void> deleteChat(@PathVariable String chatId) {
        ragService.deleteChatFromBackend(chatId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/files")
    public ResponseEntity<List<String>> getFiles() {
        return ResponseEntity.ok(ragService.getListOfUploadedFiles());
    }

    @DeleteMapping("/files")
    public ResponseEntity<Void> deleteFile(@RequestParam("fileName") String fileName) {
        ragService.deleteFileFromKnowledgeBase(fileName);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            ragService.addFileToKnowledgeBase(file);
            return ResponseEntity.ok("Plik " + file.getOriginalFilename() + " został pomyślnie wczytany i zapisany w bazie wektorowej!");
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Błąd podczas przetwarzania pliku: " + e.getMessage());
        }
    }

    @GetMapping("/ask")
    public ResponseEntity<RagResponse> ask(
            @RequestParam("chatId") String chatId,
            @RequestParam("question") String question,
            @RequestParam(value = "files", required = false) List<String> files,
            @RequestParam(value = "action", required = false) String action) {

        RagResponse response = ragService.askWithContextDTO(chatId, question, files, action);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/ask-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> askStream(
            @RequestParam("chatId") String chatId,
            @RequestParam("question") String question,
            @RequestParam(value = "files", required = false) List<String> files,
            @RequestParam(value = "action", required = false) String action) {

        return ragService.askWithContextStream(chatId, question, files, action);
    }

    @GetMapping("/generate-title")
    public ResponseEntity<String> generateTitle(@RequestParam("question") String question) {
        String prompt = "Na podstawie poniższego pytania użytkownika stwórz krótki, zwięzły tytuł rozmowy (maksymalnie 3-5 słów). Nie używaj cudzysłowów ani znaków interpunkcyjnych na końcu:\n\n" + question;

        RagResponse response = ragService.askWithContextDTO("temp-title-session", prompt, null, null);
        String title = response.getAnswer();

        return ResponseEntity.ok(title.replaceAll("[\"']", "").trim());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleControllerExceptions(Exception e) {
        e.printStackTrace();
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.TEXT_PLAIN)
                .body("Błąd serwera: " + e.getMessage());
    }
}