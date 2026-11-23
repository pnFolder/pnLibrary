package org.pnfolder.pnlibrary.core.scenario

/** One named action, requirement or function as an administrator sees it. [kind] is `action`, `requirement` or `function`. */
data class ScenarioDefinitionInfo(val kind: String, val name: String, val owner: String, val visibility: String)
