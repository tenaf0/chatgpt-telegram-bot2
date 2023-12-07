package hu.garaba.gpt;

public class GPTException extends Exception {
    private final String userMessage;

    public GPTException(String message, String userMessage) {
        super(message);

        this.userMessage = userMessage;
    }

    public String userMessage() {
        return userMessage;
    }
}
