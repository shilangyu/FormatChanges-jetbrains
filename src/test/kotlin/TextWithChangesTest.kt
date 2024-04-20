import org.example.PositionInResult
import org.example.RangeInResult
import org.example.TextWithChanges
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TextWithChangesTest {
    @Test
    fun addChange() {
    }

    @Nested
    inner class ApplyChanges {
        @Test
        fun returnsSourceStringForNoChanges() {
            val source = "while( true){foo( );}"
            val formatter = TextWithChanges(source)

            assertEquals(formatter.applyChanges(), source)
        }

        @Test
        fun doesDeletion() {
            val formatter = TextWithChanges("while( true){foo( );}")
            formatter.addChange(RangeInResult(PositionInResult.Unchanged(6u), PositionInResult.Unchanged(7u)), "")

            assertEquals(formatter.applyChanges(), "while(true){foo( );}")
        }

        @Test
        fun doesInsertion() {
            val formatter = TextWithChanges("while( true){foo( );}")
            formatter.addChange(RangeInResult(PositionInResult.Unchanged(13u), PositionInResult.Unchanged(13u)), "\n\t")

            assertEquals(formatter.applyChanges(), "while( true){\n\tfoo( );}")
        }

        @Test
        fun doesExpansion() {
            val formatter = TextWithChanges("while(true) { foo( );}")
            formatter.addChange(RangeInResult(PositionInResult.Unchanged(13u), PositionInResult.Unchanged(14u)), "    ")

            assertEquals(formatter.applyChanges(), "while(true) {    foo( );}")
        }

        @Test
        fun doesShrinking() {
            val formatter = TextWithChanges("while     (true) { foo(); }")
            formatter.addChange(RangeInResult(PositionInResult.Unchanged(5u), PositionInResult.Unchanged(10u)), " ")

            assertEquals(formatter.applyChanges(), "while (true) { foo(); }")
        }
    }
}
