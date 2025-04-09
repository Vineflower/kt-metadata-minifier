package org.vineflower.tools.minifier.patch;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class ClassPatches {
    public static ClassPatch[] getPatches() {
        return new ClassPatch[] {
                new KotlinRemoval(),
                new BuilderRemoval(),
                new FlagsToInterface(),
        };
    }
}
