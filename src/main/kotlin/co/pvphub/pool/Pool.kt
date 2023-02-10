package co.pvphub.pool

import co.pvphub.pool.plugins.PluginManager
import co.pvphub.pool.ptero.HttpClient
import co.pvphub.pool.ptero.modules.Servers
import com.electronwill.nightconfig.core.Config
import com.electronwill.nightconfig.toml.TomlParser
import com.electronwill.nightconfig.toml.TomlWriter
import com.google.gson.Gson
import java.io.ByteArrayInputStream
import java.lang.Thread.sleep

class Pool(pteroHost: String, appToken: String, clientToken: String) {
    val client = HttpClient(appToken, clientToken, pteroHost)
    private val gson = Gson()
    private val poolMaster: Servers.Server = client.server.getServerList().find { it.name == "[pvphub-pool]" }!!
    private val templates: List<Template> = poolMaster.getFilesInDirectory("templates").filter { it.endsWith(".json") }.map { gson.fromJson(poolMaster.getFile("templates/$it"), Template::class.java) }
    init {
        if(instance != null) throw PoolException("Cannot initialize multiple instances of pool, use Pool.get for proper initialization!")
        instance = this
    }
    fun getTemplateById(id: String): Template? {
        return templates.find { it.id == id }
    }
    fun clearServices(t: Template, amount: Int = 1) {
        val servers = client.server.getServerList().filterIndexed { i, _ -> i < amount }.filter { it.name.startsWith("[pool] ") }.filter { s ->
            val meta = getMetadataFromString(s.getFile(".pooldt"))
            meta.template == t.id
        }
        for (server in servers) {
            server.deleteServer()
        }
    }
    fun createService(t: Template, amount: Int = 1, compile: Boolean = true, uploadDelay: Long = 15000): List<Service> {
        val servers = mutableListOf<Service>()
        repeat(amount) {
            if(compile)
                compileTemplate(t)
            val gzContents = ByteArrayInputStream(poolMaster.getFileBytes("compiled/${t.id}.tar.gz"))
            val metaDataContents = ByteArrayInputStream(
                metadataToString(Metadata(t.id)).encodeToByteArray()
            )
            val server = client.server.createServer(t)
            // wait a bit for server to setup
            sleep(uploadDelay)
            server.uploadFile("/","tmp.tar.gz", gzContents, "application/gzip")
            server.decompressItem("tmp.tar.gz", "/")
            server.deleteItems(listOf("tmp.tar.gz"), "/")
            server.uploadFile("/",".pooldt", metaDataContents)
            server.sendPowerAction("start")
            servers.add(Service(server, t))
        }
        return servers
    }
    fun getServicesRunningByTemplate(t: Template): List<Service> {
        val services = mutableListOf<Service>()
        client.server.getServerList().filter { it.name.startsWith("[pool] ") }.map { s ->
            val meta = getMetadataFromString(s.getFile(".pooldt"))
            if(meta.template == t.id) services.add(Service(s, t))
        }
        return services
    }
    fun compileTemplate(t: Template) {
        val templateFiles = poolMaster.getFilesInDirectory("templates/${t.id}")
        if(!poolMaster.compressItems(templateFiles, "templates/${t.id}")) throw PoolException("Something went wrong while compressing items!")
        val gz = poolMaster.getFilesInDirectory("templates/${t.id}").find { it.endsWith(".tar.gz") }!!
        // delete old compiled ver
        poolMaster.deleteItems(listOf("${t.id}.tar.gz"), "/compiled")
        poolMaster.renameFiles("templates/${t.id}", listOf(
            Servers.RenameFiles.FileRename(gz, "../../compiled/${t.id}.tar.gz")
        ))
    }
    private fun metadataToString(m: Metadata): String {
        val config = Config.inMemory()
        config.set<String>("template", m.template)
        return TomlWriter().writeToString(config)
    }
    private fun getMetadataFromString(s: String): Metadata {
        val config = TomlParser().parse(s)
        return Metadata(config.get("template"))
    }
    class Service(val server: Servers.Server, val template: Template) {
        init {
            PluginManager.callServiceCreate(this)
        }
        fun end() {
            server.deleteServer()
            PluginManager.callServiceEnd(this)
        }
    }
    data class Metadata(val template: String)
    data class Template(val id: String, val pterodactyl: PterodactylSettings, val ram: Int, val storage: Int, val cpu: Int) {
        data class PterodactylSettings(val nest: Int, val egg: Int, val image: String?, val env: Map<String, Any>, val startupCmd: String?, val allocationIps: List<String>?)
    }
    companion object {
        var instance: Pool? = null
        fun get(pteroHost: String, appToken: String, clientToken: String): Pool {
            if(instance != null) {
                return instance!!
            }
            return Pool(pteroHost, appToken, clientToken)
        }
    }
}