package org.vineflower.tools.minifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;

public class Minifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(Minifier.class);

    public static void main(String[] args) throws IOException {
        String latestVersion = Download.findLatestVersion();
        Path srcJar = Path.of("kotlin-metadata-" + latestVersion + ".jar");
        Path destJar = Path.of("metadata.jar");
        Files.deleteIfExists(destJar);

        if (!Files.exists(srcJar)) {
            LOGGER.info("Downloading latest Kotlin Metadata JAR: {}", latestVersion);
            Download.download(latestVersion, srcJar);
        }

        try (FileSystem inputFs = FileSystems.newFileSystem(srcJar)) {
            Path root = inputFs.getPath("/");
            try (FileSystem outputFs = FileSystems.newFileSystem(destJar, Map.of("create", "true"))) {
                Set<String> protobufExtensions = new HashSet<>();
                Files.walkFileTree(root, new ExtensionsFinder(protobufExtensions));
                Files.walkFileTree(root, new FileVisitor(outputFs.getPath("/"), root, protobufExtensions));
            }
        }
    }
}
