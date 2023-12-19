package hu.garaba.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class FileDownloader {
    private static final System.Logger LOGGER = System.getLogger(FileDownloader.class.getCanonicalName());

    public static Path downloadFile(URI uri) throws IOException, InterruptedException {
        Path tempFile;
        HttpResponse<Path> response;
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            tempFile = Files.createTempFile("downloaded-voice", ".oga");
            response = client.send(request, HttpResponse.BodyHandlers.ofFile(tempFile));
        }
        return Files.move(tempFile, response.body(), StandardCopyOption.REPLACE_EXISTING);
    }
}
