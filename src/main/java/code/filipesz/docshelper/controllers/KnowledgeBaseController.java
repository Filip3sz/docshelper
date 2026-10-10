package code.filipesz.docshelper.controllers;

import code.filipesz.docshelper.services.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @PostMapping("/delete")
    public ResponseEntity<Void> deleteFile(@RequestParam("fileName") String fileName) {
        knowledgeBaseService.deleteFile(fileName);
        return ResponseEntity.noContent().build();
    }
}
