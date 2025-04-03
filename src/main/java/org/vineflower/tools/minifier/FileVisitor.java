package org.vineflower.tools.minifier;

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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Set;

@NullMarked
public class FileVisitor extends SimpleFileVisitor<Path> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileVisitor.class);

    private final Path outputFs;
    private final Path root;
    private final Set<String> protobufExtensions;

    public FileVisitor(Path outputFs, Path root, Set<String> protobufExtensions) {
        this.outputFs = outputFs;
        this.root = root;
        this.protobufExtensions = protobufExtensions;
    }

    private boolean shouldIncludeInnerClass(InnerClassInfo innerClass) {
//        return !innerClass.innerName().map(utf8 -> {
//            if (protobufExtensions.stream().anyMatch(utf8.stringValue()::startsWith)) return false;
//            if (utf8.equalsString("Builder")) return true;
//            if (utf8.stringValue().endsWith("OrBuilder")) return true;
//
//            return false;
//        }).orElse(false);

        String name = innerClass.innerClass().asInternalName();
        if (protobufExtensions.stream().anyMatch(name::startsWith)) return true;
        if (name.endsWith("OrBuilder")) return false;
        if (name.endsWith("$Builder")) return false;
        return true;
    }

    private boolean shouldExcludeMethod(MethodModel method) {
        String desc = method.methodTypeSymbol().returnType().descriptorString();
        if (desc.length() == 1) return false;
        String returnType = desc.substring(desc.indexOf('L') + 1, desc.length() - 1);

        if (protobufExtensions.stream().anyMatch(returnType::startsWith)) return false;
        if (returnType.startsWith("kotlin/metadata/internal/metadata/ProtoBuf") && desc.endsWith("$Builder")) return true;

        return false;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Path outputPath = outputFs.resolve(root.relativize(file).toString());

        if (!file.toString().endsWith(".class")) {
            if (file.toString().endsWith(".kotlin_module")) {
                LOGGER.info("Skipping Kotlin metadata file: {}", file);
                return FileVisitResult.CONTINUE;
            }

            LOGGER.info("Copying non-class file: {}", file);
            Files.createDirectories(outputPath.getParent());
            Files.copy(file, outputPath);
            return FileVisitResult.CONTINUE;
        }

        boolean isBuilder = file.toString().startsWith("/kotlin/metadata/internal/metadata/") && file.toString().endsWith("$Builder.class");
        boolean isExempt = protobufExtensions.stream().anyMatch(file.toString().substring(1)::startsWith);

        if (isExempt) {
            LOGGER.info("Copying exempt-from-checks file: {}", file);
            Files.createDirectories(outputPath.getParent());
            Files.copy(file, outputPath);
            return FileVisitResult.CONTINUE;
        }

        if (isBuilder) {
            LOGGER.info("Skipping Builder class: {}", file);
            return FileVisitResult.CONTINUE;
        }

        ClassFile classFileHandler = ClassFile.of();
        ClassModel clazz = classFileHandler.parse(file);
        boolean removeBuilders = clazz.thisClass().name().equalsString("kotlin/metadata/internal/metadata/ProtoBuf");
        boolean[] skip = {false};
        byte[] bytes = classFileHandler.transformClass(clazz, (builder, el) -> {
            if (skip[0]) return;

            switch (el) {
                case RuntimeVisibleAnnotationsAttribute attr -> {
                    if (attr.annotations().stream()
                            .anyMatch(annotation -> annotation.className().equalsString("Lkotlin/Metadata;"))) {
                        skip[0] = true;
                        return;
                    }
                    builder.with(el);
                }
                case InnerClassesAttribute attr -> {
                    if (!removeBuilders || isExempt) {
                        builder.with(el);
                        return;
                    }

                    List<InnerClassInfo> filteredClasses = attr.classes().stream()
                            .filter(this::shouldIncludeInnerClass)
                            .toList();
                    if (filteredClasses.isEmpty()) return;

                    InnerClassesAttribute newAttr = InnerClassesAttribute.of(filteredClasses);
                    builder.with(newAttr);
                }
                case MethodModel method -> {
                    if (shouldExcludeMethod(method)) return;
                    builder.with(el);
                }
                case Interfaces itfs -> {
                    if (!removeBuilders || isExempt) {
                        builder.with(el);
                        return;
                    }

                    List<ClassEntry> nonBuilderInterfaces = itfs.interfaces().stream().filter(entry -> !entry.name().stringValue().endsWith("OrBuilder")).toList();
                    Interfaces newItfs = Interfaces.of(nonBuilderInterfaces);
                    builder.with(newItfs);
                }
                default -> builder.with(el);
            }
        });

        if (skip[0]) {
            LOGGER.debug("Skipping class file: {}", file);
            return FileVisitResult.CONTINUE;
        }

        LOGGER.info("Copying minified version of class: {}", file);
        Files.createDirectories(outputPath.getParent());
        Files.write(outputPath, bytes);
        return FileVisitResult.CONTINUE;
    }
}
