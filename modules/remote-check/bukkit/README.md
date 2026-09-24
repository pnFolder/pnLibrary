# Remote Check for Bukkit

This module is for a replaceable policy class stored at a remote URL. The consuming plugin keeps only the URL and class name. On each server start and then every six hours the file is downloaded, checked for Java bytecode compatibility, loaded, and executed. A JAR is also accepted for policies with helper classes.

```java
RemoteCheckRunner.schedule(this,
    RemoteCheckOptions.builder(
        "https://github.com/pnFolder/policies/releases/download/latest/policy.jar",
        "ru.example.policy.CurrentPolicy"
    ).value("mode", "production").build());
```

Or specify a GitHub owner, repository, and release asset directly:

```java
RemoteCheckRunner.schedule(this,
    RemoteCheckOptions.github(
        "pnFolder", "policies", "policy.jar",
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

If the download, class loading, Java compatibility check, or policy execution fails, the consuming plugin is disabled. The URL is trusted directly, so use only a source controlled by the plugin owner.
