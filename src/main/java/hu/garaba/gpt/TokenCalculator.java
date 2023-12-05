package hu.garaba.gpt;

public class TokenCalculator {
    public static long tokenCount(String input) {
        return input.length() / 4;
    }

    public static long image(boolean highDetail, int width, int height) {
        if (!highDetail)
            return 85;

        if (height < width) {
            return image(highDetail, height, width);
        }

        if (height > 2048) {
            width = (int) Math.round(width * (2048.0 / height));
            height = 2048;
        }

        height = (int) Math.round(height * (768.0 / width));
        width = 768;

        int tilesWidth = (width + 511) / 512;
        int tilesHeight = (height + 511) / 512;
        int totalTiles = tilesWidth * tilesHeight;

        return 85 + 170 * totalTiles;
    }
}
