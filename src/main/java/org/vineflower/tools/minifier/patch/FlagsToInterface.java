package org.vineflower.tools.minifier.patch;

import org.jspecify.annotations.Nullable;

import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.util.ArrayList;
import java.util.List;

import static java.lang.classfile.ClassFile.*;
import static java.lang.reflect.AccessFlag.*;

public class FlagsToInterface implements ClassPatch {
    private static final String FLAGS_CLASS = "kotlin/metadata/internal/metadata/deserialization/Flags";

    @Override
    public @Nullable ClassTransform patch(ClassModel clazz) {
        String name = clazz.thisClass().asInternalName();

        if (!name.startsWith(FLAGS_CLASS)) return ClassTransform.ACCEPT_ALL;

        return switch (name) {
            case FLAGS_CLASS -> ClassTransform.dropping(
                    el -> el instanceof MethodModel mt && mt.methodName().equalsString("<init>")
            ).andThen((builder, el) -> {
                if (!(el instanceof InnerClassesAttribute attr)) {
                    builder.with(el);
                    return;
                }

                List<InnerClassInfo> innerClasses = new ArrayList<>();
                for (InnerClassInfo innerClass : attr.classes()) {
                    int flags = innerClass.flagsMask();
                    flags &= ~(ACC_PRIVATE | ACC_PROTECTED);
                    flags |= ACC_PUBLIC;
                    innerClasses.add(InnerClassInfo.of(innerClass.innerClass(), innerClass.outerClass(), innerClass.innerName(), flags));
                }

                builder.with(InnerClassesAttribute.of(innerClasses));
            }).andThen(ClassTransform.endHandler(builder -> builder.withFlags(PUBLIC, ABSTRACT, INTERFACE)));
            case FLAGS_CLASS + "$EnumLiteFlagField" -> ClassTransform.endHandler(builder -> builder.withFlags(PUBLIC));
            default -> ClassTransform.ACCEPT_ALL;
        };
    }
}
