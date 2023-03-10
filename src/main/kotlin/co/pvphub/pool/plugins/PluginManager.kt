package co.pvphub.pool.plugins

import co.pvphub.pool.Pool

object PluginManager {
    val pool = Pool.instance!!
    private val plugins = mutableListOf<PoolPlugin>()

    /**
     * Adds a PoolPlugin to the running instance of pool.
     * @param plugin The plugin
     */
    fun addPlugin(plugin: PoolPlugin) {
        plugins.add(plugin)
    }

    fun callServiceCreate(s: Pool.Service) {
        plugins.forEach { it.onServiceCreate(s) }
    }
    fun callServiceEnd(s: Pool.Service) {
        plugins.forEach { it.onServiceEnd(s) }
    }
}