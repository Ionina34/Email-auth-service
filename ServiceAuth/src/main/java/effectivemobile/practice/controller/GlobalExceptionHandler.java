package effectivemobile.practice.controller;

import effectivemobile.practice.controller.dto.out.ErrorResponse;
import effectivemobile.practice.security.exception.AccountNotVerified;
import effectivemobile.practice.security.exception.TimeoutConfirmationCodeException;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityExistsException.class)
    public ResponseEntity<ErrorResponse> handlerEntityExistsException(EntityExistsException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("Try another email.", e.getMessage()));
    }

    @ExceptionHandler(AccountNotVerified.class)
    public ResponseEntity<ErrorResponse> handlerAccountNotVerified(AccountNotVerified e) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("Check your email and send the verification code.", e.getMessage()));
    }

    @ExceptionHandler(TimeoutConfirmationCodeException.class)
    public ResponseEntity<ErrorResponse> handlerTimeoutConfirmationCode(TimeoutConfirmationCodeException e) {
        return ResponseEntity
                .status(HttpStatus.REQUEST_TIMEOUT)
                .body(new ErrorResponse("The code is invalid. Send a repeat registration request.", e.getMessage()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlerEntityNotFound(EntityNotFoundException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("There may be an error in writing the mail.", e.getMessage()));
    }
}
