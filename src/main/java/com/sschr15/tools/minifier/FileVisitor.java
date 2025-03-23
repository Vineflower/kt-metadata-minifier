package com.sschr15.tools.minifier;

import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.Interfaces;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

@NullMarked
public class FileVisitor extends SimpleFileVisitor<Path> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileVisitor.class);

    private final Path outputFs;
    private final Path root;

    public FileVisitor(Path outputFs, Path root) {
        this.outputFs = outputFs;
        this.root = root;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (!file.toString().endsWith(".class")) {
            if (file.toString().endsWith(".kotlin_module")) {
                LOGGER.info("Skipping Kotlin metadata file: {}", file);
                return FileVisitResult.CONTINUE;
            }

            LOGGER.info("Copying non-class file: {}", file);
            Path outputPath = outputFs.resolve(root.relativize(file).toString());
            Files.createDirectories(outputPath.getParent());
            Files.copy(file, outputPath);
            return FileVisitResult.CONTINUE;
        }

        if (file.toString().startsWith("/kotlin/metadata/internal/metadata/ProtoBuf") && file.toString().endsWith("$Builder.class")) {
            LOGGER.info("Skipping Builder class: {}", file);
            return FileVisitResult.CONTINUE;
        }

        ClassFile classFileHandler = ClassFile.of();
        ClassModel clazz = classFileHandler.parse(file);
        boolean removeBuilders = clazz.thisClass().name().equalsString("kotlin/metadata/internal/metadata/ProtoBuf");
        boolean[] kt = {false};
        byte[] bytes = classFileHandler.transformClass(clazz, (builder, el) -> {
            if (kt[0]) return;

            switch (el) {
                case RuntimeVisibleAnnotationsAttribute attr -> {
                    if (attr.annotations().stream()
                            .anyMatch(annotation -> annotation.className().equalsString("Lkotlin/Metadata;"))) {
                        kt[0] = true;
                        return;
                    }
                    builder.with(el);
                }
                case InnerClassesAttribute attr -> {
                    if (!removeBuilders) {
                        builder.with(el);
                        return;
                    }

                    List<InnerClassInfo> filteredClasses = attr.classes().stream()
                            .filter(innerClass -> !innerClass.innerName().map(utf8 -> utf8.equalsString("Builder")).orElse(false))
                            .filter(innerClass -> !innerClass.innerName().map(utf8 -> utf8.stringValue().endsWith("OrBuilder")).orElse(false))
                            .toList();
                    if (filteredClasses.isEmpty()) return;

                    InnerClassesAttribute newAttr = InnerClassesAttribute.of(filteredClasses);
                    builder.with(newAttr);
                }
                case MethodModel method -> {
                    String desc = method.methodTypeSymbol().returnType().descriptorString();
                    if (desc.substring(desc.indexOf(')') + 1).startsWith("Lkotlin/metadata/internal/metadata/ProtoBuf") && desc.endsWith("$Builder;")) {
                        return;
                    }
                    builder.with(el);
                }
                case Interfaces itfs -> {
                    List<ClassEntry> nonBuilderInterfaces = itfs.interfaces().stream().filter(entry -> !entry.name().stringValue().endsWith("OrBuilder")).toList();
                    Interfaces newItfs = Interfaces.of(nonBuilderInterfaces);
                    builder.with(newItfs);
                }
                default -> builder.with(el);
            }
        });

        // Universally skip Kotlin classes
        if (kt[0]) {
            LOGGER.debug("Skipping Kotlin class: {}", file);
            return FileVisitResult.CONTINUE;
        }

        LOGGER.info("Copying minified version of class: {}", file);
        Path outputPath = outputFs.resolve(root.relativize(file).toString());
        Files.createDirectories(outputPath.getParent());
        Files.write(outputPath, bytes);
        return FileVisitResult.CONTINUE;
    }
}
