package kaptor.a2s.compiler

import kaptor.a2s.ir.*
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.*

/**
 * 类生成器：生成事件类与脚本类的字节码。
 */
class A2sClassCompiler(private val symbols: A2sSymbolTable) {

    private val exprCompiler = A2sExprCompiler(symbols)
    private val stmtCompiler = A2sStmtCompiler(symbols, exprCompiler)

    /** 生成事件类字节码。 */
    fun generateEventClass(decl: A2sEventDecl): ByteArray {
        val internalName = A2sNames.eventClass(decl.name)
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)

        cw.visit(V17, ACC_PUBLIC or ACC_SUPER, internalName, null, TYPE_EVENT_OBJECT, null)

        // 字段
        val fieldDescs = mutableMapOf<String, String>()
        for (param in decl.params) {
            val desc = A2sTypeCodegen.boxedDescriptor(param.type)
            fieldDescs[param.name] = desc
            cw.visitField(ACC_PUBLIC or ACC_FINAL, param.name, desc, null, null).visitEnd()
        }

        // 构造器 (Object...)
        generateEventConstructor(cw, internalName, decl.params)

        // getter
        for (param in decl.params) {
            generateEventGetter(cw, internalName, param, fieldDescs[param.name]!!)
        }

        // 方法
        for (method in decl.methods) {
            generateEventMethod(cw, internalName, decl, method)
        }

        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun generateEventConstructor(cw: ClassWriter, internalName: String, params: List<A2sParam>) {
        val desc = "(" + params.joinToString("") { A2sTypeCodegen.boxedDescriptor(it.type) } + ")V"
        val mv = cw.visitMethod(ACC_PUBLIC, "<init>", desc, null, null)
        mv.visitCode()
        mv.visitVarInsn(ALOAD, 0)
        mv.visitMethodInsn(INVOKESPECIAL, TYPE_EVENT_OBJECT, "<init>", "()V", false)
        for ((i, param) in params.withIndex()) {
            mv.visitVarInsn(ALOAD, 0)
            mv.visitVarInsn(ALOAD, i + 1)
            mv.visitFieldInsn(PUTFIELD, internalName, param.name, A2sTypeCodegen.boxedDescriptor(param.type))
        }
        mv.visitInsn(RETURN)
        mv.visitMaxs(2, params.size + 1)
        mv.visitEnd()
    }

    private fun generateEventGetter(cw: ClassWriter, internalName: String, param: A2sParam, desc: String) {
        val getterName = "get${param.name.replaceFirstChar { it.uppercase() }}"
        val mv = cw.visitMethod(ACC_PUBLIC, getterName, "()Ljava/lang/Object;", null, null)
        mv.visitCode()
        mv.visitVarInsn(ALOAD, 0)
        mv.visitFieldInsn(GETFIELD, internalName, param.name, desc)
        mv.visitInsn(ARETURN)
        mv.visitMaxs(1, 1)
        mv.visitEnd()
    }

    private fun generateEventMethod(cw: ClassWriter, internalName: String, decl: A2sEventDecl, method: A2sFunctionDecl) {
        val paramDescs = method.params.joinToString("") { A2sTypeCodegen.boxedDescriptor(it.type) }
        val returnDesc = method.returnType?.let { A2sTypeCodegen.boxedDescriptor(it) } ?: "Ljava/lang/Object;"
        val desc = "($paramDescs)$returnDesc"
        val mv = cw.visitMethod(ACC_PUBLIC, method.name, desc, null, null)
        mv.visitCode()

        val ctx = A2sCompileContext(mv, symbols, internalName, isStatic = false)
        // 声明参数
        for ((i, p) in method.params.withIndex()) {
            ctx.declareLocal(p.name, p.type)
        }
        // 注入事件字段（方法内可直接访问事件字段）
        ctx.eventFields = decl.params.associate { it.name to it.type }

        when (val body = method.body) {
            is A2sFunctionBody.Expr -> {
                exprCompiler.compile(ctx, body.expr)
                mv.visitInsn(ARETURN)
            }

            is A2sFunctionBody.Block -> {
                for (stmt in body.statements) {
                    stmtCompiler.compile(ctx, stmt)
                }
                mv.visitInsn(ACONST_NULL)
                mv.visitInsn(ARETURN)
            }
        }
        ctx.finish()
        mv.visitEnd()
    }

    /** 生成脚本类字节码。 */
    fun generateScriptClass(
        index: Int,
        script: A2sScriptFile,
    ): ByteArray {
        val internalName = A2sNames.scriptClass(index)
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)

        cw.visit(V17, ACC_PUBLIC or ACC_SUPER, internalName, null, "java/lang/Object", null)

        // 顶层 val/var → 字段
        for (v in script.topLevelVars) {
            val desc = v.type?.let { A2sTypeCodegen.boxedDescriptor(it) } ?: "Ljava/lang/Object;"
            cw.visitField(ACC_PUBLIC, v.name, desc, null, null).visitEnd()
        }

        // 构造器：初始化顶层变量
        generateScriptConstructor(cw, internalName, script)

        // fun → 方法
        for (f in script.functions) {
            generateScriptFunction(cw, internalName, f)
        }

        // handler → 方法 handle_{eventType} / before_ / after_
        for (h in script.handlers) {
            generateScriptHandler(cw, internalName, h)
        }

        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun generateScriptConstructor(cw: ClassWriter, internalName: String, script: A2sScriptFile) {
        val mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null)
        mv.visitCode()
        mv.visitVarInsn(ALOAD, 0)
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)

        if (script.topLevelVars.isNotEmpty()) {
            val ctx = A2sCompileContext(mv, symbols, internalName, isStatic = false)
            for (v in script.topLevelVars) {
                val type = v.type ?: v.initializer?.let { symbols.inferType(it, ctx.localTypes()) } ?: A2sUnknown
                ctx.declareLocal("__top_${v.name}", type)
            }
            for (v in script.topLevelVars) {
                mv.visitVarInsn(ALOAD, 0)
                val init = v.initializer
                if (init != null) exprCompiler.compile(ctx, init) else mv.visitInsn(ACONST_NULL)
                val desc = v.type?.let { A2sTypeCodegen.boxedDescriptor(it) } ?: "Ljava/lang/Object;"
                mv.visitFieldInsn(PUTFIELD, internalName, v.name, desc)
            }
        }

        mv.visitInsn(RETURN)
        mv.visitMaxs(2, 1)
        mv.visitEnd()
    }

    private fun generateScriptFunction(cw: ClassWriter, internalName: String, f: A2sFunctionDecl) {
        val paramDescs = f.params.joinToString("") { A2sTypeCodegen.boxedDescriptor(it.type) }
        val returnDesc = f.returnType?.let { A2sTypeCodegen.boxedDescriptor(it) } ?: "Ljava/lang/Object;"
        val desc = "($paramDescs)$returnDesc"
        val mv = cw.visitMethod(ACC_PUBLIC, f.name, desc, null, null)
        mv.visitCode()

        val ctx = A2sCompileContext(mv, symbols, internalName, isStatic = false)
        for (p in f.params) ctx.declareLocal(p.name, p.type)

        when (val body = f.body) {
            is A2sFunctionBody.Expr -> {
                exprCompiler.compile(ctx, body.expr)
                mv.visitInsn(ARETURN)
            }

            is A2sFunctionBody.Block -> {
                for (stmt in body.statements) stmtCompiler.compile(ctx, stmt)
                mv.visitInsn(ACONST_NULL)
                mv.visitInsn(ARETURN)
            }
        }
        ctx.finish()
        mv.visitEnd()
    }

    private fun generateScriptHandler(cw: ClassWriter, internalName: String, h: A2sHandler) {
        val prefix = when (h.hookType) {
            A2sHookType.ON -> "handle"
            A2sHookType.BEFORE -> "before"
            A2sHookType.AFTER -> "after"
        }
        val methodName = "${prefix}_${A2sNames.sanitize(h.eventType)}"
        val desc = "(L$TYPE_EVENT_OBJECT;)V"
        val mv = cw.visitMethod(ACC_PUBLIC, methodName, desc, null, null)
        mv.visitCode()

        val ctx = A2sCompileContext(mv, symbols, internalName, isStatic = false)
        // 参数 1 是事件对象，类型为 A2sEventType
        h.paramName?.let { ctx.declareLocal(it, A2sEventType(h.eventType)) }

        for (stmt in h.body) stmtCompiler.compile(ctx, stmt)

        mv.visitInsn(RETURN)
        ctx.finish()
        mv.visitEnd()
    }
}
