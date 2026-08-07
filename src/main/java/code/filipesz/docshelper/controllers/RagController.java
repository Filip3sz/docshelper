package code.filipesz.docshelper.controllers;

import code.filipesz.docshelper.services.RagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/add")
    public String addData(@RequestBody String text) {
        ragService.addDocumentToKnowledgeBase(text);
        return "Dane pomyślnie zapisane w pamięci RAM aplikacji!";
    }

    @GetMapping("/ask")
    public String ask(@RequestParam String question) {
        return ragService.askWithContext(question);
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            ragService.addFileToKnowledgeBase(file);
            return ResponseEntity.ok("Plik " + file.getOriginalFilename() + " został pomyślnie wczytany i zapisany w bazie wektorowej!");
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Błąd podczas przetwarzania pliku: " + e.getMessage());
        }
    }
}
