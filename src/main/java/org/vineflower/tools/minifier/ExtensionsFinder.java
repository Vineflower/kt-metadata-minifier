package org.vineflower.tools.minifier;

import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.lang.classfile.*;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Set;

@NullMarked
public class ExtensionsFinder extends SimpleFileVisitor<Path> {
    private final Set<String> protobufExtensions;

    public ExtensionsFinder(Set<String> protobufExtensions) {
        this.protobufExtensions = protobufExtensions;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (!file.toString().endsWith(".class")) return FileVisitResult.CONTINUE;
        ClassModel clazz = ClassFile.of().parse(file);

        for (FieldModel field : clazz.fields()) {
            if (!field.fieldType().equalsString("Lkotlin/metadata/internal/protobuf/GeneratedMessageLite$GeneratedExtension;")) continue;

            field.findAttribute(Attributes.signature()).ifPresent(sig -> {
                Signature.ClassTypeSig classTypeSig = (Signature.ClassTypeSig) sig.asTypeSignature();
                for (Signature.TypeArg arg : classTypeSig.typeArgs()) {
                    if (arg instanceof Signature.TypeArg.Bounded boundArg) {
                        Signature.ClassTypeSig actualType = (Signature.ClassTypeSig) boundArg.boundType();
                        protobufExtensions.add(actualType.className());
                        if (!actualType.typeArgs().isEmpty()) {
                            // Probably List or Map or something
                            for (Signature.TypeArg arg2 : actualType.typeArgs()) {
                                if (arg2 instanceof Signature.TypeArg.Bounded boundArg2) {
                                    Signature.ClassTypeSig actualType2 = (Signature.ClassTypeSig) boundArg2.boundType();
                                    protobufExtensions.add(actualType2.className());
                                }
                            }
                        }
                    }
                }
            });
        }

        return FileVisitResult.CONTINUE;
    }
}
