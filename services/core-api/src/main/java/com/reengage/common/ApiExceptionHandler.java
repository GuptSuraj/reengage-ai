package com.reengage.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class,
            MethodArgumentNotValidException.class})
    ProblemDetail badRequest(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ProblemDetail conflict(DuplicateKeyException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "The resource already exists", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden(AccessDeniedException exception, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "You do not have access to this resource", request);
    }

    private ProblemDetail problem(HttpStatus status, String detail, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
