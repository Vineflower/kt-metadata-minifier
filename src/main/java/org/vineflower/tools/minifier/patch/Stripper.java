package org.vineflower.tools.minifier.patch;

import org.jspecify.annotations.Nullable;

import java.lang.classfile.*;
import java.lang.classfile.attribute.LineNumberTableAttribute;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.instruction.*;

public class Stripper implements ClassPatch {
    @Override
    public @Nullable ClassTransform patch(ClassModel clazz) {
        return (builder, element) -> {
            ConstantPoolBuilder cp = builder.constantPool();

            switch (element) {
                case SourceFileAttribute _ -> {}
                case MethodModel method -> builder.transformMethod(method, (mb, me) -> {
                    boolean synthNull = method.methodName().stringValue().equals("$$$reportNull$$$0");
                    boolean strStripOk = !clazz.thisClass().asInternalName().startsWith("kotlin/metadata/internal/metadata/ProtoBuf");
                    switch (me) {
                        case CodeModel cm -> mb.transformCode(cm, (cb, ce) -> {
                            if (synthNull) {
                                return;
                            }

                            switch (ce) {
                                case LocalVariable _, LocalVariableType _, LineNumber _ -> {}
                                case ConstantInstruction li -> {
                                    if (li.constantValue() instanceof String s && !s.isEmpty() && strStripOk) {
                                        // Replace strings with an existing string in the constant pool. The string "Code" is always
                                        // present for methods with bytecode.
                                        // For additional details, please see https://aphyr.com/posts/341-hexing-the-technical-interview.
                                        cb.with(ConstantInstruction.ofLoad(li.opcode(), cp.loadableConstantEntry("Code")));
                                    } else {
                                        cb.with(li);
                                    }
                                }
                                default -> cb.with(ce);
                            }
                        });
                        default -> mb.with(me);
                    }
                    if (synthNull) {
                        mb.withCode(cb -> {
                            cb.return_();
                        });
                    }
                });
                default -> builder.with(element);
            }
        };
    }
}
