package co.pvphub.pool.ptero.modules

import co.pvphub.pool.Pool
import co.pvphub.pool.PoolException
import co.pvphub.pool.ptero.*
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.BlobDataPart
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import java.io.InputStream
import java.net.InetSocketAddress
import java.util.*

class Servers(client: HttpClient, baseUrl: String, appToken: String, clientToken: String): Module(client, baseUrl, appToken, clientToken) {
    fun createServer(t: Pool.Template): Server {
        val info = Fuel.get("$baseUrl/api/application/nests/${t.pterodactyl.nest}/eggs/${t.pterodactyl.egg}?include=variables")
            .header("Authorization", "Bearer $appToken")
            .response()
            .second.jsonBody<JsonObject>(JsonObject::class.java).get("attributes").asJsonObject
        val vars = gson.fromJson<HttpClient.PaginatedResponse<JsonObject>>(info.get("relationships").asJsonObject.get("variables"), HttpClient.PaginatedResponse::class.java).data.map { it.get(
            JsonObject::class.java) }
        val fullEnv = mutableMapOf<String, Any>()
        vars.forEach { v ->
            val name = v.get("env_variable").asString
            if(t.pterodactyl.env.containsKey(name)) {
                fullEnv[name] = t.pterodactyl.env[name] as Any
            } else {
                fullEnv[name] = gson.fromJson(v.get("default_value"), Any::class.java)
            }
        }
        // All Env variables gathered from default/provided values
        val dockerImg = t.pterodactyl.image ?: info.get("docker_image").asString
        val generatedName = "[pool] ${UUID.randomUUID()}"
        val userId = client.account.getClientDetails().id
        val startupCommand = t.pterodactyl.startupCmd ?: info.get("startup").asString
        val node = getNodes().firstOrNull { it.description.contains("[pool-node]") } ?: throw PoolException("No pool nodes were available! Make sure to mark pool nodes with a `[pool-node]` in their description")
        val possibleAllocations = node.relationships.allocations.data.map { it.get(Node.Allocation::class.java) }.filter { !it.assigned && if(t.pterodactyl.allocationIps != null) t.pterodactyl.allocationIps.contains(it.ip) else true }
        val payload = ServerCreate(
            generatedName,
            userId,
            t.pterodactyl.nest,
            t.pterodactyl.egg,
            dockerImg,
            startupCommand,
            true,
            Server.ServerLimits(
                t.ram,
                0,
                t.storage,
                500,
                t.cpu
            ),
            Server.FeatureLimits(
                0,
                0,
                0
            ),
            fullEnv,
            true,
            ServerCreate.AllocationData(
                possibleAllocations[0].id,
                listOf()
            )
        )

        return Fuel.post("$baseUrl/api/application/servers")
            .jsonBody(gson.toJson(payload))
            .header("Authorization", "Bearer $appToken")
            .response()
            .second
            .jsonBody<HttpClient.PaginatedResponse.PaginatedObject<Server>>(HttpClient.PaginatedResponse.PaginatedObject::class.java).get(
                Server::class.java)

    }
    fun getNodes(): List<Node> {
        return Fuel.get("$baseUrl/api/application/nodes?include=allocations")
            .header("Authorization", "Bearer $appToken")
            .response()
            .second
            .jsonBody<HttpClient.PaginatedResponse<Node>>(HttpClient.PaginatedResponse::class.java).data.map { it.get(
            Node::class.java) }
    }
    fun getServerList(): List<Server> {
        val r = Fuel.get("$baseUrl/api/application/servers")
            .header("Authorization", "Bearer $appToken")
            .response()
            .second
        return r.jsonBody<HttpClient.PaginatedResponse<Server>>(HttpClient.PaginatedResponse::class.java).data.map { it.get(Server::class.java) }
    }
    fun getServerByUUID(u: UUID): Server {
        return Fuel.get("$baseUrl/api/application/servers/$u")
            .header("Authorization", "Bearer $appToken")
            .response()
            .second
            .jsonBody<HttpClient.PaginatedResponse.PaginatedObject<Server>>(HttpClient.PaginatedResponse.PaginatedObject::class.java).get(Server::class.java)
    }
    data class ServerCreate(val name: String, val user: Int, val nest: Int, val egg: Int, @SerializedName("docker_image") val dockerImage: String, val startup: String, @SerializedName("oom_disabled") val oomDisabled: Boolean, val limits: Server.ServerLimits, @SerializedName("feature_limits") val featureLimits: Server.FeatureLimits, val environment: Map<String, Any>, @SerializedName("start_on_completion") val startWhenSetup: Boolean, val allocation: AllocationData) {
        data class AllocationData(val default: Int, val additional: List<Int>)
    }
    data class Node(val id: Int, val uuid: UUID, val public: Boolean, val name: String,val description: String, val relationships: NodeRelationships) {
        data class NodeRelationships(val allocations: HttpClient.PaginatedResponse<Allocation>)
        data class Allocation(val id: Int, val ip: String, val port: Int, val assigned: Boolean)
    }

    data class Server(val id: Int, @SerializedName("external_id") val externalId: String?, val uuid: UUID, val identifier: String, val name: String, val description: String, val status: Any?, val suspended: Boolean, val limits: ServerLimits, @SerializedName("feature_limits") val featureLimits: FeatureLimits, val user: Int, val node: Int, val allocation: Int, val nest: Int, val egg: Int, val container: ContainerSettings, @SerializedName("updated_at") val updatedAt: Date, @SerializedName("created_at") val createdAt: Date) {
        data class ServerLimits(val memory: Int, val swap: Int, val disk: Int, val io: Int, val cpu: Int)
        data class FeatureLimits(val databases: Int, val allocations: Int, val backups: Int)
        data class ContainerSettings(@SerializedName("startup_command") val startupCommand: String, val image: String, val installed: Int, val environment: Any)
        fun getFile(f: String): String {
            val r = Fuel.get("${Pool.instance!!.client.baseUrl}/api/client/servers/$uuid/files/contents?file=/$f")
                .header("Authorization", "Bearer ${Pool.instance!!.client.clientToken}")
            return r.response().second.strBody()
        }
        fun getFilesInDirectory(d: String): List<String> {
            val r = Fuel.get("${Pool.instance!!.client.baseUrl}/api/client/servers/$uuid/files/list?directory=/$d")
                .header("Authorization", "Bearer ${Pool.instance!!.client.clientToken}")
            return r.response().second.jsonBody<HttpClient.PaginatedResponse<FileListing>>(HttpClient.PaginatedResponse::class.java).data.map { it.get(
                FileListing::class.java).name }
        }
        fun renameFiles(d: String, files: List<RenameFiles.FileRename>): Boolean {
            val r = Fuel.put("${Pool.instance!!.client.baseUrl}/api/client/servers/$uuid/files/rename")
                .header("Authorization", "Bearer ${Pool.instance!!.client.clientToken}")
                .jsonBody(gson.toJson(RenameFiles(d, files)))
            return r.response().second.statusCode == 204
        }
        fun uploadFile(dir: String, name: String, body: InputStream, contentType: String = "text/plain"): Boolean {
            val r = Fuel.get("${Pool.instance!!.client.baseUrl}/api/client/servers/$uuid/files/upload")
                .header("Authorization", "Bearer ${Pool.instance!!.client.clientToken}")
                .response().second
            val givenUrl = r.jsonBody<JsonObject>(JsonObject::class.java).get("attributes").asJsonObject.get("url").asString
            return Fuel.upload("$givenUrl&directory=$dir").add {
                BlobDataPart(body, "files", name, contentType = contentType)
            }.response().second.statusCode == 204
        }
        fun getFileBytes(path: String): ByteArray {
            val givenUrl = Fuel.get("${Pool.instance!!.client.baseUrl}/api/client/servers/$uuid/files/download?file=/$path")
                .header("Authorization", "Bearer ${Pool.instance!!.client.clientToken}")
                .response().second.jsonBody<JsonObject>(JsonObject::class.java).get("attributes").asJsonObject.get("url").asString
            return Fuel.get(givenUrl).response().second.body().toByteArray()
        }
        fun compressItems(names: List<String>, dir: String): Boolean {
            val r = Fuel.post("${Pool.instance!!.client.baseUrl}/api/client/servers/$uuid/files/compress")
                .header("Authorization", "Bearer ${Pool.instance!!.client.clientToken}")
                .jsonBody(gson.toJson(GenericFileAction(dir, names)))
            return r.response().second.statusCode == 200
        }
        fun sendPowerAction(action: String): Boolean {
            val data = mapOf(
                "signal" to action
            )
            return Fuel.post("${Pool.instance!!.client.baseUrl}/api/client/servers/$uuid/power")
                .header("Authorization", "Bearer ${Pool.instance!!.client.clientToken}")
                .jsonBody(gson.toJson(data))
                .response()
                .second
                .statusCode == 204

        }
        fun deleteServer(force: Boolean = false): Boolean {
            return Fuel.delete("${Pool.instance!!.client.baseUrl}/api/application/servers/$uuid${if (force) "/force" else ""}")
                .header("Authorization", "Bearer ${Pool.instance!!.client.appToken}")
                .response()
                .second
                .statusCode == 204
        }
        fun deleteItems(names: List<String>, dir: String): Boolean {
            val r = Fuel.post("${Pool.instance!!.client.baseUrl}/api/client/servers/$uuid/files/delete")
                .header("Authorization", "Bearer ${Pool.instance!!.client.clientToken}")
                .jsonBody(gson.toJson(GenericFileAction(dir, names)))
            return r.response().second.statusCode == 204
        }
        fun getSocketAddress(): InetSocketAddress {
            val allocation = Pool.instance!!.client.server.getNodes().find { it.relationships.allocations.data.map { it.get(Node.Allocation::class.java) }.any { it.id == allocation } }!!.relationships.allocations.data.map { it.get(Node.Allocation::class.java) }.find { it.id == allocation }!!
            return InetSocketAddress(allocation.ip, allocation.port)
        }
        fun decompressItem(name: String, dir: String): Boolean {
            val r = Fuel.post("${Pool.instance!!.client.baseUrl}/api/client/servers/$uuid/files/decompress")
                .header("Authorization", "Bearer ${Pool.instance!!.client.clientToken}")
                .jsonBody(gson.toJson(Decompress(name, dir)))
            return r.response().second.statusCode == 204
        }
    }

    // Just random body jank
    data class FileListing(val name: String)
    data class RenameFiles(val root: String, val files: List<FileRename>) {
        data class FileRename(val from: String, val to: String)
    }
    data class GenericFileAction(val root: String, val files: List<String>)
    data class Decompress(val file: String, val root: String)

}