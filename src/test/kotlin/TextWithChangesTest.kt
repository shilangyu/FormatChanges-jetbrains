import org.example.FormattingReplacement
import org.example.PositionInResult
import org.example.RangeInResult
import org.example.TextChange
import org.example.TextRange
import org.example.TextWithChanges
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class TextWithChangesTest {
    @Nested
    inner class AddChange {
        @Test
        fun cannotEditNonWhitespace() {
            val formatter = TextWithChanges("abc efg")

            assertThrows<AssertionError> {
                formatter.addChange(RangeInResult(PositionInResult.Unchanged(3u), PositionInResult.Unchanged(5u)), "")
            }
        }

        @Test
        fun normalizesStartPositions() {
            val formatter = TextWithChanges("while( true){foo( );}")
            val change = formatter.addChange(RangeInResult(PositionInResult.Unchanged(6u), PositionInResult.Unchanged(7u)), "")
            formatter.addChange(RangeInResult(PositionInResult.Unchanged(6u), PositionInResult.Changed(change, 0u)), "\n ")

            assertEquals("while(\n true){foo( );}", formatter.applyChanges())
        }

        @Test
        fun normalizesEndPositions() {
            val formatter = TextWithChanges("while( true){foo( );}")
            val change = formatter.addChange(RangeInResult(PositionInResult.Unchanged(6u), PositionInResult.Unchanged(7u)), "")
            formatter.addChange(RangeInResult(PositionInResult.Changed(change, 0u), PositionInResult.Unchanged(7u)), "\n ")

            assertEquals("while(\n true){foo( );}", formatter.applyChanges())
        }

        @Test
        fun cannotIntroduceNonWhitespace() {
            val formatter = TextWithChanges("")

            assertThrows<AssertionError> {
                formatter.addChange(RangeInResult(PositionInResult.Unchanged(0u), PositionInResult.Unchanged(0u)), "whoops")
            }
        }

        @Test
        fun cannotIntersectAChange() {
            val formatter = TextWithChanges("  ")

            formatter.addChange(RangeInResult(PositionInResult.Unchanged(0u), PositionInResult.Unchanged(1u)), "  ")

            assertThrows<AssertionError> {
                formatter.addChange(RangeInResult(PositionInResult.Unchanged(0u), PositionInResult.Unchanged(2u)), " ")
            }
        }

        @Test
        fun newChangeWithSameRangeOverrides() {
            val formatter = TextWithChanges("  ")

            formatter.addChange(RangeInResult(PositionInResult.Unchanged(0u), PositionInResult.Unchanged(1u)), "  ")
            val change = formatter.addChange(RangeInResult(PositionInResult.Unchanged(0u), PositionInResult.Unchanged(1u)), "\t")

            assertEquals(TextChange(TextRange(0u, 1u), FormattingReplacement("\t")), change)
            assertEquals("\t ", formatter.applyChanges())
        }

        @Test
        fun newChangeWithIncludingRangeAlters() {
            val formatter = TextWithChanges("   ")

            val change1 = formatter.addChange(RangeInResult(PositionInResult.Unchanged(0u), PositionInResult.Unchanged(2u)), "\r\t\r")
            val change2 =
                formatter.addChange(
                    RangeInResult(PositionInResult.Changed(change1, 1u), PositionInResult.Changed(change1, 2u)),
                    "\r",
                )

            assertEquals(TextChange(TextRange(0u, 2u), FormattingReplacement("\r\r\r")), change2)
            assertEquals("\r\r\r ", formatter.applyChanges())
        }

        @Test
        fun cannotMergeEditNonWhitespaceLeft() {
            val formatter = TextWithChanges("a  b")

            formatter.addChange(RangeInResult(PositionInResult.Unchanged(1u), PositionInResult.Unchanged(2u)), "\n")

            assertThrows<AssertionError> {
                formatter.addChange(RangeInResult(PositionInResult.Unchanged(2u), PositionInResult.Unchanged(4u)), "\t")
            }
        }

        @Test
        fun newChangeTouchingToTheLeftIsMerged() {
            val formatter = TextWithChanges("a  b")

            formatter.addChange(RangeInResult(PositionInResult.Unchanged(1u), PositionInResult.Unchanged(2u)), "\n")
            val change = formatter.addChange(RangeInResult(PositionInResult.Unchanged(2u), PositionInResult.Unchanged(3u)), "\t")

            assertEquals(TextChange(TextRange(1u, 3u), FormattingReplacement("\n\t")), change)
            assertEquals("a\n\tb", formatter.applyChanges())
        }

        @Test
        fun cannotMergeEditNonWhitespaceRight() {
            val formatter = TextWithChanges("a  b")

            formatter.addChange(RangeInResult(PositionInResult.Unchanged(2u), PositionInResult.Unchanged(3u)), "\n")

            assertThrows<AssertionError> {
                formatter.addChange(RangeInResult(PositionInResult.Unchanged(0u), PositionInResult.Unchanged(2u)), "\t")
            }
        }

        @Test
        fun newChangeTouchingToTheRightIsMerged() {
            val formatter = TextWithChanges("a  b")

            formatter.addChange(RangeInResult(PositionInResult.Unchanged(2u), PositionInResult.Unchanged(3u)), "\n")
            val change = formatter.addChange(RangeInResult(PositionInResult.Unchanged(1u), PositionInResult.Unchanged(2u)), "\t")

            assertEquals(TextChange(TextRange(1u, 3u), FormattingReplacement("\t\n")), change)
            assertEquals("a\t\nb", formatter.applyChanges())
        }

        @Test
        fun mergingChangeWithEmpty() {
            val formatter = TextWithChanges("a  b")

            formatter.addChange(RangeInResult(PositionInResult.Unchanged(2u), PositionInResult.Unchanged(2u)), "\n")
            val change = formatter.addChange(RangeInResult(PositionInResult.Unchanged(1u), PositionInResult.Unchanged(2u)), "\t")

            assertEquals(TextChange(TextRange(1u, 2u), FormattingReplacement("\t\n")), change)
            assertEquals("a\t\n b", formatter.applyChanges())
        }

        @Test
        fun mergingFromBothSides() {
            val formatter = TextWithChanges("a   b")

            formatter.addChange(RangeInResult(PositionInResult.Unchanged(1u), PositionInResult.Unchanged(2u)), "\n")
            formatter.addChange(RangeInResult(PositionInResult.Unchanged(3u), PositionInResult.Unchanged(4u)), "\n")

            val change = formatter.addChange(RangeInResult(PositionInResult.Unchanged(2u), PositionInResult.Unchanged(3u)), "\t")

            assertEquals(TextChange(TextRange(1u, 4u), FormattingReplacement("\n\t\n")), change)
            assertEquals("a\n\t\nb", formatter.applyChanges())
        }
    }

    @Nested
    inner class CountLineBreaks {
        @Test
        fun unchangedWholeString() {
            val formatter = TextWithChanges("a\nb\nc")

            assertEquals(2, formatter.countLineBreaks(RangeInResult(PositionInResult.Unchanged(0u), PositionInResult.Unchanged(5u))))
        }

        @Test
        fun emptyString() {
            val formatter = TextWithChanges("")

            assertEquals(0, formatter.countLineBreaks(RangeInResult(PositionInResult.Unchanged(0u), PositionInResult.Unchanged(0u))))
        }

        @Test
        fun noNewLines() {
            val formatter = TextWithChanges("a")

            assertEquals(0, formatter.countLineBreaks(RangeInResult(PositionInResult.Unchanged(0u), PositionInResult.Unchanged(1u))))
        }

        @Test
        fun inSubstrings() {
            val formatter = TextWithChanges("a\n")

            assertEquals(0, formatter.countLineBreaks(RangeInResult(PositionInResult.Unchanged(0u), PositionInResult.Unchanged(1u))))
            assertEquals(1, formatter.countLineBreaks(RangeInResult(PositionInResult.Unchanged(1u), PositionInResult.Unchanged(2u))))
        }

        @Test
        fun countsLineInChanges() {
            val formatter = TextWithChanges("a\nb\nc")
            formatter.addChange(RangeInResult(PositionInResult.Unchanged(1u), PositionInResult.Unchanged(2u)), "\n\n")

            assertEquals(3, formatter.countLineBreaks(RangeInResult(PositionInResult.Unchanged(0u), PositionInResult.Unchanged(5u))))
        }

        @Test
        fun startingFromChange() {
            val formatter = TextWithChanges(" \nb\nc")
            val change = formatter.addChange(RangeInResult(PositionInResult.Unchanged(0u), PositionInResult.Unchanged(2u)), "\r\n ")

            assertEquals(
                0,
                formatter.countLineBreaks(RangeInResult(PositionInResult.Changed(change, 2u), PositionInResult.Unchanged(3u))),
            )
        }

        @Test
        fun endingInChange() {
            val formatter = TextWithChanges("aa\n\nb\nc")
            val change = formatter.addChange(RangeInResult(PositionInResult.Unchanged(2u), PositionInResult.Unchanged(4u)), "\n")

            assertEquals(
                1,
                formatter.countLineBreaks(RangeInResult(PositionInResult.Unchanged(1u), PositionInResult.Changed(change, 1u))),
            )
        }

        @Test
        fun startAndEndInSameChange() {
            val formatter = TextWithChanges("a\nb\nc")
            val change = formatter.addChange(RangeInResult(PositionInResult.Unchanged(1u), PositionInResult.Unchanged(2u)), "\n\n")

            assertEquals(
                1,
                formatter.countLineBreaks(RangeInResult(PositionInResult.Changed(change, 1u), PositionInResult.Changed(change, 2u))),
            )
        }
    }

    @Nested
    inner class CountSimpleSpaces {
        @Test
        fun noChanges() {
            val formatter = TextWithChanges(" a\nb\nc")

            assertEquals(
                TextWithChanges.SimpleSpacesCount(1, 0, 4),
                formatter.countSimpleSpaces(RangeInResult(PositionInResult.Unchanged(0u), PositionInResult.Unchanged(6u)), 1000),
            )
        }

        @Test
        fun visualSpaceForTabs() {
            val source = "\t\na\t\naa\t\naaa\t\naaaa\t"
            val formatter = TextWithChanges(source)

            assertEquals(
                TextWithChanges.SimpleSpacesCount(0, 5, 24),
                formatter.countSimpleSpaces(
                    RangeInResult(PositionInResult.Unchanged(0u), PositionInResult.Unchanged(source.length.toUInt())),
                    4,
                ),
            )
        }

        @Test
        fun visualSpaceForTabsWithCRLF() {
            val source = "\t\na\t\r\naa\t\naaa\t\r\naaaa\t"
            val formatter = TextWithChanges(source)

            assertEquals(
                TextWithChanges.SimpleSpacesCount(0, 5, 24),
                formatter.countSimpleSpaces(
                    RangeInResult(PositionInResult.Unchanged(0u), PositionInResult.Unchanged(source.length.toUInt())),
                    4,
                ),
            )
        }

        @Test
        fun visualSpaceForTabsWithChanges() {
            val source = "\t\na\t\naa\t\naaa\t\naaaa\t"
            val formatter = TextWithChanges(source)
            formatter.addChange(RangeInResult(PositionInResult.Unchanged(1u), PositionInResult.Unchanged(1u)), "\n \n")

            assertEquals(
                TextWithChanges.SimpleSpacesCount(1, 5, 25),
                formatter.countSimpleSpaces(
                    RangeInResult(PositionInResult.Unchanged(0u), PositionInResult.Unchanged(source.length.toUInt())),
                    4,
                ),
            )
        }
    }

    @Nested
    inner class Search {
        @Test
        fun findsInSingleChange() {
            val formatter = TextWithChanges("a\n\n\nc")
            val change = formatter.addChange(RangeInResult(PositionInResult.Unchanged(1u), PositionInResult.Unchanged(4u)), "  \n\n")

            assertEquals(
                TextWithChanges.SearchResult(PositionInResult.Changed(change, 2u), TextWithChanges.FoundObjectKind.LINE_BREAK),
                formatter.search(
                    RangeInResult(PositionInResult.Unchanged(0u), PositionInResult.Unchanged(5u)),
                    TextWithChanges.SearchDirection.FRONT_TO_BACK,
                    TextWithChanges.SearchKind.LINE_BREAK,
                ),
            )
        }

        @Test
        fun findsInSource() {
            val formatter = TextWithChanges("\n\n\n\n\n\nc")
            formatter.addChange(RangeInResult(PositionInResult.Unchanged(1u), PositionInResult.Unchanged(4u)), "  ")

            assertEquals(
                TextWithChanges.SearchResult(PositionInResult.Unchanged(6u), TextWithChanges.FoundObjectKind.NON_WHITESPACE),
                formatter.search(
                    RangeInResult(PositionInResult.Unchanged(0u), PositionInResult.Unchanged(7u)),
                    TextWithChanges.SearchDirection.FRONT_TO_BACK,
                    TextWithChanges.SearchKind.NON_WHITESPACE,
                ),
            )
        }

        @Test
        fun doesNotFindOutsideOfRange() {
            val formatter = TextWithChanges("\n\n\n\n\n\nc")
            formatter.addChange(RangeInResult(PositionInResult.Unchanged(1u), PositionInResult.Unchanged(4u)), "  ")

            assertEquals(
                null,
                formatter.search(
                    RangeInResult(PositionInResult.Unchanged(0u), PositionInResult.Unchanged(6u)),
                    TextWithChanges.SearchDirection.FRONT_TO_BACK,
                    TextWithChanges.SearchKind.NON_WHITESPACE,
                ),
            )
        }

        @Test
        fun bothFindsFirst() {
            val formatter = TextWithChanges("a  a\n")

            assertEquals(
                TextWithChanges.SearchResult(PositionInResult.Unchanged(3u), TextWithChanges.FoundObjectKind.NON_WHITESPACE),
                formatter.search(
                    RangeInResult(PositionInResult.Unchanged(1u), PositionInResult.Unchanged(4u)),
                    TextWithChanges.SearchDirection.FRONT_TO_BACK,
                    TextWithChanges.SearchKind.BOTH,
                ),
            )
        }

        @Test
        fun backToFrontFindsLast() {
            val formatter = TextWithChanges("a\n  a")

            assertEquals(
                TextWithChanges.SearchResult(PositionInResult.Unchanged(1u), TextWithChanges.FoundObjectKind.LINE_BREAK),
                formatter.search(
                    RangeInResult(PositionInResult.Unchanged(0u), PositionInResult.Unchanged(3u)),
                    TextWithChanges.SearchDirection.BACK_TO_FRONT,
                    TextWithChanges.SearchKind.BOTH,
                ),
            )
        }
    }

    @Nested
    inner class ApplyChanges {
        @Test
        fun returnsSourceStringForNoChanges() {
            val source = "while( true){foo( );}"
            val formatter = TextWithChanges(source)

            assertEquals(source, formatter.applyChanges())
        }

        @Test
        fun doesDeletion() {
            val formatter = TextWithChanges("while( true){foo( );}")
            formatter.addChange(RangeInResult(PositionInResult.Unchanged(6u), PositionInResult.Unchanged(7u)), "")

            assertEquals("while(true){foo( );}", formatter.applyChanges())
        }

        @Test
        fun doesInsertion() {
            val formatter = TextWithChanges("while( true){foo( );}")
            formatter.addChange(RangeInResult(PositionInResult.Unchanged(13u), PositionInResult.Unchanged(13u)), "\n\t")

            assertEquals("while( true){\n\tfoo( );}", formatter.applyChanges())
        }

        @Test
        fun doesExpansion() {
            val formatter = TextWithChanges("while(true) { foo( );}")
            formatter.addChange(RangeInResult(PositionInResult.Unchanged(13u), PositionInResult.Unchanged(14u)), "    ")

            assertEquals("while(true) {    foo( );}", formatter.applyChanges())
        }

        @Test
        fun doesShrinking() {
            val formatter = TextWithChanges("while     (true) { foo(); }")
            formatter.addChange(RangeInResult(PositionInResult.Unchanged(5u), PositionInResult.Unchanged(10u)), " ")

            assertEquals("while (true) { foo(); }", formatter.applyChanges())
        }

        @Test
        fun handlesWholeReplacement() {
            val formatter = TextWithChanges("  ")
            formatter.addChange(RangeInResult(PositionInResult.Unchanged(0u), PositionInResult.Unchanged(2u)), " ")

            assertEquals(" ", formatter.applyChanges())
        }
    }
}
