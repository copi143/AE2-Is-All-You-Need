package kaptor.lsp

import kaptor.ast.ScriptFile
import kaptor.ast.EventHandler
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

data class LspMessage(
    val jsonrpc: String = "2.0",
    val id: Any? = null,
    val method: String? = null,
    val params: Any? = null,
    val result: Any? = null,
    val error: LspError? = null
)

data class LspError(val code: Int, val message: String, val data: Any? = null)

class ScriptLanguageServer(private val port: Int = 0) {
    private val logger = Logger.getLogger(ScriptLanguageServer::class.java.simpleName)
    private val gson = Gson()
    private val isRunning = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private val executor: ExecutorService = Executors.newFixedThreadPool(4)
    private val documents = ConcurrentHashMap<String, String>()
    private val languageService = ScriptLanguageService()

    companion object {
        const val METHOD_initialize = "initialize"
        const val METHOD_initialized = "initialized"
        const val METHOD_shutdown = "shutdown"
        const val METHOD_exit = "exit"
        const val METHOD_textDocument_didOpen = "textDocument/didOpen"
        const val METHOD_textDocument_didChange = "textDocument/didChange"
        const val METHOD_textDocument_didClose = "textDocument/didClose"
        const val METHOD_textDocument_completion = "textDocument/completion"
        const val METHOD_textDocument_hover = "textDocument/hover"
        const val METHOD_textDocument_definition = "textDocument/definition"
        const val METHOD_textDocument_diagnostics = "textDocument/publishDiagnostics"

        const val ERROR_PARSE_ERROR = -32700
        const val ERROR_INVALID_REQUEST = -32600
        const val ERROR_METHOD_NOT_FOUND = -32601
        const val ERROR_INTERNAL_ERROR = -32603
    }

    fun start() {
        if (isRunning.get()) return

        isRunning.set(true)
        serverSocket = if (port > 0) ServerSocket(port) else ServerSocket(0)
        val actualPort = serverSocket!!.localPort
        logger.info("LSP server started on port $actualPort")

        executor.submit { acceptConnections() }
    }

    fun startInBackground() {
        start()
    }

    private fun acceptConnections() {
        while (isRunning.get()) {
            try {
                val socket = serverSocket!!.accept()
                clientSocket = socket
                executor.submit { handleClient(socket) }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    logger.warning("Error accepting connection: ${e.message}")
                }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.inputStream))
        val writer = PrintWriter(BufferedWriter(OutputStreamWriter(socket.outputStream)), true)

        try {
            while (isRunning.get() && !socket.isClosed) {
                val message = readMessage(reader) ?: break
                val response = processMessage(message)
                if (response != null) {
                    writeMessage(writer, response)
                }
            }
        } catch (e: Exception) {
            logger.warning("Error handling client: ${e.message}")
        } finally {
            socket.close()
        }
    }

    private fun readMessage(reader: BufferedReader): LspMessage? {
        var contentLength = -1
        while (true) {
            val line = reader.readLine() ?: return null
            if (line.isEmpty()) break

            if (line.startsWith("Content-Length:")) {
                contentLength = line.substring("Content-Length:".length).trim().toInt()
            }
        }

        if (contentLength <= 0) return null

        val buffer = CharArray(contentLength)
        var totalRead = 0
        while (totalRead < contentLength) {
            val read = reader.read(buffer, totalRead, contentLength - totalRead)
            if (read == -1) break
            totalRead += read
        }

        val content = String(buffer, 0, totalRead)
        return gson.fromJson(content, LspMessage::class.java)
    }

    private fun writeMessage(writer: PrintWriter, message: LspMessage) {
        val json = gson.toJson(message)
        writer.print("Content-Length: ${json.length}\r\n\r\n")
        writer.print(json)
        writer.flush()
    }

    private fun processMessage(message: LspMessage): LspMessage? {
        return when (message.method) {
            METHOD_initialize -> handleInitialize(message)
            METHOD_initialized -> null
            METHOD_shutdown -> handleShutdown(message)
            METHOD_exit -> {
                stop()
                null
            }
            METHOD_textDocument_didOpen -> {
                handleDidOpen(message)
                null
            }
            METHOD_textDocument_didChange -> {
                handleDidChange(message)
                null
            }
            METHOD_textDocument_didClose -> {
                handleDidClose(message)
                null
            }
            METHOD_textDocument_completion -> handleCompletion(message)
            METHOD_textDocument_hover -> handleHover(message)
            METHOD_textDocument_definition -> handleDefinition(message)
            else -> {
                if (message.id != null) {
                    LspMessage(
                        id = message.id,
                        error = LspError(ERROR_METHOD_NOT_FOUND, "Method not found: ${message.method}")
                    )
                } else null
            }
        }
    }

    private fun handleInitialize(message: LspMessage): LspMessage {
        val capabilities = JsonObject().apply {
            add("textDocument", JsonObject().apply {
                add("completion", JsonObject().apply {
                    addProperty("completionItem", true)
                })
                add("hover", JsonObject().apply {
                    addProperty("contentFormat", "markdown")
                })
                add("definition", JsonObject().apply {
                    addProperty("dynamicRegistration", false)
                })
                add("publishDiagnostics", JsonObject().apply {
                    addProperty("relatedInformation", true)
                })
            })
            addProperty("workspace", true)
        }

        return LspMessage(
            id = message.id,
            result = JsonObject().apply {
                add("capabilities", capabilities)
            }
        )
    }

    private fun handleShutdown(message: LspMessage): LspMessage {
        return LspMessage(id = message.id, result = null)
    }

    private fun handleDidOpen(message: LspMessage) {
        val params = message.params as? JsonObject ?: return
        val textDocument = params.getAsJsonObject("textDocument") ?: return
        val uri = textDocument.get("uri")?.asString ?: return
        val text = textDocument.get("text")?.asString ?: return

        documents[uri] = text
        analyzeAndPublishDiagnostics(uri, text)
    }

    private fun handleDidChange(message: LspMessage) {
        val params = message.params as? JsonObject ?: return
        val textDocument = params.getAsJsonObject("textDocument") ?: return
        val uri = textDocument.get("uri")?.asString ?: return
        val contentChanges = params.getAsJsonArray("contentChanges") ?: return

        if (contentChanges.size() > 0) {
            val change = contentChanges[0].asJsonObject
            val text = change.get("text")?.asString ?: return
            documents[uri] = text
            analyzeAndPublishDiagnostics(uri, text)
        }
    }

    private fun handleDidClose(message: LspMessage) {
        val params = message.params as? JsonObject ?: return
        val textDocument = params.getAsJsonObject("textDocument") ?: return
        val uri = textDocument.get("uri")?.asString ?: return

        documents.remove(uri)
        languageService.clearCache(uri)
    }

    private fun handleCompletion(message: LspMessage): LspMessage {
        val params = message.params as? JsonObject ?: return createErrorResponse(message.id, ERROR_INVALID_REQUEST, "Invalid params")
        val textDocument = params.getAsJsonObject("textDocument") ?: return createErrorResponse(message.id, ERROR_INVALID_REQUEST, "Missing textDocument")
        val uri = textDocument.get("uri")?.asString ?: return createErrorResponse(message.id, ERROR_INVALID_REQUEST, "Missing URI")
        val position = params.getAsJsonObject("position") ?: return createErrorResponse(message.id, ERROR_INVALID_REQUEST, "Missing position")

        val line = position.get("line")?.asInt ?: 0
        val character = position.get("character")?.asInt ?: 0

        val document = documents[uri] ?: return createErrorResponse(message.id, ERROR_INVALID_REQUEST, "Document not found")

        val offset = calculateOffset(document, line, character)
        val completions = languageService.getCompletions(document, offset)

        val completionList = JsonObject().apply {
            addProperty("isIncomplete", false)
            val items = com.google.gson.JsonArray()
            for (item in completions) {
                items.add(JsonObject().apply {
                    addProperty("label", item.label)
                    addProperty("kind", item.kind.ordinal + 1)
                    if (item.detail != null) addProperty("detail", item.detail)
                    if (item.documentation != null) addProperty("documentation", item.documentation)
                    if (item.insertText != null) addProperty("insertText", item.insertText)
                })
            }
            add("items", items)
        }

        return LspMessage(id = message.id, result = completionList)
    }

    private fun handleHover(message: LspMessage): LspMessage {
        val params = message.params as? JsonObject ?: return createErrorResponse(message.id, ERROR_INVALID_REQUEST, "Invalid params")
        val textDocument = params.getAsJsonObject("textDocument") ?: return createErrorResponse(message.id, ERROR_INVALID_REQUEST, "Missing textDocument")
        val uri = textDocument.get("uri")?.asString ?: return createErrorResponse(message.id, ERROR_INVALID_REQUEST, "Missing URI")
        val position = params.getAsJsonObject("position") ?: return createErrorResponse(message.id, ERROR_INVALID_REQUEST, "Missing position")

        val line = position.get("line")?.asInt ?: 0
        val character = position.get("character")?.asInt ?: 0

        val document = documents[uri] ?: return createErrorResponse(message.id, ERROR_INVALID_REQUEST, "Document not found")

        val offset = calculateOffset(document, line, character)
        val hoverInfo = languageService.getHoverInfo(document, offset)
            ?: return LspMessage(id = message.id, result = null)

        return LspMessage(
            id = message.id,
            result = JsonObject().apply {
                addProperty("contents", JsonObject().apply {
                    addProperty("kind", "markdown")
                    addProperty("value", hoverInfo.contents)
                }.toString())
            }
        )
    }

    private fun handleDefinition(message: LspMessage): LspMessage {
        val params = message.params as? JsonObject ?: return createErrorResponse(message.id, ERROR_INVALID_REQUEST, "Invalid params")
        val textDocument = params.getAsJsonObject("textDocument") ?: return createErrorResponse(message.id, ERROR_INVALID_REQUEST, "Missing textDocument")
        val uri = textDocument.get("uri")?.asString ?: return createErrorResponse(message.id, ERROR_INVALID_REQUEST, "Missing URI")
        val position = params.getAsJsonObject("position") ?: return createErrorResponse(message.id, ERROR_INVALID_REQUEST, "Missing position")

        val line = position.get("line")?.asInt ?: 0
        val character = position.get("character")?.asInt ?: 0

        val document = documents[uri] ?: return createErrorResponse(message.id, ERROR_INVALID_REQUEST, "Document not found")

        val offset = calculateOffset(document, line, character)
        val definition = languageService.getDefinition(document, offset)
            ?: return LspMessage(id = message.id, result = null)

        return LspMessage(
            id = message.id,
            result = JsonObject().apply {
                addProperty("uri", "file://${definition.fileName}")
                add("range", JsonObject().apply {
                    add("start", JsonObject().apply {
                        addProperty("line", definition.line - 1)
                        addProperty("character", definition.column)
                    })
                    add("end", JsonObject().apply {
                        addProperty("line", definition.line - 1)
                        addProperty("character", definition.column + 10)
                    })
                })
            }
        )
    }

    private fun analyzeAndPublishDiagnostics(uri: String, text: String) {
        val result = languageService.analyze(text, uri)
        val diagnostics = com.google.gson.JsonArray()

        for (diagnostic in result.diagnostics) {
            diagnostics.add(JsonObject().apply {
                add("range", JsonObject().apply {
                    add("start", JsonObject().apply {
                        addProperty("line", diagnostic.line - 1)
                        addProperty("character", diagnostic.column)
                    })
                    add("end", JsonObject().apply {
                        addProperty("line", diagnostic.endLine - 1)
                        addProperty("character", diagnostic.endColumn)
                    })
                })
                addProperty("severity", diagnostic.severity.ordinal + 1)
                addProperty("message", diagnostic.message)
                addProperty("source", diagnostic.source)
            })
        }

        val notification = LspMessage(
            method = METHOD_textDocument_diagnostics,
            params = JsonObject().apply {
                addProperty("uri", uri)
                add("diagnostics", diagnostics)
            }
        )

        clientSocket?.let { socket ->
            val writer = PrintWriter(BufferedWriter(OutputStreamWriter(socket.outputStream)), true)
            writeMessage(writer, notification)
        }
    }

    private fun calculateOffset(document: String, line: Int, character: Int): Int {
        var offset = 0
        var currentLine = 0
        for (i in document.indices) {
            if (currentLine == line) {
                return offset + character
            }
            if (document[i] == '\n') {
                currentLine++
                offset = i + 1
            }
        }
        return offset + character
    }

    private fun createErrorResponse(id: Any?, code: Int, message: String): LspMessage {
        return LspMessage(
            id = id,
            error = LspError(code, message)
        )
    }

    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            logger.warning("Error closing server socket: ${e.message}")
        }
        executor.shutdown()
    }

    fun getPort(): Int = serverSocket?.localPort ?: 0
}
