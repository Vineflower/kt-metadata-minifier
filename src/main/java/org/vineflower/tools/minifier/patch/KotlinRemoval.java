package org.vineflower.tools.minifier.patch;

import org.jspecify.annotations.Nullable;

import java.lang.classfile.Annotation;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;

public class KotlinRemoval implements ClassPatch {
    @Override
    @Nullable
    public ClassTransform patch(ClassModel clazz) {
        RuntimeVisibleAnnotationsAttribute attr = clazz.findAttribute(Attributes.runtimeVisibleAnnotations()).orElse(null);
        if (attr == null) return ClassTransform.ACCEPT_ALL;
        for (Annotation annotation : attr.annotations()) {
            if (annotation.className().equalsString("Lkotlin/Metadata;")) return null;
        }
        return ClassTransform.ACCEPT_ALL;
    }
}
