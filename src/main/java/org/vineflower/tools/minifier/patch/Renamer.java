package org.vineflower.tools.minifier.patch;

import org.jspecify.annotations.Nullable;

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.classfile.instruction.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

public class Renamer implements ClassPatch {
    @Override
    public @Nullable ClassTransform patch(ClassModel clazz) {
        return (builder, element) -> {
            ConstantPoolBuilder cp = builder.constantPool();

            switch (element) {
                case Superclass sc -> builder.withSuperclass(rename(cp, sc.superclassEntry()));
                case Interfaces ifs -> builder.withInterfaces(ifs.interfaces().stream()
                        .map(i -> rename(cp, i)).toList());
                case InnerClassesAttribute inn -> builder.with(InnerClassesAttribute.of(
                        inn.classes().stream().map(i -> InnerClassInfo.of(
                                        rename(cp, i.innerClass()),
                                        i.outerClass().map(j -> rename(cp, j)),
                                        i.innerName(),
                                        i.flagsMask()
                                )).toList()
                ));
                case SignatureAttribute sig -> builder.with(SignatureAttribute.of(renameGenSym(cp, sig.signature())));
                case FieldModel f -> builder.withField(f.fieldName(), rename(cp, f.fieldType()), ff -> {
                    ff.transform(f, (FieldTransform) (fb, fe) -> {
                        switch (fe) {
                            case SignatureAttribute sig -> fb.with(SignatureAttribute.of(renameGenSym(cp, sig.signature())));
                            default -> fb.with(fe);
                        }
                    });
                });
                case MethodModel m -> builder.withMethod(m.methodName(), renameMethodDesc(cp, m.methodType()), m.flags().flagsMask(), (mm) -> {
                    mm.transform(m, (MethodTransform) (mb, me) -> {
                        switch (me) {
                            case SignatureAttribute sig -> mb.with(SignatureAttribute.of(renameGenSym(cp, sig.signature())));
                            case ExceptionsAttribute ex -> mb.with(ExceptionsAttribute.of(ex.exceptions().stream().map(i -> rename(cp, i)).toList()));
                            default -> mb.with(me);
                        }
                    });

                    m.code().ifPresent(cm -> mm.transformCode(cm, (c, e) -> {
                        // Remap instructions with new names
                        switch (e) {
                            case FieldInstruction fi -> c.with(FieldInstruction.of(fi.opcode(), rename(cp, fi.owner()), fi.name(), rename(cp, fi.type())));
                            case InvokeInstruction ii -> c.with(InvokeInstruction.of(ii.opcode(), rename(cp, ii.owner()), ii.name(),
                                    renameMethodDesc(cp, ii.type()), ii.isInterface()));
                            case NewObjectInstruction ni -> c.with(NewObjectInstruction.of(rename(cp, ni.className())));
                            case NewReferenceArrayInstruction ni -> c.with(NewReferenceArrayInstruction.of(rename(cp, ni.componentType())));
                            case TypeCheckInstruction ci -> c.with(TypeCheckInstruction.of(ci.opcode(), rename(cp, ci.type())));
                            case LocalVariable lvt -> c.with(LocalVariable.of(lvt.slot(), lvt.name(), rename(cp, lvt.type()), lvt.startScope(), lvt.endScope()));
                            case LocalVariableType lvt -> c.with(LocalVariableType.of(lvt.slot(), lvt.name(), renameGenSym(cp, lvt.signature()), lvt.startScope(), lvt.endScope()));
                            case ExceptionCatch ex -> c.with(ExceptionCatch.of(ex.handler(), ex.tryStart(), ex.tryEnd(), ex.catchType().map(type -> rename(cp, type))));
                            case ConstantInstruction ldc -> {
                                if (ldc.constantValue() instanceof ClassDesc desc) {
                                    String newName = rename(desc.descriptorString());
                                    if (desc.descriptorString().equals(newName)) {
                                        c.with(e);
                                    } else {
                                        ConstantInstruction.LoadConstantInstruction e1 = ConstantInstruction.ofLoad(ldc.opcode(), cp.loadableConstantEntry(ClassDesc.ofDescriptor(newName)));
                                        c.with(e1);
                                    }
                                } else {
                                    c.with(e);
                                }
                            }
                            default -> c.with(e);
                        }}));
                    }
                );
                default -> builder.with(element);
            }
        };
    }

    // TODO: this is a mess! clean it up!

    public static String rename(String in) {
        in = in.replaceAll("kotlin/metadata/internal/", "org/vineflower/kt/");
        return in.replaceAll("kotlin/metadata/jvm/", "org/vineflower/kt/");
    }

    private Utf8Entry renameGenSym(ConstantPoolBuilder cp, Utf8Entry ce) {
        String name = ce.stringValue();
        String newName = rename(name);
        if (name.equals(newName)) {
            return ce;
        }
        return cp.utf8Entry(newName);
    }

    private Utf8Entry renameMethodDesc(ConstantPoolBuilder cp, Utf8Entry ce) {
        String name = ce.stringValue();
        String newName = rename(name);
        if (name.equals(newName)) {
            return ce;
        }
        return cp.utf8Entry(MethodTypeDesc.ofDescriptor(newName));
    }

    private static Utf8Entry rename(ConstantPoolBuilder cp, Utf8Entry ce) {
        String name = ce.stringValue();
        String newName = rename(name);
        if (name.equals(newName)) {
            return ce;
        }
        // strip ;
        return cp.utf8Entry(ClassDesc.ofDescriptor(newName));
    }

    public static ClassEntry rename(ConstantPoolBuilder cp, ClassEntry ce) {
        String name = ce.asInternalName();
        String newName = rename(name);
        if (name.equals(newName)) {
            return ce;
        }
        // girl how
        if (newName.endsWith(";")) {
            return cp.classEntry(ClassDesc.ofDescriptor(newName));
        }
        return cp.classEntry(ClassDesc.ofInternalName(newName));
    }
}
