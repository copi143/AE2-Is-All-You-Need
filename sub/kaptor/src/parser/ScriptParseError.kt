package kaptor.parser

class ScriptParseError(message: String, val line: Int, val column: Int) : Exception(message)
