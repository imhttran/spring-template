package com.example.template.api;

import com.example.template.service.error.ConflictException;
import com.example.template.service.error.ForbiddenException;
import com.example.template.service.error.NotFoundException;
import com.example.template.service.error.ServerErrorException;
import com.example.template.service.error.TooManyRequestsException;
import com.example.template.service.error.UnauthenticatedException;
import com.example.template.service.error.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns service exceptions into the response shapes the API has always
 * returned. The defaults here are the common case for each exception; endpoints
 * whose 400s use the other shape catch the exception themselves (see
 * ProfileController and UsersController).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(
        ApiExceptionHandler.class
    );

    @ExceptionHandler(ApiRejection.class)
    public ResponseEntity<Object> rejection(ApiRejection rejection) {
        return rejection.response();
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Object> validation(ValidationException invalid) {
        return Api.respond(
            HttpStatus.BAD_REQUEST,
            Api.fail(invalid.getMessage())
        );
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Object> conflict(ConflictException conflict) {
        return Api.respond(
            HttpStatus.BAD_REQUEST,
            Api.fail(conflict.getMessage())
        );
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Object> notFound(NotFoundException missing) {
        return Api.respond(HttpStatus.NOT_FOUND, Api.msg(missing.getMessage()));
    }

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<Object> unauthenticated(
        UnauthenticatedException rejected
    ) {
        return Api.respond(
            HttpStatus.UNAUTHORIZED,
            Api.fail(rejected.getMessage())
        );
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Object> forbidden(ForbiddenException forbidden) {
        return Api.respond(
            HttpStatus.FORBIDDEN,
            Api.fail(forbidden.getMessage())
        );
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<Object> tooManyRequests(
        TooManyRequestsException throttled
    ) {
        return Api.respond(
            HttpStatus.TOO_MANY_REQUESTS,
            Api.fail(throttled.getMessage())
        );
    }

    /** The reason is logged, never sent: {@code respond_500} did the same. */
    @ExceptionHandler(ServerErrorException.class)
    public ResponseEntity<Object> serverError(ServerErrorException failure) {
        log.error(
            "{}: {}",
            failure.context(),
            String.valueOf(failure.getCause())
        );
        return Api.respond(
            HttpStatus.INTERNAL_SERVER_ERROR,
            failure.withSuccess()
                ? Api.fail("Internal server error")
                : Api.msg("Internal server error")
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpected(Exception failure) {
        if (failure instanceof ErrorResponse frameworkError) {
            // Framework-raised statuses keep their meaning: an unmapped path is
            // still a 404, a wrong method still a 405.
            return Api.respond(
                HttpStatus.valueOf(frameworkError.getStatusCode().value()),
                Api.msg(
                    HttpStatus.valueOf(
                        frameworkError.getStatusCode().value()
                    ).getReasonPhrase()
                )
            );
        }
        log.error("Unhandled error", failure);
        return Api.respond(
            HttpStatus.INTERNAL_SERVER_ERROR,
            Api.msg("Internal server error")
        );
    }
}
