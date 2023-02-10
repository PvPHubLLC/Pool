package co.pvphub.pool.plugins.builtin

import co.pvphub.pool.Pool
import co.pvphub.pool.plugins.PoolPlugin
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.ServerInfo

class VelocityPoolPlugin(private val proxyServer: ProxyServer): PoolPlugin() {
    val runningServices = mutableMapOf<Pool.Service, ServerInfo>()
    override fun onServiceCreate(s: Pool.Service) {
        val alloc = s.server.getSocketAddress()
        val info = ServerInfo(
            "${s.template.id}-${pool.getServicesRunningByTemplate(s.template).size + 1}",
            alloc
        )
        proxyServer.registerServer(info)
        runningServices += s to info
    }

    override fun onServiceEnd(s: Pool.Service) {
        val info = runningServices[s]
        proxyServer.unregisterServer(info)
        runningServices -= s
    }
}