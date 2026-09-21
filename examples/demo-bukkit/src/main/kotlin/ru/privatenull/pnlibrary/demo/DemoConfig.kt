package ru.privatenull.pnlibrary.demo

import ru.privatenull.pnlibrary.api.config.ConfigRange
import ru.privatenull.pnlibrary.api.config.ConfigNotBlank
import ru.privatenull.pnlibrary.api.config.ConfigScope
import ru.privatenull.pnlibrary.api.config.ManagedConfig
import java.util.function.Supplier

data class DemoSettings(
    @ConfigRange(min = 0.0) var startingBalance: Double = 100.0,
    @ConfigNotBlank var greeting: String = "Добро пожаловать в pnDemo!",
)

object DemoConfig {
    fun register(context: ru.privatenull.pnlibrary.api.plugin.PluginContext): ManagedConfig<DemoSettings> {
        val scope: ConfigScope = context.configs
        val config = scope.yaml("demo-settings.yml", DemoSettings::class.java, Supplier { DemoSettings() })
        return config
    }
}
