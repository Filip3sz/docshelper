package code.filipesz.docshelper.controllers;

import code.filipesz.docshelper.services.ChatService;
import code.filipesz.docshelper.services.DashboardService;
import code.filipesz.docshelper.services.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ChatService chatService;

    @Value("${docshelper.learnauditor-url:http://localhost:8080/dashboard}")
    private String learnauditorUrl = "http://localhost:8080/dashboard";

    @ModelAttribute("learnauditorUrl")
    public String learnauditorUrl() {
        return learnauditorUrl;
    }

    @GetMapping("/")
    public String redirectToDashboard(@RequestParam(value = "chatId", required = false) String chatId,
                                      RedirectAttributes redirect) {
        if (chatId != null && !chatId.isBlank()) {
            redirect.addAttribute("chatId", chatId);
        }
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(value = "chatId", required = false) String chatId, Model model) {
        model.addAllAttributes(dashboardService.dashboardData(chatId));
        return "dashboard";
    }

    @GetMapping("/dashboard/history")
    public String conversationHistory(@RequestParam("chatId") String chatId, Model model) {
        model.addAllAttributes(dashboardService.conversationHistoryData(chatId));
        return "dashboard :: history";
    }

    // ZWYKŁE FORMULARZE MVC — PO OPERACJI WRACAMY NA DASHBOARD
    @PostMapping("/dashboard/files")
    public String uploadFile(@RequestParam("file") MultipartFile file, @RequestParam("chatId") String chatId,
                             RedirectAttributes redirect) throws IOException {
        try {
            knowledgeBaseService.addFile(file);
        } catch (ResponseStatusException error) {
            redirect.addFlashAttribute("pageError", error.getReason());
        }
        redirect.addAttribute("chatId", chatId);
        return "redirect:/dashboard";
    }

    @PostMapping("/dashboard/chats/delete")
    public String deleteChat(@RequestParam("chatId") String chatId, RedirectAttributes redirect) {
        try {
            chatService.deleteChat(chatId);
        } catch (ResponseStatusException error) {
            if (error.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
                throw error;
            }
            redirect.addFlashAttribute("pageError", error.getReason());
        }
        return "redirect:/dashboard";
    }
}
