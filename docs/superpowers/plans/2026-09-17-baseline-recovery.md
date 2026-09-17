# Baseline Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore a clean compiling and tested baseline after the latest commit added local placeholder rendering without its parser.

**Architecture:** Keep parsing as a small immutable internal value type in the placeholders feature package. `PlaceholderHub` consumes the parsed reference and formatter list; parsing has no registry, platform, or asynchronous responsibilities.

**Tech Stack:** Kotlin, Gradle, JUnit Jupiter

**Spec:** `docs/superpowers/specs/2026-09-17-pnlibrary-architecture-and-pnupdate-design.md`

## Global Constraints

- Preserve the documented single-bracket syntax `[name|formatter:argument]`.
- Registered placeholders retain `{name}` syntax.
- Do not move feature code during baseline recovery.
- Production behavior must be introduced by a failing test first.
- The phase is complete only when `clean test :pnlibrary-distribution:build` passes.

---

### Task 1: Local placeholder expression parser

**Files:**
- Create: `pnlibrary-core/src/test/kotlin/ru/privatenull/pnlibrary/core/placeholders/LocalPlaceholderExpressionTest.kt`
- Create: `pnlibrary-core/src/main/kotlin/ru/privatenull/pnlibrary/core/placeholders/LocalPlaceholderExpression.kt`

**Interfaces:**
- Consumes: bracket tokens discovered by `PlaceholderHub.renderFor`.
- Produces: `LocalPlaceholderExpression.parse(token): LocalPlaceholderExpression?`, `reference: String`, and `formatters: List<String>`.

- [ ] **Step 1: Write parser tests**

```kotlin
class LocalPlaceholderExpressionTest {
    @Test
    fun `parses local reference and formatter pipeline`() {
        val expression = requireNotNull(LocalPlaceholderExpression.parse("[player-level|default:0|upper]"))
        assertEquals("player-level", expression.reference)
        assertEquals(listOf("default:0", "upper"), expression.formatters)
    }

    @Test
    fun `rejects malformed or empty local expressions`() {
        listOf("player", "[]", "[ ]", "[|upper]", "[[player]]", "[player").forEach {
            assertNull(LocalPlaceholderExpression.parse(it), it)
        }
    }
}
```

- [ ] **Step 2: Run the focused test and observe RED**

Run: `./gradlew :pnlibrary-core:test --tests '*LocalPlaceholderExpressionTest'`

Expected: compilation fails because `LocalPlaceholderExpression` is unresolved in both the new test and existing `PlaceholderHub`.

- [ ] **Step 3: Implement the minimal immutable parser**

```kotlin
internal data class LocalPlaceholderExpression(
    val reference: String,
    val formatters: List<String>,
) {
    companion object {
        fun parse(token: String): LocalPlaceholderExpression? {
            if (token.length < 3 || token.first() != '[' || token.last() != ']') return null
            val body = token.substring(1, token.length - 1)
            if ('[' in body || ']' in body) return null
            val parts = body.split('|').map(String::trim)
            val reference = parts.firstOrNull().orEmpty()
            if (reference.isEmpty()) return null
            return LocalPlaceholderExpression(reference, parts.drop(1).filter(String::isNotEmpty))
        }
    }
}
```

- [ ] **Step 4: Run the focused test and observe GREEN**

Run: `./gradlew :pnlibrary-core:test --tests '*LocalPlaceholderExpressionTest'`

Expected: all parser tests pass.

- [ ] **Step 5: Commit the isolated regression fix**

```bash
git add pnlibrary-core/src/main/kotlin/ru/privatenull/pnlibrary/core/placeholders/LocalPlaceholderExpression.kt pnlibrary-core/src/test/kotlin/ru/privatenull/pnlibrary/core/placeholders/LocalPlaceholderExpressionTest.kt
git commit -m "fix: restore local placeholder expression parsing"
```

### Task 2: Full baseline verification

**Files:**
- No files are expected to change.

**Interfaces:**
- Consumes: all current Gradle modules and distribution tasks.
- Produces: a reproducible green baseline for later migrations.

- [ ] **Step 1: Run complete verification**

Run: `./gradlew clean test :pnlibrary-distribution:build --warning-mode all`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Diagnose any newly exposed failure**

If the command fails, stop this plan at the first newly exposed error and create a
separate diagnosis and regression-test task for that concrete failure before editing
production code.

- [ ] **Step 3: Record the verified baseline**

Record the command, successful exit code, executed test tasks, and distribution
artifacts in the phase completion note. Do not create a verification-only commit.
