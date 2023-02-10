package co.pvphub.pool.plugins.builtin

import co.pvphub.pool.Pool
import co.pvphub.pool.plugins.PoolPlugin
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.ServerInfo

class VelocityPoolPlugin(private val proxyServer: ProxyServer): PoolPlugin() {
    val runningServices = mutableMapOf<Pool.Service, ServerInfo>()
    override fun onServiceCreate(service: Pool.Service) {
        val alloc = service.server.getSocketAddress()
        val info = ServerInfo(
            "${service.template.id}-${pool.getServicesRunningByTemplate(service.template).size + 1}",
            alloc
        )
        proxyServer.registerServer(info)
        runningServices += service to info
    }

    override fun onServiceEnd(service: Pool.Service) {
        val info = runningServices[service]
        proxyServer.unregisterServer(info)
        runningServices -= service
    }
}