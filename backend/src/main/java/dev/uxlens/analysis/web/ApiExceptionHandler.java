package dev.uxlens.analysis.web;

import dev.uxlens.image.ImageTooLargeException;
import dev.uxlens.image.InvalidImageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Turns expected errors into RFC 9457 problem details ({@code application/problem+json}). */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(InvalidImageException.class)
    ProblemDetail invalidImage(InvalidImageException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid image", e.getMessage());
    }

    @ExceptionHandler({ImageTooLargeException.class, MaxUploadSizeExceededException.class})
    ProblemDetail tooLarge(Exception e) {
        return problem(HttpStatus.CONTENT_TOO_LARGE, "File too large", "The file is larger than 5 MB.");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ProblemDetail missingFile(MissingServletRequestPartException e) {
        return problem(HttpStatus.BAD_REQUEST, "Missing file", "Send the screenshot in a multipart field named 'file'.");
    }

    @ExceptionHandler(AnalysisNotFoundException.class)
    ProblemDetail notFound(AnalysisNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Analysis not found", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
