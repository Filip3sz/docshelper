package code.filipesz.docshelper.services;

import code.filipesz.docshelper.dto.ExamDraftRequest;
import code.filipesz.docshelper.dto.ExamExportResponse;
import code.filipesz.docshelper.entities.Chat;
import code.filipesz.docshelper.entities.Message;
import code.filipesz.docshelper.repositories.ChatRepository;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Service
public class MessageService {

    private static final Duration MODEL_IDLE_TIMEOUT = Duration.ofMinutes(3);

    private final ChatRepository chatRepository;
    private final DocumentAssistantService documentAssistant;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ChatService chatService;
    private final LearnauditorService learnauditorService;
    private final TransactionTemplate transaction;
    private final ConcurrentMap<UUID, ActiveReply> repliesAwaitingObserver = new ConcurrentHashMap<>();

    public MessageService(ChatRepository chatRepository, DocumentAssistantService documentAssistant,
                          KnowledgeBaseService knowledgeBaseService, ChatService chatService,
                          LearnauditorService learnauditorService, PlatformTransactionManager transactionManager) {
        this.chatRepository = chatRepository;
        this.documentAssistant = documentAssistant;
        this.knowledgeBaseService = knowledgeBaseService;
        this.chatService = chatService;
        this.learnauditorService = learnauditorService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public List<Message> getHistory(String chatId) {
        return chatRepository.findById(chatId)
                .map(chat -> List.copyOf(chat.getMessages()))
                .orElseGet(List::of);
    }

    public ActiveReply startReply(String chatId, String question, List<String> files) {
        chatService.validateChatId(chatId);
        if (!StringUtils.hasText(question)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wpisz pytanie.");
        }
        List<String> activeFiles = (files != null)
                ? files.stream().filter(f -> f != null && !f.isBlank()).distinct().toList()
                : List.of();
        if (activeFiles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zaznacz co najmniej jeden plik w Bazie wiedzy.");
        }

        ActiveReply reply = new ActiveReply(chatId, question, activeFiles);
        knowledgeBaseService.reserveFilesForReply(activeFiles, () -> chatService.reserveChat(chatId, reply));

        try {
            if (!knowledgeBaseService.containsAllFiles(activeFiles)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Co najmniej jeden z wybranych plików jest już niedostępny. Wybierz pliki ponownie.");
            }
            repliesAwaitingObserver.put(reply.id(), reply);
            Schedulers.parallel().schedule(() -> repliesAwaitingObserver.remove(reply.id(), reply), 1, TimeUnit.MINUTES);
            Flux.defer(() -> streamReply(chatId, question, activeFiles, reply))
                    .subscribeOn(Schedulers.boundedElastic())
                    .takeUntilOther(reply.cancellation.asMono())
                    .concatWith(Flux.defer(() -> reply.cancelled
                            ? Flux.error(new CancellationException())
                            : Flux.empty()))
                    .doOnTerminate(() -> chatService.releaseChat(chatId, reply))
                    .subscribe(reply.events::tryEmitNext, reply.events::tryEmitError, reply.events::tryEmitComplete);
        } catch (RuntimeException error) {
            repliesAwaitingObserver.remove(reply.id(), reply);
            chatService.releaseChat(chatId, reply);
            reply.events.tryEmitError(error);
            throw error;
        }
        return reply;
    }

    public ActiveReply observeReply(String chatId, UUID replyId) {
        chatService.validateChatId(chatId);
        ActiveReply pending = repliesAwaitingObserver.get(replyId);
        if (pending != null && pending.chatId.equals(chatId)) {
            repliesAwaitingObserver.remove(replyId, pending);
            return pending;
        }
        return findReply(chatId, replyId);
    }

    public ActiveReply findReply(String chatId, UUID replyId) {
        chatService.validateChatId(chatId);
        return chatService.getActiveOperation(chatId) instanceof ActiveReply reply && reply.id().equals(replyId)
                ? reply
                : null;
    }

    public void cancelReply(String chatId, UUID replyId) {
        ActiveReply reply = findReply(chatId, replyId);
        if (reply == null) {
            return;
        }
        synchronized (reply) {
            if (chatService.getActiveOperation(chatId) == reply && !reply.saved) {
                reply.cancelled = true;
                reply.cancellation.tryEmitEmpty();
            }
        }
    }

    public ExamExportResponse exportExamToLearnauditor(String chatId, String topic, List<String> files) {
        chatService.validateChatId(chatId);
        List<String> activeFiles = (files != null)
                ? files.stream().filter(f -> f != null && !f.isBlank()).distinct().toList()
                : List.of();
        if (activeFiles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zaznacz co najmniej jeden plik w Bazie wiedzy.");
        }

        Object exportOp = new Object();
        knowledgeBaseService.reserveFilesForReply(activeFiles, () -> chatService.reserveChat(chatId, exportOp));
        try {
            if (!knowledgeBaseService.containsAllFiles(activeFiles)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Co najmniej jeden z wybranych plików jest już niedostępny. Wybierz pliki ponownie.");
            }
            List<Message> history = getHistory(chatId);
            List<Document> docs = knowledgeBaseService.fetchDocsForExam(topic, activeFiles);
            ExamDraftRequest draft = documentAssistant.generateLearnauditorExam(docs, history, topic);
            String importUrl = learnauditorService.sendExamDraft(draft);

            String userEntry = StringUtils.hasText(topic)
                    ? "Przygotuj test do LearnAuditora: " + topic.trim()
                    : "Przygotuj test do LearnAuditora z wybranych plików.";
            String summary = "Gotowe! Przygotowałem egzamin „" + draft.title() + "” ("
                    + draft.tasks().size() + " zadań) i przesłałem szkic do LearnAuditora. Zajrzyj do LearnAuditora, aby utworzyć test na swoim koncie.";
            saveMessages(chatId, userEntry, summary, activeFiles);

            return new ExamExportResponse(draft.title(), draft.tasks().size(), importUrl, summary);
        } finally {
            chatService.releaseChat(chatId, exportOp);
        }
    }

    private Flux<String> streamReply(String chatId, String question, List<String> files, ActiveReply reply) {
        List<Message> history = getHistory(chatId);
        String searchQuery = resolveSearchQuery(question, history);
        List<Document> docs = knowledgeBaseService.fetchDocs(searchQuery, files);
        StringBuilder fullResponse = new StringBuilder();

        return documentAssistant.answer(question, docs, history)
                .timeout(MODEL_IDLE_TIMEOUT)
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> {
                    String answer = fullResponse.toString();
                    if (answer.isBlank()) {
                        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Model zwrócił pustą odpowiedź.");
                    }
                    // Cancellation and the final database write share the operation monitor.
                    synchronized (reply) {
                        if (chatService.getActiveOperation(chatId) == reply && !reply.cancelled) {
                            saveMessages(chatId, question, answer, files);
                            reply.saved = true;
                        }
                    }
                });
    }

    private String resolveSearchQuery(String question, List<Message> history) {
        if (!question.strip().matches("(?i)[A-D][.)]?")) {
            return question;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            Message previous = history.get(i);
            if ("user".equals(previous.getSender())) {
                return previous.getText();
            }
        }
        return question;
    }

    private void saveMessages(String chatId, String userQuestion, String aiAnswer, List<String> files) {
        transaction.executeWithoutResult(status -> {
            String title = titleFor(userQuestion);
            Chat chat = chatRepository.findById(chatId)
                    .orElseGet(() -> Chat.builder().id(chatId).title(title).build());
            if (chat.getMessages().isEmpty()) {
                chat.setTitle(title);
            }
            chat.getSelectedFiles().clear();
            chat.getSelectedFiles().addAll(files);
            chat.addMessage(Message.builder().sender("user").text(userQuestion).build());
            chat.addMessage(Message.builder().sender("ai").text(aiAnswer).build());
            chatRepository.save(chat);
        });
    }

    private static String titleFor(String question) {
        return question.length() > 25 ? question.substring(0, 25) + "..." : question;
    }

    public static final class ActiveReply {
        private final UUID id = UUID.randomUUID();
        private final String chatId;
        private final String question;
        private final List<String> files;
        private final Sinks.One<Void> cancellation = Sinks.one();
        private final Sinks.Many<String> events = Sinks.many().replay().all();
        private volatile boolean cancelled;
        private boolean saved;

        private ActiveReply(String chatId, String question, List<String> files) {
            this.chatId = chatId;
            this.question = question;
            this.files = files;
        }

        public UUID id() {
            return id;
        }

        public String question() {
            return question;
        }

        List<String> files() {
            return files;
        }

        String title() {
            return titleFor(question);
        }

        public Flux<String> stream() {
            return events.asFlux();
        }
    }
}
