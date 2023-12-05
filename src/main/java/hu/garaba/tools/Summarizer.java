package hu.garaba.tools;

import hu.garaba.Session;
import hu.garaba.gpt.Model;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Summarizer {
    private static final System.Logger LOGGER = System.getLogger(Summarizer.class.getCanonicalName());

    public static boolean isValidURL(String url) {
        if (!url.contains(".")) {
            return false;
        }

        try {
            new URI(url);

            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static String collectOutput(Process extractorProcess) throws IOException {
        try (BufferedReader bufferedReader = extractorProcess.inputReader(StandardCharsets.UTF_8);
             BufferedReader errorReader = extractorProcess.errorReader()) {
            String output = bufferedReader.lines().collect(Collectors.joining("\n"));
            errorReader.lines().forEach(l -> LOGGER.log(System.Logger.Level.DEBUG, l));
            return output;
        }
    }

    public static String extractArticle(URI uri) throws IOException {
        LOGGER.log(System.Logger.Level.DEBUG, "Starting extraction of article at " + uri.getHost());
        Process extractorProcess = new ProcessBuilder("trafilatura", "-u", uri.toString())
                .start();

        return collectOutput(extractorProcess);
    }

    private static final Pattern videoIdPattern = Pattern.compile("v=([^&]+)");

    public static String extractVideoTranscript(URI youtubeUrl) throws IOException {
        LOGGER.log(System.Logger.Level.DEBUG, "Starting extraction of video");

        Matcher matcher = videoIdPattern.matcher(youtubeUrl.getQuery());
        if (!matcher.find()) {
            throw new IllegalArgumentException("Video url " + youtubeUrl + "'s video id could not be extracted from URL");
        }
        String videoId = matcher.group(1);
        Process extractorProcess = new ProcessBuilder("youtube_transcript_api", videoId, "--format", "text")
                .start();

        return collectOutput(extractorProcess);
    }

    public static void summarizeText(Session session, String text) {
        if (text.isBlank()) {
            throw new IllegalArgumentException("Got empty text to summarize");
        }

        session.initConversation(Model.GPT4, "You are to provide a comprehensive summary of the given text. " +
                "The summary should cover all the key points and main ideas presented in the original text, " +
                "while also condensing the information into a concise and easy-to-understand format. " +
                "Please ensure that the summary includes relevant details and examples that support the main ideas, " +
                "while avoiding any unnecessary information or repetition. The length of the summary should be " +
                "appropriate for the length and complexity of the original text, providing a clear " +
                "and accurate overview without omitting any important information.");
        session.addMessage(text);
    }
}
