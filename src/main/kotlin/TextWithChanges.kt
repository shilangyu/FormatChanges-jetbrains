package org.example

import java.util.TreeSet

// There already is such a method on chars, but it handles a more broad range of whitespace chars,
// so here we specialize to what the assignment wanted.
fun Char.isWhitespace(): Boolean = this == ' ' || this == '\t' || this == '\r' || this == '\n'

fun String.isWhitespace(): Boolean = all { it.isWhitespace() }

// Represents a range in some text with an inclusive start and exclusive end.
data class TextRange(val start: UInt, val end: UInt) {
    init {
        assert(start <= end)
    }

    fun toIntRange() = IntRange(start.toInt(), end.toInt() - 1)
}

// Represents a formatting replacement. The replacement must be composed of whitespace only.
data class FormattingReplacement(val text: String) {
    init {
        assert(text.isWhitespace())
    }
}

// Represents the replacement of some range in source text.
data class TextChange(val range: TextRange, val replacement: FormattingReplacement) {
    companion object {
        fun dummy(offset: UInt): TextChange = TextChange(TextRange(offset, offset), FormattingReplacement(""))
    }
}

sealed class PositionInResult {
    data class Unchanged(val offset: UInt) : PositionInResult()

    data class Changed(val change: TextChange, val offset: UInt) : PositionInResult() {
        val isStart get() = offset == 0u
        val isEnd get() = offset == change.replacement.text.length.toUInt()
    }
}

data class RangeInResult(val start: PositionInResult, val end: PositionInResult)

class TextWithChanges(private val source: String) {
    // make sure we order the changes increasingly by the start offset
    // we maintain an invariant that ranges do not intersect/touch each other
    private val changes = TreeSet<TextChange> { a, b -> a.range.start.compareTo(b.range.start) }

    // NOTE: could handle checking if we are actually changing anything
    fun addChange(
        range: RangeInResult,
        text: String,
    ): TextChange {
        // a position has two equivalent representations on the edges:
        // as a source offset to the end of the segment, or as a change offset to the beginning
        // and
        // as a source offset to the start of a segment, or as a change offset to the end
        fun normalize(position: PositionInResult): PositionInResult =
            when (position) {
                is PositionInResult.Changed -> position
                is PositionInResult.Unchanged -> {
                    val glb = changes.floor(TextChange.dummy(position.offset))

                    if (glb != null && glb.range.start == position.offset) {
                        PositionInResult.Changed(glb, 0u)
                    } else if (glb != null && glb.range.end == position.offset) {
                        PositionInResult.Changed(
                            glb,
                            glb.replacement.text.length.toUInt(),
                        )
                    } else {
                        position
                    }
                }
            }

        // operate on normalized positions to reduce the amount of cases to consider
        val start = normalize(range.start)
        val end = normalize(range.end)

        when (start) {
            is PositionInResult.Changed ->
                when (end) {
                    is PositionInResult.Changed -> {
                        if (start.change != end.change) {
                            // check if we are exactly in between two ranges, if so, merge
                            val higher = changes.higher(start.change)
                            if (higher != null && higher == end.change) {
                                // all good, let's merge
                                val replacement = start.change.replacement.text + text + end.change.replacement.text
                                val textChange =
                                    TextChange(
                                        TextRange(start.change.range.start, end.change.range.end),
                                        FormattingReplacement(replacement),
                                    )

                                assert(source.substring(textChange.range.toIntRange()).isWhitespace())
                                changes.remove(start.change)
                                changes.remove(end.change)
                                changes.add(textChange)
                                return textChange
                            }

                            throw throw AssertionError("The given range intersects changes")
                        }
                        // this altering an existing change
                        val change = start.change

                        val replacement =
                            change.replacement.text.substring(0, start.offset.toInt()) +
                                text +
                                change.replacement.text.substring(end.offset.toInt())
                        val textChange = TextChange(change.range, FormattingReplacement(replacement))

                        changes.remove(change)
                        changes.add(textChange)
                        return textChange
                    }

                    is PositionInResult.Unchanged -> {
                        if (start.isEnd) {
                            // new change is exactly to the right of an existing change, see if we can merge
                            // there must not be any more changes in between start and end
                            val higher = changes.higher(start.change)
                            if (higher == null || higher.range.start > end.offset) {
                                // all good, let's merge
                                val replacement = start.change.replacement.text + text
                                val textChange =
                                    TextChange(TextRange(start.change.range.start, end.offset), FormattingReplacement(replacement))

                                assert(source.substring(textChange.range.toIntRange()).isWhitespace())
                                changes.remove(start.change)
                                changes.add(textChange)
                                return textChange
                            }
                        }
                        throw AssertionError("The given range intersects changes")
                    }
                }

            is PositionInResult.Unchanged ->
                when (end) {
                    is PositionInResult.Changed -> {
                        if (end.isStart) {
                            // new change is exactly to the left of an existing change, see if we can merge
                            // there must not be any more changes in between start and end
                            val lower = changes.lower(end.change)
                            if (lower == null || lower.range.end < start.offset) {
                                // all good, let's merge
                                val replacement = text + end.change.replacement.text
                                val textChange =
                                    TextChange(TextRange(start.offset, end.change.range.end), FormattingReplacement(replacement))

                                assert(source.substring(textChange.range.toIntRange()).isWhitespace())

                                changes.remove(end.change)
                                changes.add(textChange)
                                return textChange
                            }
                        }

                        throw AssertionError("The given range intersects changes")
                    }
                    is PositionInResult.Unchanged -> {
                        // this is a new change
                        // make sure we only edit whitespace
                        assert(source.substring(start.offset.toInt(), end.offset.toInt()).isWhitespace())

                        val textChange = TextChange(TextRange(start.offset, end.offset), FormattingReplacement(text))

                        changes.add(textChange)
                        return textChange
                    }
                }
        }
    }

    enum class SearchKind { NON_WHITESPACE, LINE_BREAK, BOTH }

    enum class SearchDirection { FRONT_TO_BACK, BACK_TO_FRONT }

    enum class FoundObjectKind { NON_WHITESPACE, LINE_BREAK }

    data class SearchResult(val position: PositionInResult, val objectKind: FoundObjectKind)

    fun search(
        range: RangeInResult,
        direction: SearchDirection,
        whatToSearch: SearchKind,
    ): SearchResult? {
        val matchNonWhitespace = { c: Char -> if (!c.isWhitespace()) FoundObjectKind.NON_WHITESPACE else null }
        val matchLineBreak = { c: Char -> if (c == '\n' || c == '\r') FoundObjectKind.LINE_BREAK else null }
        val matchBoth = { c: Char -> matchNonWhitespace(c) ?: matchLineBreak(c) }

        val matcher =
            when (whatToSearch) {
                SearchKind.NON_WHITESPACE -> matchNonWhitespace
                SearchKind.LINE_BREAK -> matchLineBreak
                SearchKind.BOTH -> matchBoth
            }

        // given a found character we need to relativize it to the base offset of the segment
        fun computePosition(
            offset: UInt,
            baseOffset: PositionInResult,
        ): PositionInResult =
            when (baseOffset) {
                is PositionInResult.Changed -> baseOffset.copy(offset = baseOffset.offset + offset)
                is PositionInResult.Unchanged -> baseOffset.copy(offset = baseOffset.offset + offset)
            }

        val result =
            when (direction) {
                SearchDirection.FRONT_TO_BACK ->
                    segmentsInRange(range).map {
                        it.text.mapIndexed { i, c -> i.toUInt() to matcher(c) }.find { (_, v) -> v != null }?.let { (i, v) ->
                            computePosition(i, it.baseOffset) to v!!
                        }
                    }
                // ugh-oh, back-to-front is done eagerly. segmentsInRange could be improved to accommodate both directions
                SearchDirection.BACK_TO_FRONT ->
                    segmentsInRange(range).toMutableList().also { it.reversed() }.map {
                        it.text.mapIndexed { i, c -> i.toUInt() to matcher(c) }.findLast { (_, v) -> v != null }?.let { (i, v) ->
                            computePosition(i, it.baseOffset) to v!!
                        }
                    }.asSequence()
            }
                .firstNotNullOfOrNull { it }

        return result?.let {
            SearchResult(it.first, it.second)
        }
    }

    fun countLineBreaks(range: RangeInResult): Int {
        // NOTE: could be done more efficiently, ie without creating intermediate strings
        fun String.countLineBreaks(): Int = this.splitToSequence("\r\n", "\n", "\r").count() - 1

        return segmentsInRange(range).sumOf { it.text.countLineBreaks() }
    }

    data class SimpleSpacesCount(val spaces: Int, val tabs: Int, val visualSpace: Int)

    fun countSimpleSpaces(
        range: RangeInResult,
        tabWidth: Int,
    ): SimpleSpacesCount {
        var spaces = 0
        var tabs = 0
        var visualSpace = 0

        // we assume that we start at zero, ie the first column
        var lineOffset = 0

        for (segment in segmentsInRange(range).map { it.text }) {
            for (char in segment) {
                when (char) {
                    ' ' -> {
                        spaces += 1
                        visualSpace += 1
                        lineOffset += 1
                    }
                    '\t' -> {
                        tabs += 1
                        val fillSpace = tabWidth - (lineOffset % tabWidth)
                        visualSpace += fillSpace
                        lineOffset += fillSpace
                    }
                    '\r', '\n' -> lineOffset = 0
                    else -> {
                        lineOffset += 1
                        visualSpace += 1
                    }
                }
            }
        }

        return SimpleSpacesCount(spaces, tabs, visualSpace)
    }

    fun applyChanges(): String {
        // we just iterate over changes by applying them and alternating with the original source
        val result = StringBuilder()
        var currentOffset = 0
        for (change in changes) {
            result.append(source.substring(currentOffset, change.range.start.toInt()))
            result.append(change.replacement.text)
            currentOffset = change.range.end.toInt()
        }
        result.append(source.substring(currentOffset))
        return result.toString()
    }

    data class Segment(val text: String, val baseOffset: PositionInResult)

    private fun segmentsInRange(range: RangeInResult): Sequence<Segment> =
        sequence {
            // there are quite a few possible paths, bare with me
            var current =
                when (range.start) {
                    is PositionInResult.Changed -> {
                        when (range.end) {
                            is PositionInResult.Changed -> {
                                // both start and end are within an existing change, if it's the same one yield it and exit
                                if (range.start.change == range.end.change) {
                                    yield(
                                        Segment(
                                            range.start.change.replacement.text.substring(
                                                range.start.offset.toInt(),
                                                range.end.offset.toInt(),
                                            ),
                                            PositionInResult.Changed(range.start.change, range.start.offset),
                                        ),
                                    )
                                    return@sequence
                                }
                            }
                            // left for exhaustiveness checks
                            is PositionInResult.Unchanged -> {}
                        }

                        // it's a difference change, let's yield the rest of the current change and proceed
                        yield(
                            Segment(
                                range.start.change.replacement.text.substring(range.start.offset.toInt()),
                                PositionInResult.Changed(range.start.change, range.start.offset),
                            ),
                        )
                        range.start.change.range.end
                    }
                    is PositionInResult.Unchanged -> range.start.offset
                }

            // at this point `current` points to the beginning of a source segment
            // in fact, after each iteration it will be in that spot

            // assuming `range` is correctly provided, this terminates
            // NOTE: this could be handled better (by upper bounding by the amount of changes)
            while (true) {
                // find the closest change above the current position
                val nextChange = changes.higher(TextChange.dummy(current))

                when (range.end) {
                    is PositionInResult.Changed -> {
                        // nextChange should not be null here since end is a change
                        // since end is a change, it is not an offset to the source. Thus, we yield the whole segment up
                        // to the change
                        yield(
                            Segment(
                                source.substring(current.toInt(), nextChange!!.range.start.toInt()),
                                PositionInResult.Unchanged(current),
                            ),
                        )
                        // exit condition 1: the end was a change, and we are that change now
                        if (nextChange == range.end.change) {
                            yield(
                                Segment(
                                    nextChange.replacement.text.substring(0, range.end.offset.toInt()),
                                    PositionInResult.Changed(nextChange, 0u),
                                ),
                            )
                            return@sequence
                        }
                    }
                    is PositionInResult.Unchanged -> {
                        // exit condition 2: the end was in the source, and we are in that segment now
                        if (nextChange == null || nextChange.range.start > range.end.offset) {
                            yield(Segment(source.substring(current.toInt(), range.end.offset.toInt()), PositionInResult.Unchanged(current)))
                            return@sequence
                        } else {
                            // it is not the current source segment, consume it whole until the next change
                            yield(
                                Segment(
                                    source.substring(current.toInt(), nextChange.range.start.toInt()),
                                    PositionInResult.Unchanged(current),
                                ),
                            )
                        }
                    }
                }

                // at this point we yielded the source segment, and we are free to yield the change segment
                yield(Segment(nextChange.replacement.text, PositionInResult.Changed(nextChange, 0u)))
                current = nextChange.range.end
            }
        }
}
