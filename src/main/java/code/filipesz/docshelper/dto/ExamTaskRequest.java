package code.filipesz.docshelper.dto;

public record ExamTaskRequest(
        String name,
        String description,
        String requirements,
        String solution
) {
}
