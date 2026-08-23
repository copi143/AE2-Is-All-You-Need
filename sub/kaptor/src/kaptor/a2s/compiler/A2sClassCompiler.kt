package kaptor.a2s.compiler

import kaptor.a2s.ir.*
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.*

/**
 * 类生成器：生成事件类与脚本类的字节码。
 */
class A2sClassCompiler(private val symbols: A2sSymbolTable) {

    private val exprCompiler = A2sExprCompiler(symbols).also { it.classCompiler = this }
    private val stmtCompiler = A2sStmtCompiler(symbols, exprCompiler)

    /** compileLambda() 写入，A2sCompiler.compile() 返回。key = lambdaClassName。 */
    val collectedLambdas = mutableMapOf<String, ByteArray>()

    /** 全局 lambda 索引计数器（跨方法共享，确保每个 lambda 类名唯一）。 */
    private var lambdaCounter = 0
    fun nextLambdaIndex(): Int = lambdaCounter++

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
        val desc = "(" + "Ljava/lang/Object;".repeat(method.params.size) + ")Ljava/lang/Object;"
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

        // handler → 方法
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
        val desc = "(" + "Ljava/lang/Object;".repeat(f.params.size) + ")Ljava/lang/Object;"
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

    /**
     * 生成 lambda 隐藏类字节码：实现 A2sLambdaFn 接口，参数从 invoke(Object[]) 拆包。
     * capturedVars: 闭包捕获的外层变量 (name, type)，作为额外字段传入构造器。
     */
    fun generateLambdaClass(
        className: String,
        expr: A2sLambda,
        scriptClassName: String,
        capturedVars: List<Pair<String, A2sType>> = emptyList(),
    ): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        cw.visit(V17, ACC_PUBLIC or ACC_SUPER, className, null, "java/lang/Object",
            arrayOf("kaptor/a2s/runtime/A2sLambdaFn"))

        // 字段：scriptObj + 每个捕获变量
        cw.visitField(ACC_PRIVATE, "scriptObj", "Ljava/lang/Object;", null, null).visitEnd()
        for ((capName, _) in capturedVars) {
            cw.visitField(ACC_PRIVATE, "cap_$capName", "Ljava/lang/Object;", null, null).visitEnd()
        }

        // 构造器：(Object scriptObj, Object cap0, Object cap1, ...)
        val ctorDesc = "(Ljava/lang/Object;" + "Ljava/lang/Object;".repeat(capturedVars.size) + ")V"
        val ctorMv = cw.visitMethod(ACC_PUBLIC, "<init>", ctorDesc, null, null)
        ctorMv.visitCode()
        ctorMv.visitVarInsn(ALOAD, 0)
        ctorMv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        // this.scriptObj = arg1
        ctorMv.visitVarInsn(ALOAD, 0)
        ctorMv.visitVarInsn(ALOAD, 1)
        ctorMv.visitFieldInsn(PUTFIELD, className, "scriptObj", "Ljava/lang/Object;")
        // this.cap_X = argN
        for ((i, _) in capturedVars.withIndex()) {
            ctorMv.visitVarInsn(ALOAD, 0)
            ctorMv.visitVarInsn(ALOAD, i + 2)
            ctorMv.visitFieldInsn(PUTFIELD, className, "cap_${capturedVars[i].first}", "Ljava/lang/Object;")
        }
        ctorMv.visitInsn(RETURN)
        ctorMv.visitMaxs(2, 2 + capturedVars.size)
        ctorMv.visitEnd()

        // invoke([Object]) -> 加载 scriptObj/captured vars 到局部槽 + 拆包参数 + 编译 body
        val invokeMv = cw.visitMethod(ACC_PUBLIC, "invoke", "([Ljava/lang/Object;)Ljava/lang/Object;", null, null)
        invokeMv.visitCode()

        // 用 isStatic=true 让 nextLocal 从 0 开始，然后预留 slot 0(this) 和 slot 1(Object[] args)
        val bodyCtx = A2sCompileContext(invokeMv, symbols, scriptClassName, isStatic = true)
        bodyCtx.declareLocal("__this", A2sAny)    // 预留 slot 0: this
        bodyCtx.declareLocal("__args_param", A2sAny) // 预留 slot 1: Object[] args 参数

        // 1. 加载 scriptObj 到局部槽（供顶层 var 访问）
        val scriptObjSlot = bodyCtx.declareLocal("__scriptObj", A2sAny)  // slot 2
        invokeMv.visitVarInsn(ALOAD, 0) // this
        invokeMv.visitFieldInsn(GETFIELD, className, "scriptObj", "Ljava/lang/Object;")
        invokeMv.visitVarInsn(ASTORE, scriptObjSlot)
        bodyCtx.scriptObjSlot = scriptObjSlot

        // 2. 加载捕获变量到局部槽（用原始名称和类型，供类型推断和嵌套 lambda 捕获分析使用）
        for ((capName, capType) in capturedVars) {
            val slot = bodyCtx.declareLocal(capName, capType)
            invokeMv.visitVarInsn(ALOAD, 0) // this
            invokeMv.visitFieldInsn(GETFIELD, className, "cap_$capName", "Ljava/lang/Object;")
            invokeMv.visitVarInsn(ASTORE, slot)
        }

        // 3. 从 Object[] 拆包 lambda 参数（slot 1 始终是 Object[] args 参数）
        for ((i, param) in expr.params.withIndex()) {
            val slot = bodyCtx.declareLocal(param.name, param.type ?: A2sAny)
            invokeMv.visitVarInsn(ALOAD, 1) // slot 1 = Object[] args
            invokeMv.visitLdcInsn(i)
            invokeMv.visitInsn(AALOAD)
            invokeMv.visitVarInsn(ASTORE, slot)
        }

        // 4. 编译 body
        when {
            expr.body.size == 1 && expr.body[0] is A2sExprStmt -> {
                exprCompiler.compile(bodyCtx, (expr.body[0] as A2sExprStmt).expr)
                invokeMv.visitInsn(ARETURN)
            }
            expr.body.size == 1 && expr.body[0] is A2sReturn -> {
                exprCompiler.compile(bodyCtx, (expr.body[0] as A2sReturn).value!!)
                invokeMv.visitInsn(ARETURN)
            }
            else -> {
                for ((i, stmt) in expr.body.withIndex()) {
                    if (i == expr.body.lastIndex && stmt is A2sExprStmt) {
                        exprCompiler.compile(bodyCtx, stmt.expr)
                        invokeMv.visitInsn(ARETURN)
                    } else {
                        stmtCompiler.compile(bodyCtx, stmt)
                    }
                }
                invokeMv.visitInsn(ACONST_NULL)
                invokeMv.visitInsn(ARETURN)
            }
        }

        bodyCtx.finish()
        invokeMv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }
}
