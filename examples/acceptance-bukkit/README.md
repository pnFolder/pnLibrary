# pnLibrary Bukkit acceptance plugin

This module is a manual end-to-end test client for a real Bukkit/Paper server. It is intentionally
separate from `demo-bukkit`: the demo shows integration style, while this plugin exposes switches
and commands for verifying pnLibrary subsystems one by one.

Build both runnable JARs:

```text
gradlew.bat :examples:acceptance-bukkit:assembleAcceptanceKit
```

The ready-to-copy pair is placed in the root `build/acceptance-bukkit` directory. Copy both JARs
from there into the server's `plugins` directory:

- `distribution/build/libs/pnLibrary-<version>-bukkit-java8.jar`
- `examples/acceptance-bukkit/build/libs/pnLibrary-acceptance-bukkit-<version>.jar`

Start the server and run `/pnaccept status`. Optional network-facing checks are disabled in
`plugins/pnLibraryAcceptance/config.yml`; enable one at a time and restart the server.

Useful checks:

- `/pnaccept event` — event bus;
- `/pnaccept task` — unified scheduler;
- `/pnaccept cooldown` twice — cooldown state;
- `/pnaccept metrics` — metrics lifecycle;
- `/pnaccept diagnostics`, then `/pndebug acceptance --local` — diagnostic collection;
- `/pnaccept update` — updater, after enabling it in `config.yml`;
- `/pnaccept download` — auxiliary file delivery, after enabling it in `config.yml`.
# Remote policy acceptance check

Ready-to-upload Java and Kotlin policies are located in `policies/AcceptancePolicy.java`
and `policies/AcceptancePolicy.kt`.
It requires plugin version `2.3.0`, so the current `2.2.0-beta.2` acceptance plugin is denied.

For a local check, enable the feature in `config.yml` and use an absolute file URL:

```yaml
remote-policy:
  enabled: true
  on-deny: DISABLE_PLUGIN
  source: file:///C:/path/to/AcceptancePolicy.java
```

For GitHub, upload the same file and use its raw `https://raw.githubusercontent.com/.../AcceptancePolicy.java` URL.
