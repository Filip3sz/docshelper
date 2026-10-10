package code.filipesz.docshelper.controllers;

import code.filipesz.docshelper.dto.ExamExportResponse;
import code.filipesz.docshelper.services.MessageService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private static final Logger log = LoggerFactory.getLogger(MessageController.class);

    private final MessageService messageService;

    @PostMapping(value = "/replies", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ReplyStarted startReply(
            @RequestParam("chatId") String chatId,
            @RequestParam("question") String question,
            @RequestParam(value = "files", required = false) List<String> files) {
        return new ReplyStarted(messageService.startReply(chatId, question, files).id());
    }

    @GetMapping(value = "/replies/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AnswerChunk>> streamReply(@RequestParam("chatId") String chatId,
                                                          @RequestParam("replyId") UUID replyId) {
        MessageService.ActiveReply reply = messageService.observeReply(chatId, replyId);
        return reply == null ? Flux.just(event("done", "")) : renderReply(reply);
    }

    @PostMapping("/replies/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelReply(@RequestParam("chatId") String chatId, @RequestParam("replyId") UUID replyId) {
        messageService.cancelReply(chatId, replyId);
    }

    @PostMapping(value = "/learnauditor-exam", produces = MediaType.APPLICATION_JSON_VALUE)
    public ExamExportResponse createLearnauditorExam(
            @RequestParam("chatId") String chatId,
            @RequestParam(value = "topic", required = false) String topic,
            @RequestParam(value = "files", required = false) List<String> files) {
        return messageService.exportExamToLearnauditor(chatId, topic, files);
    }

    private Flux<ServerSentEvent<AnswerChunk>> renderReply(MessageService.ActiveReply reply) {
        return reply.stream()
                .map(text -> ServerSentEvent.builder(new AnswerChunk(text)).build())
                .concatWithValues(event("done", ""))
                .onErrorResume(CancellationException.class, error -> Flux.just(event("cancelled", "")))
                .onErrorResume(error -> {
                    log.error("Nie udało się dokończyć odpowiedzi czatu", error);
                    return Flux.just(event("failed", "Nie udało się dokończyć odpowiedzi. Spróbuj ponownie."));
                });
    }

    private ServerSentEvent<AnswerChunk> event(String event, String text) {
        return ServerSentEvent.builder(new AnswerChunk(text)).event(event).build();
    }

    public record ReplyStarted(UUID replyId) {}

    public record AnswerChunk(String text) {}
}
