package allyouneed.resgen

import allyouneed.util.idify
import com.google.gson.JsonObject

// ---------------------------------------------------------------------------------------------
// Language file templates: `+vars` placeholder sets + `{name}` template entries
// ---------------------------------------------------------------------------------------------

/** Top-level key of a language file holding the `{name}` placeholder value sets. */
internal const val VARS_KEY = "+vars"

/** Matches `{name}` placeholders inside language file keys/values. */
internal val PLACEHOLDER_REGEX = Regex("""\{([a-zA-Z0-9_]+)}""")

/**
 * 语言文件模板：顶层 `+vars` 定义 `{name}` 占位符的取值集合，其余条目中 key/value 含
 * `{name}` 的即为模板条目，含 `{name}` 的 key/value 由 [expandLangTemplate] 展开。
 *
 * Splits the language file template into the `+vars` placeholder sets and the remaining
 * entries (plain keys plus `{name}` template entries).
 */
internal fun splitLangTemplate(localeJson: JsonObject): Pair<Map<String, List<String>>, Map<String, String>> {
    val vars = linkedMapOf<String, List<String>>()
    val entries = linkedMapOf<String, String>()
    for ((key, value) in localeJson.entrySet()) {
        if (key == VARS_KEY) {
            for ((name, listEl) in value.asJsonObject.entrySet()) {
                vars[name] = listEl.asJsonArray.map { it.asString }
            }
        } else {
            entries[key] = value.asString
        }
    }
    return vars to entries
}

/**
 * 展开语言文件模板条目：对每个 `{name}` 占位符按 `+vars` 集合做笛卡尔积。key 中占位符
 * 替换为 id 形式（如 `1K` → `1k`、`LuV` → `luv`），value 中保留原样。展开后 key 若不在
 * 英文键集合 [enKeys] 中则跳过，避免为英文不存在的条目生成翻译。
 *
 * Expands `{name}` template entries against the `+vars` sets (Cartesian product). Expanded
 * keys absent from [enKeys] are skipped, so only entries that exist in en_us are emitted.
 */
internal fun expandLangTemplate(
    entries: Map<String, String>,
    vars: Map<String, List<String>>,
    enKeys: Set<String>,
): Map<String, String> {
    val result = linkedMapOf<String, String>()
    for ((key, value) in entries) {
        val names = PLACEHOLDER_REGEX.findAll(key).map { it.groupValues[1] }.toList() +
            PLACEHOLDER_REGEX.findAll(value).map { it.groupValues[1] }
        if (names.isEmpty()) {
            result[key] = value
            continue
        }
        for (name in names.toSet()) {
            require(name in vars) {
                "Undefined placeholder {$name} in lang template '$key'; add it under \"$VARS_KEY\""
            }
        }
        for (combo in placeholderCombinations(names.distinct(), vars)) {
            val expandedKey = renderPlaceholders(key, combo, idify = true)
            if (expandedKey !in enKeys) continue
            result[expandedKey] = renderPlaceholders(value, combo, idify = false)
        }
    }
    return result
}

/** 各占位符取值集合的笛卡尔积。 */
internal fun placeholderCombinations(
    names: List<String>,
    vars: Map<String, List<String>>,
): List<Map<String, String>> {
    if (names.isEmpty()) return listOf(emptyMap())
    val head = names.first()
    return vars.getValue(head).flatMap { headValue ->
        placeholderCombinations(names.drop(1), vars).map { it + (head to headValue) }
    }
}

internal fun renderPlaceholders(
    template: String,
    combo: Map<String, String>,
    idify: Boolean,
): String {
    var out = template
    for ((name, value) in combo) {
        val rendered = if (idify) idify(value) else value
        out = out.replace("{$name}", rendered)
    }
    return out
}
