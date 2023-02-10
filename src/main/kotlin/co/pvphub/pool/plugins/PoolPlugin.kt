package co.pvphub.pool.plugins

import co.pvphub.pool.Pool

abstract class PoolPlugin {
    val pool = Pool.instance!!
    val ptero = Pool.instance!!.client

    /**
     * Gets called when the plugin is added/enabled
     */
    /* no-op */ open fun enable() {}

    /**
     * Gets called when a service is created, you can assume the pterodactyl server is created by now.
     * @param service The service
     */
    /* no-op */ open fun onServiceCreate(service: Pool.Service) {}

    /**
     * Gets called when a service is destroyed, the pterodactyl server will be deleted by now.
     * @param service The service
     */
    /* no-op */ open fun onServiceEnd(service: Pool.Service) {}
}