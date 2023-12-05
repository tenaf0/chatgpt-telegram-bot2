package hu.garaba.gpt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenCalculatorTest {
    @Test
    public void imageCostTest() {
        assertEquals(85, TokenCalculator.image(false, 4096, 8192));
        assertEquals(765, TokenCalculator.image(true, 1024, 1024));
        assertEquals(1105, TokenCalculator.image(true, 2048, 4096));
    }
}