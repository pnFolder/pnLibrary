# Component Metadata Design

## Goal

Keep release metadata inside the JAR and make compatibility checks explicit without requiring a separate `pn-update.json` file.

## Embedded schema

Every distributable JAR may contain `META-INF/pnlibrary/component.json`:

```json
{
  "schema": 1,
  "product": "pnlibrary",
  "version": "2.3.0",
  "channel": "stable",
  "pnLibraryApi": { "minimum": 1, "maximum": 1 },
  "java": { "minimum": 8, "maximum": null }
}
```

`version` is a complete semantic version. Beta builds use `2.3.0-beta.1`, `2.3.0-beta.2`, and so on. `channel` remains explicit for filtering. A missing or `null` maximum means no upper bound.

Platform and Minecraft ranges are optional extensions and are emitted only when a platform-specific artifact needs them. Proxy artifacts do not require Minecraft ranges.

## Release flow

GitHub Releases provide release names, asset names, sizes, and digests. The updater selects an asset by product/platform/Java rules, verifies the downloaded bytes against GitHub's digest, then reads and validates the embedded descriptor. `pn-update.json` is not required.

## Compatibility

The embedded descriptor remains backward-compatible with existing `component` metadata during migration. New generated metadata uses `product`; readers accept both forms for one compatibility cycle.
