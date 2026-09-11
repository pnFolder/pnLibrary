# Release checklist

## Required before versioning

- The working tree contains no accidental files or unrelated staged changes.
- `CHANGELOG.md` describes every public API change and compatibility break.
- `README.md`, `HELP-README.md`, and feature documentation use the release API.
- No public example references removed or transitional classes as the preferred API.
- All dynamic registrations are removed when their owning plugin context closes.
- Bukkit, BungeeCord, and Velocity compile on the configured toolchain.
- Distribution JARs contain the correct descriptors and version.
- API and Bukkit API source JARs are produced.

## Action-system blocker

Do not mark the typed action system stable while `PluginContext.actions` still
exposes the older `PlayerActionService`. The typed `Action` model currently has
configuration support, but its cross-platform execution facade is not yet the
single public entry point. Finish that migration or explicitly label the typed
API experimental for the next beta.

## Maven Central blocker

The release workflow checks for `publishAndReleaseToMavenCentral`, but the
project does not currently define that Gradle task. Keep `publish_central`
disabled until the publishing plugin, verified Maven Central namespace, POM
metadata, signing key, and credentials are configured. GitHub release artifacts
can still be produced independently.

## Release workflow

1. Choose the next semantic version.
2. Update `gradle.properties` and documentation through the release script.
3. Review the generated diff.
4. Build all distribution artifacts.
5. Push the release commit and annotated `v<version>` tag.
6. Let `.github/workflows/release.yml` create the GitHub release.
7. Publish the public API artifacts to Maven Central only when signing and
   Central credentials are configured.
8. Download each uploaded JAR and verify its descriptor and filename.

The GitHub workflow is the release authority. Local scripts prepare and inspect
the version; they do not silently publish a release from a developer machine.
