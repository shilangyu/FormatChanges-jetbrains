package org.example

fun main() {
    val formatter = TextWithChanges("while( true){foo( );}")
    val change = formatter.addChange(RangeInResult(PositionInResult.Unchanged(6u), PositionInResult.Unchanged(7u)), "")
    formatter.addChange(RangeInResult(PositionInResult.Changed(change, 0u), PositionInResult.Unchanged(7u)), "  ")

    println(formatter.applyChanges())
}
