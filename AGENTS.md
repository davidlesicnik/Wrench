# Repository Guidelines

## Project Structure & Module Organization
- Root project uses a single Android module: `:app`.
- Main code lives in `app/src/main/java/com/lesicnik/wrench`.
- UI code is under `ui/` (screens, navigation, theme, reusable components).
- Data and networking are under `data/` (`remote/`, `repository/`, and expense mappers/calculators).
- Android resources live in `app/src/main/res`.
- Unit tests are in `app/src/test/java`; instrumented/device tests are in `app/src/androidTest/java`.

## Build, Test, and Development Commands
- `./gradlew :app:assembleDebug` builds a local debug APK.
- `./gradlew :app:testDebugUnitTest` runs JVM unit tests (JUnit/Robolectric).
- `./gradlew :app:connectedDebugAndroidTest` runs instrumented tests on a connected device/emulator.
- `./gradlew :app:compileDebugKotlin :app:compileDebugJavaWithJavac` performs a fast compile check (used in CI/CodeQL).
- Release artifacts are CI-oriented; `release` variants are gated for GitHub Actions and signing secrets.

## Coding Style & Naming Conventions
- Kotlin style is `official` (`gradle.properties`); use 4-space indentation and no tabs.
- Follow standard Kotlin naming: packages lowercase, classes/objects `PascalCase`, functions/variables `camelCase`.
- Keep feature UI files named by role, e.g., `ExpensesScreen.kt`, `ExpensesViewModel.kt`, `ExpenseRepository.kt`.
- Prefer small, focused classes and keep data-layer parsing/mapping logic in repository subpackages.

## Testing Guidelines
- Frameworks in use: JUnit4, Robolectric, AndroidX test, Espresso, and Compose UI test.
- Test class naming: `<UnitUnderTest>Test` for unit tests; `<UnitUnderTest>InstrumentedTest` for device tests.
- Descriptive backtick test names are encouraged (example: ``fun `parseDate handles ISO format`()``).
- No strict coverage gate is configured; add tests for parsing, repository behavior, and regressions.

## Commit & Pull Request Guidelines
- Recent history favors short, imperative commit subjects (for example: `fix CI build error`, `Code de-duplication`).
- Keep commits scoped to one change; avoid mixing refactors with behavior fixes.
- PRs should include: concise summary, linked issue (if any), test evidence (`testDebugUnitTest` output), and screenshots for UI changes.
- If a PR prepares a release, update `versionCode` and `versionName` in `app/build.gradle.kts`.

## Security & Configuration Tips
- Never commit secrets or keystores; `local.properties` is ignored for local signing config.
- CI release signing uses `RELEASE_KEYSTORE_*` environment variables/GitHub secrets.
- Keep API keys out of source code; runtime credentials are stored via `EncryptedSharedPreferences`.
