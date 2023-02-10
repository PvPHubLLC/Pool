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

    /**
     * Gets a template by its ID, returns null if no template exists.
     * @param id The id to look for
     */
    fun getTemplateById(id: String): Template? {
        return templates.find { it.id == id }
    }

    /**
     * Clears an amount of services created from a template.
     * @param template The template to look for
     * @param amount The amount to clear
     */
    fun clearServices(template: Template, amount: Int = 1) {
        val servers = client.server.getServerList().filterIndexed { i, _ -> i < amount }.filter { it.name.startsWith("[pool] ") }.filter { s ->
            val meta = getMetadataFromString(s.getFile(".pooldt"))
            meta.template == template.id
        }
        for (server in servers) {
            server.deleteServer()
        }
    }

    /**
     * Creates service(s) based on a template
     * @param template The template
     * @param amount The amount of services
     * @param compile If we should call `compileTemplate` to compile the template first
     * @param uploadDelay Time to wait for the server to setup
     *
     * @return Returns a list of services created.
     */
    fun createService(template: Template, amount: Int = 1, compile: Boolean = true, uploadDelay: Long = 15000): List<Service> {
        val servers = mutableListOf<Service>()
        repeat(amount) {
            if(compile)
                compileTemplate(template)
            val gzContents = ByteArrayInputStream(poolMaster.getFileBytes("compiled/${template.id}.tar.gz"))
            val metaDataContents = ByteArrayInputStream(
                metadataToString(Metadata(template.id)).encodeToByteArray()
            )
            val server = client.server.createServer(template)
            // wait a bit for server to setup
            sleep(uploadDelay)
            server.uploadFile("/","tmp.tar.gz", gzContents, "application/gzip")
            server.decompressItem("tmp.tar.gz", "/")
            server.deleteItems(listOf("tmp.tar.gz"), "/")
            server.uploadFile("/",".pooldt", metaDataContents)
            server.sendPowerAction("start")
            servers.add(Service(server, template))
        }
        return servers
    }

    /**
     * Returns the services running by a template
     * @param template The template to search for
     */
    fun getServicesRunningByTemplate(template: Template): List<Service> {
        val services = mutableListOf<Service>()
        client.server.getServerList().filter { it.name.startsWith("[pool] ") }.map { s ->
            val meta = getMetadataFromString(s.getFile(".pooldt"))
            if(meta.template == template.id) services.add(Service(s, template))
        }
        return services
    }

    /**
     * Compiles a template (Updates the compiled version) from the templates directory.
     * @param template The template to compile
     */
    fun compileTemplate(template: Template) {
        val templateFiles = poolMaster.getFilesInDirectory("templates/${template.id}")
        if(!poolMaster.compressItems(templateFiles, "templates/${template.id}")) throw PoolException("Something went wrong while compressing items!")
        val gz = poolMaster.getFilesInDirectory("templates/${template.id}").find { it.endsWith(".tar.gz") }!!
        // delete old compiled ver
        poolMaster.deleteItems(listOf("${template.id}.tar.gz"), "/compiled")
        poolMaster.renameFiles("templates/${template.id}", listOf(
            Servers.RenameFiles.FileRename(gz, "../../compiled/${template.id}.tar.gz")
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

        /**
         * Ends (Destroys) this service, deletes the server and then calls the plugins service end function.
         */
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