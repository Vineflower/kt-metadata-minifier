package org.vineflower.tools.minifier.patch;

import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class Visitors {
    private static final ClassFile CLASS_FILE_HANDLER = ClassFile.of();

    public static class FirstPass extends SimpleFileVisitor<Path> {
        private final ClassPatch[] patches;

        public FirstPass(ClassPatch[] patches) {
            this.patches = patches;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (!file.toString().endsWith(".class")) return FileVisitResult.CONTINUE;
            ClassModel clazz = CLASS_FILE_HANDLER.parse(file);
            for (ClassPatch patch : patches) {
                patch.firstPass(clazz);
            }
            return FileVisitResult.CONTINUE;
        }
    }

    public static class TransformPass extends SimpleFileVisitor<Path> {
        private final ClassPatch[] patches;
        private final Path outputRoot;
        private final Path inputRoot;

        public TransformPass(ClassPatch[] patches, Path outputRoot, Path inputRoot) {
            this.patches = patches;
            this.outputRoot = outputRoot;
            this.inputRoot = inputRoot;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (!file.toString().endsWith(".class")) return FileVisitResult.CONTINUE;
            Path outputPath = outputRoot.resolve(inputRoot.relativize(file).toString());
            ClassModel clazz = CLASS_FILE_HANDLER.parse(file);

            ClassTransform transform = ClassTransform.ACCEPT_ALL;

            for (ClassPatch patch : patches) {
                ClassTransform patchTransform = patch.patch(clazz);
                if (patchTransform == null) return FileVisitResult.CONTINUE;

                if (patchTransform != ClassTransform.ACCEPT_ALL) {
                    transform = transform == ClassTransform.ACCEPT_ALL ? patchTransform : transform.andThen(patchTransform);
                }
            }

            Files.createDirectories(outputPath.getParent());
            if (transform == ClassTransform.ACCEPT_ALL) {
                Files.copy(file, outputPath);
            } else {
                byte[] bytes = CLASS_FILE_HANDLER.transformClass(clazz, transform);
                Files.write(outputPath, bytes);
            }

            return FileVisitResult.CONTINUE;
        }
    }
}
