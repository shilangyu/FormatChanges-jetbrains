# FormatChanges

Run tests with `./gradlew test` and check formatting with `./gradlew ktlintCheck`.

## Project structure

- `src/main/kotlin/TextWithChanges.kt` - implementation of the `TextWithChanges` class for managing whitespace changes of some source code
- `src/test/kotlin/TextWithChangesTest.kt` - unit tests for all methods of `TextWithChanges`
- `.github/workflows/ci.yml` - CI for enforcing passing of tests and code style
