package com.daara.internatadmin.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Correspondance exception vers statut et code d'erreur, reprise a l'identique du monolithe.
 * Les erreurs de validation Bean Validation ne sont volontairement pas traitees ici :
 * le corps 400 par defaut de Spring Boot est conserve (le frontend affiche alors ses messages de repli).
 * Une ressource introuvable reste un 400 INVALID_REQUEST, comme dans le monolithe.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabled(DisabledException ex) {
        return error(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Ce compte a été désactivé.");
    }

    @ExceptionHandler(DuplicateEntityException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateEntityException ex) {
        return error(HttpStatus.CONFLICT, "POSSIBLE_DUPLICATE", ex.getMessage());
    }

    @ExceptionHandler(PaymentAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handlePaymentDuplicate(PaymentAlreadyExistsException ex) {
        return error(HttpStatus.CONFLICT, "PAYMENT_ALREADY_EXISTS", ex.getMessage());
    }

    @ExceptionHandler(AmountMismatchException.class)
    public ResponseEntity<ErrorResponse> handleAmountMismatch(AmountMismatchException ex) {
        return error(HttpStatus.CONFLICT, "AMOUNT_MISMATCH", ex.getMessage());
    }

    @ExceptionHandler(PartialPaymentException.class)
    public ResponseEntity<ErrorResponse> handlePartialPayment(PartialPaymentException ex) {
        return error(HttpStatus.CONFLICT, "PARTIAL_PAYMENT_NOT_CONFIRMED", ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Le fichier dépasse la taille maximale autorisée.");
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleServiceUnavailable(ServiceUnavailableException ex) {
        log.warn("Dependance indisponible : {}", ex.getCause() != null ? ex.getCause().toString() : "sans cause");
        return serviceUnavailable();
    }

    // Echec d'acquisition d'un verrou (lock_timeout) : jamais un 409, que le frontend lirait comme une erreur metier.
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleLockFailure(PessimisticLockingFailureException ex) {
        log.warn("Verrou non obtenu : {}", ex.getMessage());
        return serviceUnavailable();
    }

    public static ResponseEntity<ErrorResponse> serviceUnavailable() {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", ServiceUnavailableException.DEFAULT_MESSAGE);
    }

    public static ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}
