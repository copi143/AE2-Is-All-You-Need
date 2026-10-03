package com.google.gson.internal.bind;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 为单个 POJO 生成专用 {@code TypeAdapter} 子类字节码：
 * - 字段访问：MethodHandle.invokeExact（失败时由调用方降级为 Field + GsonFastPath.fget/fset）
 * - write：字段名字面量 + 预构建的（可能包过 RuntimeTypeWrapper 的）子适配器，无 Map 遍历
 * - read：字段名 hashCode lookupswitch + equals 验证，无 HashMap 查找
 */
final class FastAdapterGenerator implements Opcodes {
    private static final String SUPER = "com/google/gson/TypeAdapter";
    private static final String REFLECTIVE_SUPER = "com/google/gson/internal/bind/FastReflectiveAdapter";
    private static final String HELPER = "com/google/gson/internal/bind/GsonFastPath";
    private static final String MH = "java/lang/invoke/MethodHandle";
    private static final String FIELD = "java/lang/reflect/Field";
    private static final String READER = "com/google/gson/stream/JsonReader";
    private static final String WRITER = "com/google/gson/stream/JsonWriter";
    private static final String TOKEN = "com/google/gson/stream/JsonToken";
    private static final String OBJ_CTOR = "com/google/gson/internal/ObjectConstructor";
    private static final String OBJ_CTOR_DESC = "Lcom/google/gson/internal/ObjectConstructor;";
    private static final String ADAPTER_DESC = "Lcom/google/gson/TypeAdapter;";

    private FastAdapterGenerator() {
    }

    static byte[] generate(Class<?> raw, List<FastAdapterEntry> writes, List<FastAdapterEntry> reads) {
        String simple = raw.getSimpleName().replaceAll("[^A-Za-z0-9_$]", "_");
        if (simple.isEmpty()) simple = "Anon";
        String name = "com/google/gson/internal/bind/FastReflectiveAdapter$" + simple;

        ClassWriter cw = new FrameSafeClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(V17, ACC_FINAL | ACC_SUPER, name, null, REFLECTIVE_SUPER, null);

        cw.visitField(ACC_PRIVATE | ACC_FINAL, "ctor", OBJ_CTOR_DESC, null, null).visitEnd();
        for (int i = 0; i < writes.size(); i++) {
            FastAdapterEntry e = writes.get(i);
            cw.visitField(ACC_PRIVATE | ACC_FINAL, "wAcc" + i, accDesc(e), null, null).visitEnd();
            cw.visitField(ACC_PRIVATE | ACC_FINAL, "wAd" + i, ADAPTER_DESC, null, null).visitEnd();
        }
        for (int i = 0; i < reads.size(); i++) {
            FastAdapterEntry e = reads.get(i);
            cw.visitField(ACC_PRIVATE | ACC_FINAL, "rAcc" + i, accDesc(e), null, null).visitEnd();
            cw.visitField(ACC_PRIVATE | ACC_FINAL, "rAd" + i, ADAPTER_DESC, null, null).visitEnd();
        }

        genConstructor(cw, name, writes, reads);
        genWrite(cw, name, writes);
        genRead(cw, name, reads);

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static String accDesc(FastAdapterEntry e) {
        return e.methodHandle ? "Ljava/lang/invoke/MethodHandle;" : "Ljava/lang/reflect/Field;";
    }

    private static void genConstructor(ClassWriter cw, String name, List<FastAdapterEntry> writes, List<FastAdapterEntry> reads) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>",
                "(" + OBJ_CTOR_DESC + "[Ljava/lang/Object;[Ljava/lang/Object;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, REFLECTIVE_SUPER, "<init>", "()V", false);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitFieldInsn(PUTFIELD, name, "ctor", OBJ_CTOR_DESC);
        for (int i = 0; i < writes.size(); i++) {
            unpack(mv, name, 2, "wAcc" + i, accDesc(writes.get(i)), writes.get(i).dataIndex);
            unpack(mv, name, 2, "wAd" + i, ADAPTER_DESC, writes.get(i).dataIndex + 1);
        }
        for (int i = 0; i < reads.size(); i++) {
            unpack(mv, name, 3, "rAcc" + i, accDesc(reads.get(i)), reads.get(i).dataIndex);
            unpack(mv, name, 3, "rAd" + i, ADAPTER_DESC, reads.get(i).dataIndex + 1);
        }
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void unpack(MethodVisitor mv, String owner, int arrLocal, String field, String desc, int index) {
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, arrLocal);
        mv.visitLdcInsn(index);
        mv.visitInsn(AALOAD);
        mv.visitTypeInsn(CHECKCAST, Type.getType(desc).getInternalName());
        mv.visitFieldInsn(PUTFIELD, owner, field, desc);
    }

    private static void genWrite(ClassWriter cw, String name, List<FastAdapterEntry> writes) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "write",
                "(L" + WRITER + ";Ljava/lang/Object;)V", null, new String[]{"java/io/IOException"});
        mv.visitCode();
        Label nonNull = new Label();
        mv.visitVarInsn(ALOAD, 2);
        mv.visitJumpInsn(IFNONNULL, nonNull);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, WRITER, "nullValue", "()L" + WRITER + ";", false);
        mv.visitInsn(POP);
        mv.visitInsn(RETURN);
        mv.visitLabel(nonNull);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, WRITER, "beginObject", "()L" + WRITER + ";", false);
        mv.visitInsn(POP);
        for (int i = 0; i < writes.size(); i++) {
            FastAdapterEntry e = writes.get(i);
            Label skip = new Label();
            if (e.methodHandle) {
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, name, "wAcc" + i, accDesc(e));
                mv.visitVarInsn(ALOAD, 2);
                mv.visitMethodInsn(INVOKEVIRTUAL, MH, "invokeExact",
                        "(Ljava/lang/Object;)Ljava/lang/Object;", false);
            } else {
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, name, "wAcc" + i, accDesc(e));
                mv.visitVarInsn(ALOAD, 2);
                mv.visitMethodInsn(INVOKESTATIC, HELPER, "fget",
                        "(L" + FIELD + ";Ljava/lang/Object;)Ljava/lang/Object;", false);
            }
            mv.visitVarInsn(ASTORE, 3);
            mv.visitVarInsn(ALOAD, 3);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitJumpInsn(IF_ACMPEQ, skip);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitLdcInsn(e.jsonName);
            mv.visitMethodInsn(INVOKEVIRTUAL, WRITER, "name", "(Ljava/lang/String;)L" + WRITER + ";", false);
            mv.visitInsn(POP);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, name, "wAd" + i, ADAPTER_DESC);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ALOAD, 3);
            mv.visitMethodInsn(INVOKEVIRTUAL, SUPER, "write", "(L" + WRITER + ";Ljava/lang/Object;)V", false);
            mv.visitLabel(skip);
        }
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, WRITER, "endObject", "()L" + WRITER + ";", false);
        mv.visitInsn(POP);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void genRead(ClassWriter cw, String name, List<FastAdapterEntry> reads) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "read",
                "(L" + READER + ";)Ljava/lang/Object;", null, new String[]{"java/io/IOException"});
        mv.visitCode();

        Label body = new Label();
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, READER, "peek", "()L" + TOKEN + ";", false);
        mv.visitFieldInsn(GETSTATIC, TOKEN, "NULL", "L" + TOKEN + ";");
        mv.visitJumpInsn(IF_ACMPNE, body);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, READER, "nextNull", "()V", false);
        mv.visitInsn(ACONST_NULL);
        mv.visitInsn(ARETURN);

        mv.visitLabel(body);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, name, "ctor", OBJ_CTOR_DESC);
        mv.visitMethodInsn(INVOKEINTERFACE, OBJ_CTOR, "construct", "()Ljava/lang/Object;", true);
        mv.visitVarInsn(ASTORE, 2);

        Label tryStart = new Label();
        Label tryEnd = new Label();
        Label handler = new Label();
        Label loop = new Label();
        Label loopEnd = new Label();
        Label skipVal = new Label();
        Label cont = new Label();

        mv.visitLabel(tryStart);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, READER, "beginObject", "()V", false);

        mv.visitLabel(loop);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, READER, "hasNext", "()Z", false);
        mv.visitJumpInsn(IFEQ, loopEnd);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, READER, "nextName", "()Ljava/lang/String;", false);
        mv.visitVarInsn(ASTORE, 3);

        if (reads.isEmpty()) {
            mv.visitJumpInsn(GOTO, skipVal);
        } else {
            TreeMap<Integer, List<Integer>> buckets = new TreeMap<>();
            for (int i = 0; i < reads.size(); i++) {
                buckets.computeIfAbsent(reads.get(i).jsonName.hashCode(), k -> new ArrayList<>()).add(i);
            }
            int[] keys = new int[buckets.size()];
            Label[] labels = new Label[buckets.size()];
            int k = 0;
            for (Map.Entry<Integer, List<Integer>> b : buckets.entrySet()) {
                keys[k] = b.getKey();
                labels[k] = new Label();
                k++;
            }
            mv.visitVarInsn(ALOAD, 3);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
            mv.visitLookupSwitchInsn(skipVal, keys, labels);

            k = 0;
            for (Map.Entry<Integer, List<Integer>> b : buckets.entrySet()) {
                mv.visitLabel(labels[k++]);
                List<Integer> bucket = b.getValue();
                for (int bi = 0; bi < bucket.size(); bi++) {
                    int i = bucket.get(bi);
                    FastAdapterEntry e = reads.get(i);
                    Label next = new Label();
                    mv.visitVarInsn(ALOAD, 3);
                    mv.visitLdcInsn(e.jsonName);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
                    mv.visitJumpInsn(IFEQ, next);

                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, name, "rAd" + i, ADAPTER_DESC);
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, SUPER, "read", "(L" + READER + ";)Ljava/lang/Object;", false);
                    mv.visitVarInsn(ASTORE, 4);
                    if (e.nullSkip) {
                        mv.visitVarInsn(ALOAD, 4);
                        mv.visitJumpInsn(IFNULL, cont);
                    }
                    if (e.methodHandle) {
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(GETFIELD, name, "rAcc" + i, accDesc(e));
                        mv.visitVarInsn(ALOAD, 2);
                        mv.visitVarInsn(ALOAD, 4);
                        mv.visitMethodInsn(INVOKEVIRTUAL, MH, "invokeExact",
                                "(Ljava/lang/Object;Ljava/lang/Object;)V", false);
                    } else {
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(GETFIELD, name, "rAcc" + i, accDesc(e));
                        mv.visitVarInsn(ALOAD, 2);
                        mv.visitVarInsn(ALOAD, 4);
                        mv.visitMethodInsn(INVOKESTATIC, HELPER, "fset",
                                "(L" + FIELD + ";Ljava/lang/Object;Ljava/lang/Object;)V", false);
                    }
                    mv.visitJumpInsn(GOTO, cont);
                    mv.visitLabel(next);
                }
                mv.visitJumpInsn(GOTO, skipVal);
            }
        }

        mv.visitLabel(skipVal);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, READER, "skipValue", "()V", false);
        mv.visitLabel(cont);
        mv.visitJumpInsn(GOTO, loop);

        mv.visitLabel(loopEnd);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, READER, "endObject", "()V", false);
        mv.visitLabel(tryEnd);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitInsn(ARETURN);

        mv.visitLabel(handler);
        mv.visitVarInsn(ASTORE, 5);
        mv.visitTypeInsn(NEW, "com/google/gson/JsonSyntaxException");
        mv.visitInsn(DUP);
        mv.visitVarInsn(ALOAD, 5);
        mv.visitMethodInsn(INVOKESPECIAL, "com/google/gson/JsonSyntaxException", "<init>", "(Ljava/lang/Throwable;)V", false);
        mv.visitInsn(ATHROW);

        mv.visitTryCatchBlock(tryStart, tryEnd, handler, "java/lang/IllegalStateException");
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

}
