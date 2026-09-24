# pnLibrary component metadata

Build integrations that create `META-INF/pnlibrary/component.json` inside a plugin JAR. pnLibrary
reads this passive resource before installing an update; it never loads code from the downloaded JAR.

The implementation is split into three modules:

- `core` validates and writes the canonical descriptor;
- `gradle` supports both Kotlin and Groovy Gradle DSL;
- `maven` provides the `generate` Maven goal bound to `process-resources`.

The component version defaults to the Gradle/Maven project version. The component ID defaults to the
normalized Gradle project name or Maven `artifactId`. Configure it explicitly when the native plugin
ID differs. See `docs/updates.md` for complete examples.
