package code.filipesz.docshelper.controllers;

import code.filipesz.docshelper.entities.Chat;
import code.filipesz.docshelper.services.RagService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.List;

@Controller
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @GetMapping("/")
    public String redirectToRag() {
        return "redirect:/rag";
    }

    @GetMapping("/rag")
    public String index(@RequestParam(value = "chatId", required = false) String chatId, Model model) {
        ragService.cleanupEmptyChats();

        if (chatId == null || chatId.isBlank()) {
            chatId = "session-" + System.currentTimeMillis();
        }

        model.addAttribute("currentChatId", chatId);

        List<String> allFiles = ragService.getFiles();
        model.addAttribute("uploadedFiles", allFiles);
        model.addAttribute("chats", ragService.getHistory());

        Chat currentChat = ragService.getChatById(chatId);
        model.addAttribute("messages", currentChat != null ? currentChat.getMessages() : List.of());

        List<String> selectedFiles;
        if (currentChat != null && currentChat.getSelectedFiles() != null) {
            selectedFiles = currentChat.getSelectedFiles();
        } else {
            selectedFiles = allFiles;
        }

        model.addAttribute("selectedContextFiles", selectedFiles);

        return "index";
    }

    @PostMapping("/rag/update-files")
    @ResponseBody
    public ResponseEntity<Void> updateSelectedFiles(
            @RequestParam("chatId") String chatId,
            @RequestParam(value = "files", required = false) List<String> files) {
        ragService.updateSelectedFiles(chatId, files != null ? files : List.of());
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/rag/ask-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public Flux<String> askStream(
            @RequestParam("chatId") String chatId,
            @RequestParam("question") String question,
            @RequestParam(value = "files", required = false) List<String> files) {
        return ragService.askStream(chatId, question, files)
                .onErrorResume(throwable -> {
                    return Flux.empty();
                });
    }

    @PostMapping("/rag/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file, @RequestParam("chatId") String chatId) {
        if (file != null && !file.isEmpty()) {
            try {
                ragService.addFile(file);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return "redirect:/rag?chatId=" + chatId;
    }

    @PostMapping("/rag/delete-file")
    public String deleteFile(@RequestParam("fileName") String fileName, @RequestParam("chatId") String chatId) {
        ragService.deleteFile(fileName);
        return "redirect:/rag?chatId=" + chatId;
    }

    @PostMapping("/rag/delete-chat")
    public String deleteChat(@RequestParam("chatId") String chatId) {
        ragService.deleteChat(chatId);
        return "redirect:/rag";
    }
}