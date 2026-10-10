package code.filipesz.docshelper.dto;

import java.util.List;

public record ExamDraftRequest(
        String title,
        String lecture,
        List<ExamTaskRequest> tasks
) {
}
