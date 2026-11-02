package com.daara.internatadmin.common;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "INVALID_CURRENT_PASSWORD", "message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "INVALID_REQUEST", "message", ex.getMessage()));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Map<String, String>> handleDisabled(DisabledException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "ACCOUNT_DISABLED", "message", "Ce compte a été désactivé."));
    }
    @ExceptionHandler(DuplicateEntityException.class)
    public ResponseEntity<Map<String, String>> handleDuplicate(DuplicateEntityException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "POSSIBLE_DUPLICATE", "message", ex.getMessage()));
    }
    @ExceptionHandler(PaymentAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handlePaymentDuplicate(PaymentAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "PAYMENT_ALREADY_EXISTS", "message", ex.getMessage()));
    }

    @ExceptionHandler(AmountMismatchException.class)
    public ResponseEntity<Map<String, String>> handleAmountMismatch(AmountMismatchException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "AMOUNT_MISMATCH", "message", ex.getMessage()));
    }

    @ExceptionHandler(PartialPaymentException.class)
    public ResponseEntity<Map<String, String>> handlePartialPayment(PartialPaymentException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "PARTIAL_PAYMENT_NOT_CONFIRMED", "message", ex.getMessage()));
    }
    // Echec d'acquisition d'un verrou (lock_timeout) : jamais un 409, que le frontend lirait comme une erreur metier.
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleLockFailure(PessimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "SERVICE_UNAVAILABLE", "message", "Service temporairement indisponible. Veuillez réessayer."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", "FILE_TOO_LARGE", "message", "Le fichier dépasse la taille maximale autorisée."));
    }
}