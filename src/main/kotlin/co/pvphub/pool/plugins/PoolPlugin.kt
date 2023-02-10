package co.pvphub.pool.plugins

import co.pvphub.pool.Pool

abstract class PoolPlugin {
    val pool = Pool.instance!!
    val ptero = Pool.instance!!.client
    /* no-op */ open fun enable() {}
    /* no-op */ open fun onServiceCreate(s: Pool.Service) {}
    /* no-op */ open fun onServiceEnd(s: Pool.Service) {}
}