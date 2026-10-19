package org.pnfolder.pnlibrary.demo

import org.pnfolder.pnlibrary.api.config.ConfigNotBlank
import org.pnfolder.pnlibrary.api.config.ConfigRange
import org.pnfolder.pnlibrary.api.config.ConfigScope
import org.pnfolder.pnlibrary.api.config.ManagedConfig
import org.pnfolder.pnlibrary.api.plugin.ModuleContext
import java.util.function.Supplier

data class DemoSettings(
    @ConfigRange(min = 0.0)
    var startingBalance: Double = 100.0,
    @ConfigNotBlank
    var greeting: String = "Добро пожаловать в pnDemo!",
)

object DemoConfig {
    fun register(context: ModuleContext): ManagedConfig<DemoSettings> {
        val scope: ConfigScope = context.configs
        val config = scope.yaml("demo-settings.yml", DemoSettings::class.java, Supplier { DemoSettings() })
        return config
    }
}
