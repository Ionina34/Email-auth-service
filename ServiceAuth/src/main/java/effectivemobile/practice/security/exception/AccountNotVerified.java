package effectivemobile.practice.security.exception;

public class AccountNotVerified extends RuntimeException{
    public AccountNotVerified(String message) {
        super(message);
    }
}
