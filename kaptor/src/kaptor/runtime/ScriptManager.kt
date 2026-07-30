package kaptor.runtime

import kaptor.compiler.CompiledHandler
import kaptor.compiler.CompiledScript
import kaptor.compiler.ScriptCompiler
import kaptor.compiler.ScriptCompileError
import kaptor.parser.antlr.KotlinLexer
import kaptor.parser.antlr.KotlinParser
import kaptor.parser.antlr.KotlinSubsetVisitor
import kaptor.lsp.ScriptLanguageService
import kaptor.parser.ScriptParseError
import kaptor.ScriptLogger
import kaptor.createLogger
import org.antlr.v4.runtime.*
import java.io.File
import java.net.URLClassLoader
import java.nio.file.*
import java.util.concurrent.ConcurrentHashMap

object ScriptManager {
    private val compiledScripts = ConcurrentHashMap<String, CompiledScript>()
    private val loadedHandlers = ConcurrentHashMap<String, ScriptHandlerBase>()
    private val compiler = ScriptCompiler()
    private val languageService = ScriptLanguageService()
    private var scriptDir: Path? = null
    private var fileWatcher: WatchService? = null
    private var watcherThread: Thread? = null
    private var isRunning = false
    private var classLoader: URLClassLoader? = null
    private var managerLogger: ScriptLogger = createLogger()

    fun init(scriptsPath: Path, logger: ScriptLogger = createLogger()) {
        managerLogger = logger
        scriptDir = scriptsPath
        if (!Files.exists(scriptsPath)) {
            Files.createDirectories(scriptsPath)
        }
        classLoader = URLClassLoader(
            arrayOf(scriptsPath.toUri().toURL()),
            ScriptManager::class.java.classLoader
        )
        managerLogger.info("ScriptManager initialized with directory: $scriptsPath")
    }

    fun loadEventClasses() {
        val dir = scriptDir ?: return
        val bytecodes = ScriptEventBus.getEventClassBytecodes()
        if (bytecodes.isEmpty()) return

        for ((eventType, bytecode) in bytecodes) {
            if (ScriptEventBus.getEventClass(eventType) != null) continue
            val safeName = eventType.replace(Regex("[^a-zA-Z0-9_]"), "_")
            val classFileName = "kaptor/runtime/event/${safeName}.class"
            val classFile = dir.resolve(classFileName)
            Files.createDirectories(classFile.parent)
            Files.write(classFile, bytecode)
        }

        classLoader = URLClassLoader(
            arrayOf(dir.toUri().toURL()),
            ScriptManager::class.java.classLoader
        )

        for ((eventType, _) in bytecodes) {
            if (ScriptEventBus.getEventClass(eventType) != null) continue
            val safeName = eventType.replace(Regex("[^a-zA-Z0-9_]"), "_")
            try {
                val clazz = classLoader!!.loadClass("kaptor.runtime.event.$safeName")
                ScriptEventBus.registerEventClass(eventType, clazz)
            } catch (e: Exception) {
                managerLogger.error("Failed to load event class for $eventType", e)
            }
        }
    }

    fun loadAllScripts() {
        val dir = scriptDir ?: return
        managerLogger.info("Loading all scripts from $dir")

        Files.list(dir).use { stream ->
            stream.filter { it.toString().endsWith(".kts") || it.toString().endsWith(".kt") }.forEach { path ->
                try {
                    loadScript(path)
                } catch (e: Exception) {
                    managerLogger.error("Failed to load script: ${path.fileName}", e)
                }
            }
        }

        managerLogger.info("Loaded ${compiledScripts.size} scripts with ${ScriptEventBus.getHandlerCount()} handlers")
    }

    fun loadScript(path: Path): Boolean {
        val fileName = path.fileName.toString()
        val name = when {
            fileName.endsWith(".kts") -> fileName.removeSuffix(".kts")
            fileName.endsWith(".kt") -> fileName.removeSuffix(".kt")
            else -> return false
        }
        val isKt = fileName.endsWith(".kt")
        val source = Files.readString(path)

        return try {
            val ir = parseWithAntlr(source, name, isKt)

            val eventClassMap = buildEventClassMap()
            val compiled = compiler.compile(ir, name, eventClassMap, isKt)

            unregisterScript(name)

            val classFile = scriptDir!!.resolve("${compiled.className.replace('.', '/')}.class")
            Files.createDirectories(classFile.parent)
            Files.write(classFile, compiled.bytecode)
            classLoader = URLClassLoader(
                arrayOf(scriptDir!!.toUri().toURL()),
                ScriptManager::class.java.classLoader
            )

            val clazz = classLoader!!.loadClass(compiled.className)
            val handler = clazz.getDeclaredConstructor().newInstance() as ScriptHandlerBase

            for (ch in compiled.handlers) {
                ScriptEventBus.registerHandler(handler, ch.eventType, ch.hookType, ch.costLimit, name)
            }

            compiledScripts[name] = compiled
            loadedHandlers[name] = handler

            managerLogger.info("Script loaded successfully: $name (${compiled.eventTypes.size} handlers)")
            true
        } catch (e: ScriptParseError) {
            managerLogger.error("Parse error in script $name: ${e.message}")
            false
        } catch (e: ScriptCompileError) {
            managerLogger.error("Compile error in script $name: ${e.message}")
            false
        } catch (e: Exception) {
            managerLogger.error("Error loading script $name", e)
            false
        }
    }

    private fun parseWithAntlr(source: String, name: String, isKt: Boolean = false): kaptor.ir.IrScriptFile {
        val charStream = CharStreams.fromString(source)
        val lexer = KotlinLexer(charStream)
        val tokenStream = CommonTokenStream(lexer)
        val parser = KotlinParser(tokenStream)

        val errors = mutableListOf<String>()
        parser.removeErrorListeners()
        parser.addErrorListener(object : BaseErrorListener() {
            override fun syntaxError(
                recognizer: Recognizer<*, *>?,
                offendingSymbol: Any?,
                line: Int,
                charPositionInLine: Int,
                msg: String?,
                e: RecognitionException?
            ) {
                errors.add("Line $line:$charPositionInLine - $msg")
            }
        })

        lexer.removeErrorListeners()
        lexer.addErrorListener(object : BaseErrorListener() {
            override fun syntaxError(
                recognizer: Recognizer<*, *>?,
                offendingSymbol: Any?,
                line: Int,
                charPositionInLine: Int,
                msg: String?,
                e: RecognitionException?
            ) {
                errors.add("Lexer error at line $line:$charPositionInLine - $msg")
            }
        })

        val parseTree = if (isKt) parser.kotlinFile() else parser.script()

        if (errors.isNotEmpty()) {
            throw ScriptParseError(errors.joinToString("\n"), 1, 0)
        }

        val declaredEvents = ScriptEventBus.buildDeclaredEventsMap()
        val visitor = KotlinSubsetVisitor(declaredEvents)
        val ir = visitor.visit(parseTree) as? kaptor.ir.IrScriptFile
            ?: throw ScriptParseError("Failed to convert ANTLR tree to script: $name", 1, 0)

        return ir
    }

    fun unloadScript(name: String): Boolean {
        return unregisterScript(name)
    }

    private fun unregisterScript(name: String): Boolean {
        val removed = compiledScripts.remove(name)
        loadedHandlers.remove(name)
        ScriptEventBus.unregisterScript(name)
        return removed != null
    }

    fun reloadScript(path: Path): Boolean {
        val fileName = path.fileName.toString()
        val scriptName = when {
            fileName.endsWith(".kts") -> fileName.removeSuffix(".kts")
            fileName.endsWith(".kt") -> fileName.removeSuffix(".kt")
            else -> return false
        }
        managerLogger.info("Reloading script: $scriptName")
        unregisterScript(scriptName)
        return loadScript(path)
    }

    fun startHotReload() {
        if (isRunning) return
        val dir = scriptDir ?: return

        isRunning = true
        fileWatcher = FileSystems.getDefault().newWatchService()

        dir.register(
            fileWatcher!!,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_DELETE
        )

        watcherThread = Thread({
            managerLogger.info("Script hot-reload watcher started for $dir")
            while (isRunning) {
                try {
                    val key = fileWatcher!!.take()
                    for (event in key.pollEvents()) {
                        val kind = event.kind()
                        val filePath = event.context() as? Path ?: continue
                        val fullPath = dir.resolve(filePath)

                        when (kind) {
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY -> {
                                val f = filePath.toString()
                                if (f.endsWith(".kts") || f.endsWith(".kt")) {
                                    Thread.sleep(100)
                                    reloadScript(fullPath)
                                }
                            }
                            StandardWatchEventKinds.ENTRY_DELETE -> {
                                val f = filePath.toString()
                                if (f.endsWith(".kts") || f.endsWith(".kt")) {
                                    val name = if (f.endsWith(".kts")) f.removeSuffix(".kts") else f.removeSuffix(".kt")
                                    unregisterScript(name)
                                    managerLogger.info("Script deleted: $name")
                                }
                            }
                        }
                    }
                    key.reset()
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    managerLogger.error("Error in script watcher", e)
                }
            }
            managerLogger.info("Script hot-reload watcher stopped")
        }, "ScriptHotReload-Watcher")
        watcherThread!!.isDaemon = true
        watcherThread!!.start()
    }

    fun stopHotReload() {
        isRunning = false
        watcherThread?.interrupt()
        watcherThread = null
        try {
            fileWatcher?.close()
        } catch (_: Exception) {}
        fileWatcher = null
    }

    fun getLoadedScripts(): Set<String> = compiledScripts.keys.toSet()

    fun isScriptLoaded(name: String): Boolean = compiledScripts.containsKey(name)

    fun getStats(): ScriptStats {
        return ScriptStats(
            loadedScripts = compiledScripts.size,
            totalHandlers = ScriptEventBus.getHandlerCount(),
            registeredEventTypes = ScriptEventBus.getRegisteredEventTypes()
        )
    }

    fun getLanguageService(): ScriptLanguageService = languageService

    private fun buildEventClassMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (eventType in ScriptEventBus.getRegisteredEventClassTypes()) {
            val clazz = ScriptEventBus.getEventClass(eventType) ?: continue
            map[eventType] = clazz.name.replace('.', '/')
        }
        return map
    }

    fun reset() {
        stopHotReload()
        ScriptEventBus.resetAll()
        compiledScripts.clear()
        loadedHandlers.clear()
        scriptDir = null
        classLoader = null
        compiler.resetCounter()
    }
}

data class ScriptStats(
    val loadedScripts: Int,
    val totalHandlers: Int,
    val registeredEventTypes: Set<String>
)
