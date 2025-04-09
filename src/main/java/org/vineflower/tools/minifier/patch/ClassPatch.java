package org.vineflower.tools.minifier.patch;

import org.jspecify.annotations.Nullable;

import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;

public interface ClassPatch {
    /**
     * Always called with every class to allow for state initialization.
     */
    default void firstPass(ClassModel clazz) {}

    /**
     * @return a {@link ClassTransform} instance to transform, {@link ClassTransform#ACCEPT_ALL ACCEPT_ALL}
     * to skip transforming by this patch, and {@code null} to remove the class.
     */
    @Nullable ClassTransform patch(ClassModel clazz);
}
