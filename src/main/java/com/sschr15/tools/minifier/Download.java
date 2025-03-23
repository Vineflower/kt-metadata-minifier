package com.sschr15.tools.minifier;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Download {
    private static final String BASE = "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-metadata-jvm";

    public static String findLatestVersion() {
        String urlString = BASE + "/maven-metadata.xml";
        URI url = URI.create(urlString);

        try {
            String text = new String(url.toURL().openStream().readAllBytes());
            Matcher matcher = Pattern.compile("<latest>(.+?)</latest>").matcher(text);
            if (!matcher.find()) {
                throw new IllegalStateException("Failed to find latest version in " + urlString);
            }
            return matcher.group(1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void download(String version, Path output) {
        String urlString = BASE + "/" + version + "/kotlin-metadata-jvm-" + version + ".jar";
        try {
            URI url = URI.create(urlString);
            try (var inputStream = url.toURL().openStream()) {
                Files.copy(inputStream, output);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}