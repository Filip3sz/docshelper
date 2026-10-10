package code.filipesz.docshelper.dto;

public record ExamExportResponse(
        String title,
        int taskCount,
        String importUrl,
        String message
) {
}
