package org.vineflower.tools.minifier.patch;

import org.jspecify.annotations.Nullable;

import java.lang.classfile.*;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.util.HashSet;
import java.util.Set;

public class BuilderRemoval implements ClassPatch {
    private final Set<String> protobufExtensions = new HashSet<>();

    private boolean isExtension(String check) {
        return protobufExtensions.stream().anyMatch(check::startsWith);
    }

    private void parseTypeArg(Signature.TypeArg arg) {
        if (arg instanceof Signature.TypeArg.Bounded boundedArg) {
            Signature.ClassTypeSig classTypeSig = (Signature.ClassTypeSig) boundedArg.boundType();
            protobufExtensions.add(classTypeSig.className());
            if (!classTypeSig.typeArgs().isEmpty()) {
                for (Signature.TypeArg arg2 : classTypeSig.typeArgs()) {
                    parseTypeArg(arg2);
                }
            }
        }
    }

    private boolean shouldExcludeMethod(MethodModel method) {
        String desc = method.methodTypeSymbol().returnType().descriptorString();
        if (desc.length() == 1) return false;
        String returnType = desc.substring(desc.indexOf('L') + 1, desc.length() - 1);

        if (isExtension(returnType)) return false;
        if (isBuilder(returnType)) return true;

        return false;
    }

    private static boolean isBuilder(String className) {
        return className.endsWith("$Builder") || className.endsWith("OrBuilder");
    }

    @Override
    public void firstPass(ClassModel clazz) {
        for (FieldModel field : clazz.fields()) {
            if (!field.fieldType().equalsString("Lkotlin/metadata/internal/protobuf/GeneratedMessageLite$GeneratedExtension;")) continue;

            field.findAttribute(Attributes.signature()).ifPresent(sig -> {
                Signature.ClassTypeSig classTypeSig = (Signature.ClassTypeSig) sig.asTypeSignature();
                for (Signature.TypeArg arg : classTypeSig.typeArgs()) {
                    parseTypeArg(arg);
                }
            });
        }
    }

    @Override
    @Nullable
    public ClassTransform patch(ClassModel clazz) {
        String className = clazz.thisClass().name().stringValue();
        if (isExtension(className)) return ClassTransform.ACCEPT_ALL;
        if (!className.startsWith("kotlin/metadata/internal/metadata")) return ClassTransform.ACCEPT_ALL;

        if (isBuilder(className)) return null;

        return ClassTransform.dropping(el -> {
            if (el instanceof MethodModel method) {
                return shouldExcludeMethod(method);
            }
            return false;
        }).andThen((builder, el) -> {
            switch (el) {
                case InnerClassesAttribute attr -> builder.with(InnerClassesAttribute.of(
                        attr.classes().stream().filter(c -> {
                            String name = c.innerClass().asInternalName();
                            if (isExtension(name)) return true;
                            if (isBuilder(name)) return false;
                            return true;
                        }).toList()
                ));
                case Interfaces itfs -> builder.with(Interfaces.of(
                        itfs.interfaces().stream().filter(
                                entry -> isExtension(entry.asInternalName()) || !isBuilder(entry.asInternalName())
                        ).toList()
                ));
                default -> builder.with(el);
            }
        });
    }
}
