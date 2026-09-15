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

## Action system

- `PluginContext.actions` exposes only `ActionService`.
- No release may restore `PlayerAction`, a handwritten action serializer, or a
  second action registry.
- Built-in and custom actions must use the same polymorphic configuration path.

## Maven Central

The public `pnlibrary-api` module defines `publishAndReleaseToMavenCentral` through
the Vanniktech publishing plugin. A release requires the Central credentials,
signing key, and license variables documented in `PUBLISHING.md`.

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
