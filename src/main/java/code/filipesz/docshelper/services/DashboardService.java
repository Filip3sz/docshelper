package code.filipesz.docshelper.services;

import code.filipesz.docshelper.entities.Chat;
import code.filipesz.docshelper.repositories.ChatRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
// DANE DO WIDOKU DASHBOARDU — KONTROLER OBSŁUGUJE TYLKO ŻĄDANIA I WIDOKI
public class DashboardService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final ChatService chatService;
    private final ChatRepository chatRepository;

    public DashboardService(KnowledgeBaseService knowledgeBaseService, ChatService chatService,
                            ChatRepository chatRepository) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.chatService = chatService;
        this.chatRepository = chatRepository;
    }

    public Map<String, Object> dashboardData(String chatId) {
        String currentChatId = chatId == null || chatId.isBlank() ? "session-" + System.currentTimeMillis() : chatId;
        Map<String, MessageService.ActiveReply> activeReplies = chatService.activeReplies();
        Map<String, Object> model = new LinkedHashMap<>(conversationHistoryData(currentChatId, activeReplies));

        List<String> uploadedFiles = knowledgeBaseService.getFiles();
        Chat currentChat = chatService.getChatById(currentChatId);
        MessageService.ActiveReply activeReply = activeReplies.get(currentChatId);
        List<String> selectedFiles = uploadedFiles;
        if (activeReply != null) {
            selectedFiles = activeReply.files();
        } else if (currentChat != null && currentChat.getSelectedFiles() != null) {
            selectedFiles = currentChat.getSelectedFiles();
        }

        model.put("uploadedFiles", uploadedFiles);
        model.put("messages", currentChat != null && currentChat.getMessages() != null ? currentChat.getMessages() : List.of());
        model.put("selectedContextFiles", selectedFiles);
        model.put("activeReplyId", activeReply == null ? "" : activeReply.id().toString());
        model.put("activeQuestion", activeReply == null ? "" : activeReply.question());
        return model;
    }

    public Map<String, Object> conversationHistoryData(String chatId) {
        return conversationHistoryData(chatId, chatService.activeReplies());
    }

    private Map<String, Object> conversationHistoryData(String chatId, Map<String, MessageService.ActiveReply> activeReplies) {
        Map<String, Chat> history = new LinkedHashMap<>();
        chatRepository.findAll().stream()
                .filter(chat -> chat.getMessages() != null && !chat.getMessages().isEmpty())
                .forEach(chat -> history.put(chat.getId(), chat));
        activeReplies.forEach((id, reply) -> history.putIfAbsent(id, Chat.builder().id(id).title(reply.title()).build()));
        Chat activeChat = history.get(chatId);
        return Map.of(
                "currentChatId", chatId,
                "chats", history.values(),
                "conversationTitle", activeChat == null || activeChat.getTitle() == null ? "Nowa rozmowa" : activeChat.getTitle()
        );
    }
}
