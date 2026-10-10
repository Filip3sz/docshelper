package code.filipesz.docshelper.config;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INVALID_REQUEST = "Sprawdź przesłane dane i spróbuj ponownie.";
    private static final String NOT_FOUND = "Ta strona nie istnieje.";
    private static final String UPLOAD_TOO_LARGE = "Plik przekracza dopuszczalny rozmiar.";
    private static final String SERVER_ERROR = "Nie udało się wykonać operacji. Spróbuj ponownie.";
    private static final Map<Integer, String> STATUS_MESSAGES = Map.of(
            HttpStatus.NOT_FOUND.value(), NOT_FOUND,
            HttpStatus.PAYLOAD_TOO_LARGE.value(), UPLOAD_TOO_LARGE
    );

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException error) {
        HttpStatusCode status = error.getStatusCode();
        String message = error.getReason();
        if (message == null || message.isBlank()) {
            message = defaultMessageFor(status);
        }
        if (status.is5xxServerError()) {
            log.error("Błąd obsługi żądania DocsHelper", error);
        }
        return ResponseEntity.status(status)
                .headers(error.getHeaders())
                .body(errorBody(message));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
            TypeMismatchException.class, HttpMessageNotReadableException.class, BindException.class})
    public ResponseEntity<Map<String, String>> handleBadRequest() {
        return ResponseEntity.badRequest().body(errorBody(INVALID_REQUEST));
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Map<String, String>> handleNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(NOT_FOUND));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleUploadLimit() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(errorBody(UPLOAD_TOO_LARGE));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, String>> handleMultipartError() {
        return ResponseEntity.badRequest().body(errorBody("Nie udało się przesłać pliku."));
    }

    @ExceptionHandler({ConversionNotSupportedException.class, Exception.class})
    @Nullable
    public ResponseEntity<Map<String, String>> handleUnexpectedError(Exception error, HttpServletResponse response) {
        if (response.isCommitted()) {
            return null;
        }
        HttpStatusCode status = HttpStatus.INTERNAL_SERVER_ERROR;
        HttpHeaders headers = HttpHeaders.EMPTY;
        if (error instanceof ErrorResponse frameworkError) {
            status = frameworkError.getStatusCode();
            headers = frameworkError.getHeaders();
        }
        if (status.is5xxServerError()) {
            log.error("Błąd obsługi żądania DocsHelper", error);
        }
        return ResponseEntity.status(status)
                .headers(headers)
                .body(errorBody(defaultMessageFor(status)));
    }

    private String defaultMessageFor(HttpStatusCode status) {
        return STATUS_MESSAGES.getOrDefault(status.value(),
                status.is5xxServerError() ? SERVER_ERROR : INVALID_REQUEST);
    }

    private Map<String, String> errorBody(String message) {
        String safeMessage = (message != null && !message.isBlank()) ? message : SERVER_ERROR;
        return Map.of("message", safeMessage);
    }
}
