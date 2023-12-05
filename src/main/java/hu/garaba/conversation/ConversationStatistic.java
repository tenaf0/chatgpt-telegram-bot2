package hu.garaba.conversation;

import java.util.concurrent.atomic.AtomicLong;

public class ConversationStatistic {
    private final AtomicLong input = new AtomicLong();
    private final AtomicLong output = new AtomicLong();

    public void add(long input, long output) {
        this.input.addAndGet(input);
        this.output.addAndGet(output);
    }

    public record CountPair(long input, long output) {}
    public CountPair getAndClear() {
        return new CountPair(input.getAndSet(0), output.getAndSet(0));
    }
}
