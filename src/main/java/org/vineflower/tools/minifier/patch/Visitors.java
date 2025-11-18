package org.vineflower.tools.minifier.patch;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Visitors {
    private static final ClassFile CLASS_FILE_HANDLER = ClassFile.of();

    public static class GatherData extends SimpleFileVisitor<Path> {
        public final Set<ClassDesc> ifaces = new HashSet<>();
        public final Map<ClassDesc, ClassDesc> hierarchy = new HashMap<>();

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (!file.toString().endsWith(".class")) return FileVisitResult.CONTINUE;

            ClassModel clazz = ClassFile.of().parse(file);
            if (clazz.flags().has(AccessFlag.INTERFACE)) {
                ifaces.add(ClassDesc.ofInternalName(Renamer.rename(clazz.thisClass().asInternalName())));
            }
            clazz.superclass().ifPresent(ce ->
                hierarchy.put(ClassDesc.ofInternalName(Renamer.rename(clazz.thisClass().asInternalName())),
                ClassDesc.ofInternalName(Renamer.rename(ce.asInternalName()))));

            return FileVisitResult.CONTINUE;
        }
    }

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
        private final FileSystem fs;
        private final ClassFile cf;

        public TransformPass(ClassHierarchyResolver resolver, ClassPatch[] patches, Path outputRoot, Path inputRoot, FileSystem fs) {
            this.patches = patches;
            this.outputRoot = outputRoot;
            this.inputRoot = inputRoot;
            this.fs = fs;

            this.cf = ClassFile.of(
                ClassFile.ClassHierarchyResolverOption.of(
                    desc -> {
                        ClassHierarchyResolver.ClassHierarchyInfo res = resolver.getClassInfo(desc);
                        if (res != null) {
                            return res;
                        }
                        // Inverse rename
                        desc = ClassDesc.ofDescriptor(desc.descriptorString().replace("org/vineflower/kt/", "kotlin/metadata/internal/"));
                        res = resolver.getClassInfo(desc);
                        if (res != null) {
                            return res;
                        }
                        return ClassHierarchyResolver.defaultResolver().getClassInfo(desc);
                    })
            );
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (!file.toString().endsWith(".class")) return FileVisitResult.CONTINUE;
            ClassModel clazz = this.cf.parse(file);

            ClassTransform transform = ClassTransform.ACCEPT_ALL;

            for (ClassPatch patch : patches) {
                ClassTransform patchTransform = patch.patch(clazz);
                if (patchTransform == null) return FileVisitResult.CONTINUE;

                if (patchTransform != ClassTransform.ACCEPT_ALL) {
                    transform = transform == ClassTransform.ACCEPT_ALL ? patchTransform : transform.andThen(patchTransform);
                }
            }

            ClassTransform finalTransform = transform;
            ConstantPoolBuilder cp = ConstantPoolBuilder.of();
            String name = clazz.thisClass().asInternalName();
            String newName = Renamer.rename(name);
            byte[] bytes = this.cf.build(Renamer.rename(cp, clazz.thisClass()), cp, cb -> cb.transform(clazz, finalTransform));

            Path outputPath = fs.getPath(newName + ".class");
            Files.createDirectories(outputPath.getParent());
            Files.write(outputPath, bytes);

            return FileVisitResult.CONTINUE;
        }
    }
}
