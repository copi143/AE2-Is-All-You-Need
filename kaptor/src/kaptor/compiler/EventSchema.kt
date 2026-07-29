package kaptor.compiler

data class ParamDef(
    val name: String,
    val type: Class<*>
)

class EventSchema(val parameters: List<ParamDef>)

class SchemaBuilder {
    private val params = mutableListOf<ParamDef>()

    fun string(name: String) { params.add(ParamDef(name, String::class.java)) }
    fun int(name: String) { params.add(ParamDef(name, Int::class.java)) }
    fun long(name: String) { params.add(ParamDef(name, Long::class.java)) }
    fun double(name: String) { params.add(ParamDef(name, Double::class.java)) }
    fun bool(name: String) { params.add(ParamDef(name, Boolean::class.java)) }
    fun any(name: String) { params.add(ParamDef(name, Any::class.java)) }

    fun build(): EventSchema = EventSchema(params.toList())
}
