package code.filipesz.docshelper.controllers;

import code.filipesz.docshelper.services.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/files")
    public ResponseEntity<Void> updateSelectedFiles(
            @RequestParam("chatId") String chatId,
            @RequestParam(value = "files", required = false) List<String> files) {
        chatService.updateSelectedFiles(chatId, files != null ? files : List.of());
        return ResponseEntity.ok().build();
    }
}
