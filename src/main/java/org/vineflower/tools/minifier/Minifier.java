package org.vineflower.tools.minifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vineflower.tools.minifier.patch.ClassPatch;
import org.vineflower.tools.minifier.patch.ClassPatches;
import org.vineflower.tools.minifier.patch.Visitors;

import java.lang.classfile.ClassHierarchyResolver;
import java.lang.constant.ClassDesc;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

public class Minifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(Minifier.class);

    public static void main(String[] args) throws IOException {
        String version = args.length > 0 ? args[0] : Download.findLatestVersion();
        Path srcJar = Path.of("kotlin-metadata-" + version + ".jar");
        Path destJar = Path.of("metadata.jar");
        Files.deleteIfExists(destJar);

        if (!Files.exists(srcJar)) {
            LOGGER.info("Downloading Kotlin Metadata JAR: {}", version);
            Download.download(version, srcJar);
        }

        try (FileSystem inputFs = FileSystems.newFileSystem(srcJar)) {
            Path root = inputFs.getPath("/");

            Visitors.GatherData data = new Visitors.GatherData();
            Files.walkFileTree(root, data);
            ClassHierarchyResolver resolver = ClassHierarchyResolver.of(data.ifaces, data.hierarchy);

            try (FileSystem outputFs = FileSystems.newFileSystem(destJar, Map.of("create", "true"))) {
                ClassPatch[] patches = ClassPatches.getPatches();
                Files.walkFileTree(root, new Visitors.FirstPass(patches));
                Files.walkFileTree(root, new Visitors.TransformPass(resolver, patches, outputFs.getPath("/"), root, outputFs));
            }
        }
    }
}
