package com.minima.chat.core.data.util

import com.minima.chat.core.network.dto.ChatMessageDto

/**
 * Fits OpenAI-compatible chat history into a character budget without breaking
 * tool_call integrity: an `assistant` with `tool_calls` stays glued to its
 * following `tool` results.
 *
 * Strategy (industrial, quiet):
 * 1. Keep leading `system` messages (soft-trim only if they alone blow the budget).
 * 2. Strip images from older user turns (keep the most recent [keepRecentImages]).
 * 3. Drop oldest non-system atomic units until under budget.
 * 4. If units were dropped, insert a short system omission note after systems.
 *
 * Used by Agent and non-Agent send paths so long sessions / vision base64 cannot
 * silently 400 the provider.
 */
object HistoryWindow {

    /** ~25–30k tokens rough upper bound for phone BYOK defaults. */
    const val DEFAULT_MAX_CHARS = 100_000

    const val OMISSION_NOTE =
        "[Earlier messages were omitted to fit the model context window.]"

    /** Keep images on this many most-recent user turns that carry attachments. */
    const val DEFAULT_KEEP_RECENT_IMAGES = 1

    data class Result(
        val messages: List<ChatMessageDto>,
        val omittedUnits: Int,
        val strippedImages: Int,
        val charsBefore: Int,
        val charsAfter: Int,
    ) {
        val didTrim: Boolean get() = omittedUnits > 0 || strippedImages > 0
    }

    fun estimateChars(m: ChatMessageDto): Int {
        var n = m.role.length +
            (m.content?.length ?: 0) +
            (m.name?.length ?: 0) +
            (m.toolCallId?.length ?: 0)
        m.toolCalls?.forEach { tc ->
            n += tc.id.length + tc.function.name.length + tc.function.arguments.length + 24
        }
        m.images.forEach { img ->
            n += img.mimeType.length + img.base64.length + 32
        }
        return n
    }

    fun estimateChars(messages: List<ChatMessageDto>): Int =
        messages.sumOf { estimateChars(it) }

    fun fit(
        messages: List<ChatMessageDto>,
        maxChars: Int = DEFAULT_MAX_CHARS,
        keepRecentImages: Int = DEFAULT_KEEP_RECENT_IMAGES,
    ): Result {
        val budget = maxChars.coerceAtLeast(2_000)
        val before = estimateChars(messages)
        if (before <= budget) {
            return Result(messages, omittedUnits = 0, strippedImages = 0, charsBefore = before, charsAfter = before)
        }

        val systems = messages.takeWhile { it.role.equals("system", ignoreCase = true) }
            .toMutableList()
        var rest = messages.drop(systems.size).toMutableList()

        softTrimSystems(systems, budget)

        var stripped = 0
        val imageTurnIndexes = rest.withIndex()
            .filter { (_, m) ->
                m.role.equals("user", ignoreCase = true) &&
                    m.images.any { it.base64.isNotBlank() }
            }
            .map { it.index }
        val keepImg = keepRecentImages.coerceAtLeast(0)
        if (imageTurnIndexes.size > keepImg) {
            val drop = imageTurnIndexes.dropLast(keepImg).toSet()
            rest = rest.mapIndexed { i, m ->
                if (i in drop && m.images.isNotEmpty()) {
                    stripped += m.images.size
                    m.copy(images = emptyList())
                } else {
                    m
                }
            }.toMutableList()
        }

        if (estimateChars(systems) + estimateChars(rest) <= budget) {
            val out = systems + rest
            return Result(out, 0, stripped, before, estimateChars(out))
        }

        val units = partitionUnits(rest)
        var keepFrom = 0
        while (keepFrom < units.size) {
            val candidate = units.subList(keepFrom, units.size).flatten()
            val noteCost = if (keepFrom > 0) OMISSION_NOTE.length + 16 else 0
            val total = estimateChars(systems) + estimateChars(candidate) + noteCost
            if (total <= budget) break
            keepFrom++
        }

        val omitted: Int
        val kept: List<ChatMessageDto>
        if (keepFrom >= units.size) {
            // Even the last unit alone is too big — keep it, strip images, soft-trim bodies.
            omitted = (units.size - 1).coerceAtLeast(0)
            val last = (units.lastOrNull() ?: emptyList()).map { m ->
                if (m.images.isNotEmpty()) {
                    stripped += m.images.size
                    m.copy(images = emptyList())
                } else {
                    m
                }
            }.toMutableList()
            shrinkUnitInPlace(last, systems, budget)
            kept = last
        } else {
            omitted = keepFrom
            kept = units.subList(keepFrom, units.size).flatten()
        }

        val out = ArrayList<ChatMessageDto>(systems.size + kept.size + 1)
        out += systems
        if (omitted > 0) {
            out += ChatMessageDto(role = "system", content = OMISSION_NOTE)
        }
        out += kept
        return Result(
            messages = out,
            omittedUnits = omitted,
            strippedImages = stripped,
            charsBefore = before,
            charsAfter = estimateChars(out),
        )
    }

    /**
     * Atomic units that must stay together for valid OpenAI tool replay.
     */
    internal fun partitionUnits(messages: List<ChatMessageDto>): List<List<ChatMessageDto>> {
        val units = mutableListOf<List<ChatMessageDto>>()
        var i = 0
        while (i < messages.size) {
            val m = messages[i]
            when {
                !m.toolCalls.isNullOrEmpty() -> {
                    val unit = mutableListOf(m)
                    i++
                    while (i < messages.size && messages[i].role.equals("tool", ignoreCase = true)) {
                        unit += messages[i]
                        i++
                    }
                    units += unit
                }
                m.role.equals("tool", ignoreCase = true) -> {
                    val unit = mutableListOf(m)
                    i++
                    while (i < messages.size && messages[i].role.equals("tool", ignoreCase = true)) {
                        unit += messages[i]
                        i++
                    }
                    units += unit
                }
                else -> {
                    units += listOf(m)
                    i++
                }
            }
        }
        return units
    }

    private fun softTrimSystems(systems: MutableList<ChatMessageDto>, budget: Int) {
        val systemBudget = (budget * 2) / 3
        while (systems.isNotEmpty() && estimateChars(systems) > systemBudget) {
            val idx = systems.indexOfLast { (it.content?.length ?: 0) > 2_000 }
            if (idx >= 0) {
                val m = systems[idx]
                val cut = (m.content!!.length / 2).coerceAtLeast(1_000).coerceAtMost(m.content!!.length)
                systems[idx] = m.copy(content = m.content!!.take(cut) + "\n…[truncated]")
                continue
            }
            if (systems.size > 1) {
                systems.removeAt(systems.lastIndex)
            } else {
                val m = systems[0]
                val c = m.content.orEmpty()
                systems[0] = m.copy(content = c.take(1_500) + if (c.length > 1_500) "\n…[truncated]" else "")
                break
            }
        }
    }

    private fun shrinkUnitInPlace(
        unit: MutableList<ChatMessageDto>,
        systems: List<ChatMessageDto>,
        budget: Int,
    ) {
        val noteCost = OMISSION_NOTE.length + 16
        fun over(): Boolean =
            estimateChars(systems) + estimateChars(unit) + noteCost > budget

        val isToolRound = unit.any { !it.toolCalls.isNullOrEmpty() } ||
            unit.any { it.role.equals("tool", ignoreCase = true) }

        while (over() && unit.isNotEmpty()) {
            val longIdx = unit.indexOfFirst { (it.content?.length ?: 0) > 600 }
            if (longIdx >= 0) {
                val m = unit[longIdx]
                unit[longIdx] = m.copy(content = m.content!!.take(400) + "…[truncated]")
                continue
            }
            val toolCallIdx = unit.indexOfFirst { !it.toolCalls.isNullOrEmpty() }
            if (toolCallIdx >= 0) {
                val m = unit[toolCallIdx]
                val trimmedCalls = m.toolCalls!!.map { tc ->
                    val args = tc.function.arguments
                    if (args.length <= 400) {
                        tc
                    } else {
                        tc.copy(
                            function = tc.function.copy(arguments = args.take(350) + "…"),
                        )
                    }
                }
                unit[toolCallIdx] = m.copy(toolCalls = trimmedCalls)
                if (!over()) break
            }
            // Tool rounds: never removeAt — orphaning tool results (or dropping
            // assistant(tool_calls)) breaks OpenAI-compatible replay (HTTP 400).
            if (isToolRound) {
                val toolIdx = unit.withIndex()
                    .filter { it.value.role.equals("tool", ignoreCase = true) }
                    .maxByOrNull { it.value.content?.length ?: 0 }
                    ?.index
                if (toolIdx != null && (unit[toolIdx].content?.length ?: 0) > 40) {
                    unit[toolIdx] = unit[toolIdx].copy(content = "…[omitted for context]")
                    continue
                }
                // Assistant prose on the tool_calls message
                val asstIdx = unit.indexOfFirst { !it.toolCalls.isNullOrEmpty() }
                if (asstIdx >= 0 && (unit[asstIdx].content?.length ?: 0) > 40) {
                    unit[asstIdx] = unit[asstIdx].copy(content = "…")
                    continue
                }
                // Already stubbed — stop rather than destroy structure
                break
            }
            if (unit.size > 1) {
                unit.removeAt(0)
            } else {
                val m = unit[0]
                unit[0] = m.copy(content = (m.content.orEmpty().take(200) + "…").take(240))
                break
            }
        }
    }

    /** True when every tool message is preceded by an assistant carrying tool_calls. */
    fun toolIntegrityOk(messages: List<ChatMessageDto>): Boolean {
        var i = 0
        while (i < messages.size) {
            val m = messages[i]
            if (m.role.equals("tool", ignoreCase = true)) {
                // Walk back to find the owning assistant(tool_calls)
                var j = i - 1
                while (j >= 0 && messages[j].role.equals("tool", ignoreCase = true)) j--
                if (j < 0 || messages[j].toolCalls.isNullOrEmpty()) return false
            }
            i++
        }
        return true
    }
}
