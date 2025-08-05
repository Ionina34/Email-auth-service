package effectivemobile.practice.security.exception;

public class TimeoutConfirmationCodeException extends RuntimeException{
    public TimeoutConfirmationCodeException(String message) {
        super(message);
    }
}
