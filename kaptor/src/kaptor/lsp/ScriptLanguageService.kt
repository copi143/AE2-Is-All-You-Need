package kaptor.lsp

import kaptor.parser.antlr.KotlinLexer
import kaptor.parser.antlr.KotlinParser
import kaptor.parser.antlr.KotlinSubsetVisitor
import kaptor.ast.ScriptFile
import kaptor.ast.AstNode
import kaptor.ast.EventHandler
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.tree.ParseTree
import java.util.concurrent.ConcurrentHashMap

data class Diagnostic(
    val line: Int,
    val column: Int,
    val endLine: Int,
    val endColumn: Int,
    val message: String,
    val severity: DiagnosticSeverity,
    val source: String = "kaptor"
)

enum class DiagnosticSeverity {
    ERROR, WARNING, INFO, HINT
}

data class CompletionItem(
    val label: String,
    val kind: CompletionItemKind,
    val detail: String? = null,
    val documentation: String? = null,
    val insertText: String? = null
)

enum class CompletionItemKind {
    KEYWORD, FUNCTION, VARIABLE, PROPERTY, VALUE, CLASS, MODULE, SNIPPET, EVENT
}

data class TokenInfo(
    val type: String,
    val text: String,
    val line: Int,
    val column: Int,
    val stopLine: Int,
    val stopColumn: Int
)

data class ScriptAnalysisResult(
    val ast: ScriptFile?,
    val parseTree: ParseTree?,
    val diagnostics: List<Diagnostic>,
    val tokens: List<TokenInfo>
)

class ScriptLanguageService {
    private val analysisCache = ConcurrentHashMap<String, ScriptAnalysisResult>()
    private val builtInKeywords = setOf(
        "fun", "val", "var", "if", "else", "when", "for", "while", "do",
        "return", "break", "continue", "throw", "import", "this", "super",
        "true", "false", "null", "in", "is", "as", "out", "typeof"
    )

    private val builtInFunctions = setOf(
        "println", "print", "toString", "toInt", "toLong", "toDouble",
        "len", "listOf", "mapOf", "setOf", "check", "require", "error",
        "checkNotNull", "requireNotNull"
    )

    fun analyze(source: String, fileName: String = "<input>"): ScriptAnalysisResult {
        val diagnostics = mutableListOf<Diagnostic>()
        val tokens = mutableListOf<TokenInfo>()

        val charStream = CharStreams.fromString(source)
        val lexer = KotlinLexer(charStream)
        val tokenStream = CommonTokenStream(lexer)
        val parser = KotlinParser(tokenStream)

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
                diagnostics.add(Diagnostic(
                    line = line,
                    column = charPositionInLine,
                    endLine = line,
                    endColumn = charPositionInLine + 1,
                    message = msg ?: "Syntax error",
                    severity = DiagnosticSeverity.ERROR
                ))
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
                diagnostics.add(Diagnostic(
                    line = line,
                    column = charPositionInLine,
                    endLine = line,
                    endColumn = charPositionInLine + 1,
                    message = msg ?: "Lexer error",
                    severity = DiagnosticSeverity.ERROR
                ))
            }
        })

        tokenStream.fill()
        for (token in tokenStream.tokens) {
            if (token.type != Token.EOF) {
                tokens.add(TokenInfo(
                    type = parser.vocabulary.getSymbolicName(token.type),
                    text = token.text,
                    line = token.line,
                    column = token.charPositionInLine,
                    stopLine = token.line,
                    stopColumn = token.charPositionInLine + token.text.length
                ))
            }
        }

        val parseTree = try {
            parser.kotlinFile()
        } catch (e: Exception) {
            diagnostics.add(Diagnostic(
                line = 1, column = 0, endLine = 1, endColumn = 1,
                message = "Parse error: ${e.message}",
                severity = DiagnosticSeverity.ERROR
            ))
            null
        }

        var ast: ScriptFile? = null
        if (parseTree != null) {
            try {
                val visitor = KotlinSubsetVisitor()
                ast = visitor.visit(parseTree) as? ScriptFile
            } catch (e: Exception) {
                diagnostics.add(Diagnostic(
                    line = 1, column = 0, endLine = 1, endColumn = 1,
                    message = "AST conversion error: ${e.message}",
                    severity = DiagnosticSeverity.ERROR
                ))
            }
        }

        if (ast != null) {
            diagnostics.addAll(semanticAnalysis(ast))
        }

        val result = ScriptAnalysisResult(ast, parseTree, diagnostics, tokens)
        analysisCache[fileName] = result
        return result
    }

    private fun semanticAnalysis(ast: ScriptFile): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()

        val eventTypes = mutableMapOf<String, Int>()
        for (handler in ast.handlers) {
            val count = eventTypes.getOrDefault(handler.eventType, 0) + 1
            eventTypes[handler.eventType] = count
            if (count > 1) {
                diagnostics.add(Diagnostic(
                    line = handler.line,
                    column = handler.col,
                    endLine = handler.line,
                    endColumn = handler.col + handler.eventType.length,
                    message = "Duplicate event handler for '${handler.eventType}'",
                    severity = DiagnosticSeverity.WARNING
                ))
            }
        }

        return diagnostics
    }

    fun getCompletions(source: String, offset: Int): List<CompletionItem> {
        val items = mutableListOf<CompletionItem>()

        for (kw in builtInKeywords) {
            items.add(CompletionItem(
                label = kw,
                kind = CompletionItemKind.KEYWORD,
                detail = "Keyword"
            ))
        }

        for (func in builtInFunctions) {
            items.add(CompletionItem(
                label = func,
                kind = CompletionItemKind.FUNCTION,
                detail = "Built-in function"
            ))
        }

        val context = getContextAtOffset(source, offset)
        if (context != null) {
            items.addAll(getContextCompletions(context))
        }

        return items
    }

    private fun getContextAtOffset(source: String, offset: Int): String? {
        if (offset <= 0 || offset > source.length) return null
        val beforeCursor = source.substring(0, offset)
        val lastLine = beforeCursor.lines().lastOrNull() ?: return null

        return when {
            lastLine.trimStart().startsWith("on ") -> "event_handler"
            lastLine.trimStart().startsWith("fun ") -> "function_decl"
            lastLine.trimStart().startsWith("val ") || lastLine.trimStart().startsWith("var ") -> "variable_decl"
            lastLine.trimStart().startsWith("if (") -> "condition"
            lastLine.trimStart().startsWith("when (") -> "when_subject"
            lastLine.trimEnd().endsWith(".") -> "member_access"
            lastLine.trimEnd().endsWith("::") -> "callable_reference"
            else -> null
        }
    }

    private fun getContextCompletions(context: String): List<CompletionItem> {
        return when (context) {
            "member_access" -> listOf(
                CompletionItem("toString", CompletionItemKind.FUNCTION, "Convert to string"),
                CompletionItem("hashCode", CompletionItemKind.FUNCTION, "Get hash code"),
                CompletionItem("equals", CompletionItemKind.FUNCTION, "Compare equality")
            )
            else -> emptyList()
        }
    }

    fun getHoverInfo(source: String, offset: Int): HoverInfo? {
        val result = analyze(source)
        if (result.tokens.isEmpty()) return null

        val token = result.tokens.find {
            offset >= getSourceOffset(source, it.line, it.column) &&
            offset < getSourceOffset(source, it.stopLine, it.stopColumn)
        } ?: return null

        return when {
            token.type in listOf("IF", "ELSE", "WHEN", "FOR", "WHILE", "FUN", "VAL", "VAR",
                "RETURN", "BREAK", "CONTINUE", "TRUE", "FALSE", "NULL", "IMPORT") ->
                HoverInfo("**${token.text}**", "Kotlin keyword")
            token.type == "Identifier" -> {
                if (token.text in builtInFunctions) {
                    HoverInfo("**${token.text}()**", "Built-in function")
                } else {
                    HoverInfo("**${token.text}**", "Identifier")
                }
            }
            else -> null
        }
    }

    private fun getSourceOffset(source: String, line: Int, column: Int): Int {
        var offset = 0
        var currentLine = 1
        for (i in source.indices) {
            if (currentLine == line) {
                return offset + column
            }
            if (source[i] == '\n') {
                currentLine++
                offset = i + 1
            }
        }
        return offset + column
    }

    fun getDefinition(source: String, offset: Int): DefinitionLocation? {
        val result = analyze(source)
        if (result.ast == null) return null

        val token = result.tokens.find {
            offset >= getSourceOffset(source, it.line, it.column) &&
            offset < getSourceOffset(source, it.stopLine, it.stopColumn)
        } ?: return null

        if (token.type != "Identifier") return null

        for (handler in result.ast.handlers) {
            if (handler.eventType == token.text) {
                return DefinitionLocation(
                    fileName = "<input>",
                    line = handler.line,
                    column = handler.col
                )
            }
        }

        return null
    }

    fun getCachedResult(fileName: String): ScriptAnalysisResult? = analysisCache[fileName]

    fun clearCache(fileName: String) { analysisCache.remove(fileName) }

    fun clearAllCache() { analysisCache.clear() }
}

data class HoverInfo(val contents: String, val kind: String)
data class DefinitionLocation(val fileName: String, val line: Int, val column: Int)
