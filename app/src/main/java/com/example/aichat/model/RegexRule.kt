package com.example.aichat.model

import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class RegexRule(
    val pattern: String,
    val replacement: String,
    val flags: String = ""
)

fun RegexRule.apply(input: String): String = try {
    val (body, appliedFlags) = if (pattern.startsWith("/") && pattern.lastIndexOf('/') > 0) {
        val last = pattern.lastIndexOf('/')
        pattern.substring(1, last) to pattern.substring(last + 1)
    } else {
        pattern to flags
    }
    val regex = compileRegex(body, appliedFlags)
    regex.replace(input, replacement)
} catch (_: Throwable) {
    input
}

private fun compileRegex(pattern: String, rawFlags: String): Regex {
    val options = buildSet {
        rawFlags.lowercase().forEach { c ->
            when (c) {
                'i' -> add(RegexOption.IGNORE_CASE)
                'm' -> add(RegexOption.MULTILINE)
                's' -> add(RegexOption.DOT_MATCHES_ALL)
            }
        }
    }
    val key = RegexCacheKey(pattern, options.map { it.name }.sorted().joinToString(":"))
    return regexCache[key] ?: run {
        val regex = if (options.isEmpty()) Regex(pattern) else Regex(pattern, options)
        regexCache[key] = regex
        regex
    }
}

private data class RegexCacheKey(val pattern: String, val flags: String)

private val regexCache = ConcurrentHashMap<RegexCacheKey, Regex>()
