package code.filipesz.docshelper.services;

import code.filipesz.docshelper.entities.Chat;
import code.filipesz.docshelper.repositories.ChatRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ChatService {

    private final ChatRepository chatRepository;
    private final TransactionTemplate transaction;
    private final ConcurrentMap<String, Object> activeOperations = new ConcurrentHashMap<>();

    public ChatService(ChatRepository chatRepository, PlatformTransactionManager transactionManager) {
        this.chatRepository = chatRepository;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public Chat getChatById(String chatId) {
        validateChatId(chatId);
        return chatRepository.findById(chatId).orElse(null);
    }

    public void updateSelectedFiles(String chatId, List<String> files) {
        changeChat(chatId, () -> {
            Chat chat = chatRepository.findById(chatId)
                    .orElseGet(() -> Chat.builder().id(chatId).title("Nowa rozmowa").build());
            chat.getSelectedFiles().clear();
            if (files != null) {
                chat.getSelectedFiles().addAll(files);
            }
            chatRepository.save(chat);
        });
    }

    public void deleteChat(String chatId) {
        changeChat(chatId, () -> {
            if (chatRepository.existsById(chatId)) {
                chatRepository.deleteById(chatId);
            }
        });
    }

    public Map<String, MessageService.ActiveReply> activeReplies() {
        Map<String, MessageService.ActiveReply> replies = new HashMap<>();
        activeOperations.forEach((chatId, operation) -> {
            if (operation instanceof MessageService.ActiveReply reply) {
                replies.put(chatId, reply);
            }
        });
        return replies;
    }

    boolean isFileInUse(String fileName) {
        return activeOperations.values().stream()
                .anyMatch(operation -> operation instanceof MessageService.ActiveReply reply && reply.files().contains(fileName));
    }

    Object getActiveOperation(String chatId) {
        return activeOperations.get(chatId);
    }

    void reserveChat(String chatId, Object operation) {
        if (activeOperations.putIfAbsent(chatId, operation) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "W tej rozmowie trwa inna operacja. Poczekaj chwilę i spróbuj ponownie.");
        }
    }

    void releaseChat(String chatId, Object operation) {
        synchronized (operation) {
            activeOperations.remove(chatId, operation);
        }
    }

    void validateChatId(String chatId) {
        if (!StringUtils.hasText(chatId) || chatId.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieprawidłowy identyfikator rozmowy.");
        }
    }

    private void changeChat(String chatId, Runnable action) {
        validateChatId(chatId);
        Object operation = new Object();
        reserveChat(chatId, operation);
        try {
            transaction.executeWithoutResult(status -> action.run());
        } finally {
            releaseChat(chatId, operation);
        }
    }
}
