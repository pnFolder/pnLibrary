# Remote Check for Bukkit

This module is for a replaceable policy class stored as a verified remote `.class` file. The consuming plugin keeps only the URL, SHA-256, and class name. On each server start and then every six hours the file is downloaded, checked for Java bytecode compatibility, loaded, and executed. A JAR is also accepted for policies with helper classes.

```java
RemoteCheckRunner.schedule(this,
    RemoteCheckOptions.builder(
        "https://github.com/pnFolder/policies/releases/download/latest/policy.jar",
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "ru.example.policy.CurrentPolicy"
    ).value("mode", "production").build());
```

Or specify a GitHub owner, repository, and release asset directly:

```java
RemoteCheckRunner.schedule(this,
    RemoteCheckOptions.github(
        "pnFolder", "policies", "policy.jar",
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "ru.example.policy.CurrentPolicy"
    ).build());
```

The remote class implements the stable interface:

```java
public final class CurrentPolicy implements RemoteCheck {
    public RemoteCheckResult check(RemoteCheckContext context) {
        if (context.pluginVersion().startsWith("1.")) {
            return RemoteCheckResult.deny("эта версия плагина больше не поддерживается");
        }
        return RemoteCheckResult.allow();
    }
}
```

The SHA-256 must be updated together with the remote JAR. If the download, hash, class loading, or policy execution fails, the consuming plugin is disabled. This is intentionally a fail-closed mechanism: only use URLs and hashes controlled by the plugin owner.
