package org.example

import java.util.TreeSet

// There already is such a method on chars, but it handles a more broad range of whitespace chars,
// so here we specialize to what the assignment wanted.
fun String.isWhitespace(): Boolean = all { it == ' ' || it == '\t' || it == '\r' || it == '\n' }

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

    fun countLineBreaks(range: RangeInResult): Int {
        // NOTE: could be done more efficiently
        fun String.countLineBreaks(): Int = this.splitToSequence("\r\n", "\n").count() - 1

        var count = 0

        var current =
            when (range.start) {
                is PositionInResult.Changed -> {
                    when (range.end) {
                        is PositionInResult.Changed -> {
                            if (range.start.change == range.end.change) {
                                count +=
                                    range.start.change.replacement.text.substring(
                                        range.start.offset.toInt(),
                                        range.end.offset.toInt(),
                                    ).countLineBreaks()
                                return count
                            }
                        }
                        is PositionInResult.Unchanged -> {}
                    }

                    count += range.start.change.replacement.text.substring(range.start.offset.toInt()).countLineBreaks()
                    range.start.change.range.end
                }
                is PositionInResult.Unchanged -> range.start.offset
            }
        // assuming `range` is correctly provided, this terminates
        // NOTE: this could be handled better (by upper bounding by the amount of changes)
        while (true) {
            val nextChange = changes.higher(TextChange.dummy(current))

            when (range.end) {
                is PositionInResult.Changed -> {
                    // nextChange should not be null here since end is a change
                    count += source.substring(current.toInt(), nextChange!!.range.start.toInt()).countLineBreaks()

                    if (nextChange == range.end.change) {
                        count += nextChange.replacement.text.substring(0, range.end.offset.toInt()).countLineBreaks()
                        return count
                    }
                }
                is PositionInResult.Unchanged -> {
                    if (nextChange == null || nextChange.range.start > range.end.offset) {
                        count += source.substring(current.toInt(), range.end.offset.toInt()).countLineBreaks()
                        return count
                    } else {
                        count += source.substring(current.toInt(), nextChange.range.start.toInt()).countLineBreaks()
                    }
                }
            }

            count += nextChange.replacement.text.countLineBreaks()
            current = nextChange.range.end
        }
    }


    fun applyChanges(): String {
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
}
