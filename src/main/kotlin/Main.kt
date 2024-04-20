package org.example
import java.util.TreeSet

// There already is such a method on chars, but it handles a more broad range of whitespace chars,
// so here we specialize to what the assignment wanted.
fun String.isWhitespace(): Boolean = all { it == ' ' || it == '\t' || it == '\r' || it == '\n' }

// Represents a range in some text with an inclusive start and exclusive end.
data class TextRange(val start: UInt, val end: UInt) {
    val empty get() = start == end

    init {
        assert(start <= end)
    }
}

// Represents a formatting replacement. The replacement must be composed of whitespace only.
data class FormattingReplacement(val text: String) {
    init {
        assert(text.isWhitespace())
    }
}

// Represents the replacement of some range in source text.
data class TextChange(val range: TextRange, val replacement: FormattingReplacement)

sealed class PositionInResult {
    data class Unchanged(val offset: UInt) : PositionInResult()

    data class Changed(val change: TextChange, val offset: UInt) : PositionInResult()
}

data class RangeInResult(val start: PositionInResult, val end: PositionInResult)

class TextWithChanges(private val source: String) {
    // make sure we order the changes increasingly by the start offset
    // we maintain an invariant that ranges do not intersect/touch each other
    private val changes = TreeSet<TextChange> { a, b -> a.range.start.compareTo(b.range.start) }

    // TODO: could handle checking if we are actually changing anything
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
                    val glb =
                        changes.floor(
                            TextChange(
                                TextRange(position.offset, position.offset),
                                FormattingReplacement(""),
                            ),
                        )

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

        // TODO: check when changes are touching
        when (start) {
            is PositionInResult.Changed ->
                when (end) {
                    is PositionInResult.Changed -> {
                        if (start.change != end.change) {
                            throw throw AssertionError("The given range intersects changes")
                        }
                        val change = start.change
                        // this altering an existing change
                        changes.remove(change)

                        val replacement =
                            change.replacement.text.substring(0, start.offset.toInt()) +
                                text +
                                change.replacement.text.substring(end.offset.toInt())
                        val textChange = TextChange(change.range, FormattingReplacement(replacement))

                        changes.add(textChange)
                        return textChange
                    }

                    is PositionInResult.Unchanged -> throw AssertionError("The given range intersects changes")
                }

            is PositionInResult.Unchanged ->
                when (end) {
                    is PositionInResult.Changed -> throw AssertionError("The given range intersects changes")
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
    fun applyChanges(): String {
        val result = StringBuilder()
        var currentOffset = 0
        for (change in changes) {
            result.append(source.substring(currentOffset, change.range.start.toInt()))
            result.append(change.replacement.text)
            currentOffset = change.range.end.toInt()
        }
        // TODO: make sure this doesnt go over the upper limit
        result.append(source.substring(currentOffset))
        return result.toString()
    }
}

fun main() {
    val formatter = TextWithChanges("while( true){foo( );}")
    val change = formatter.addChange(RangeInResult(PositionInResult.Unchanged(6u), PositionInResult.Unchanged(7u)), "")
    formatter.addChange(RangeInResult(PositionInResult.Unchanged(6u), PositionInResult.Unchanged(7u)), "  ")
    println(formatter.applyChanges())
}
