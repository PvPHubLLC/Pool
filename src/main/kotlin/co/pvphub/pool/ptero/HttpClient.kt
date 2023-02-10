package co.pvphub.pool.ptero

import co.pvphub.pool.ptero.modules.Account
import co.pvphub.pool.ptero.modules.Servers
import com.github.kittinunf.fuel.core.Response
import com.google.gson.Gson
import com.google.gson.internal.LinkedTreeMap
import java.lang.reflect.Type
val gson = Gson()
class HttpClient(val appToken: String, val clientToken: String, val baseUrl: String) {
    val account = Account(this, baseUrl, appToken, clientToken)
    val server = Servers(this, baseUrl, appToken, clientToken)

    data class PaginatedResponse<T>(val `object`: String, val data: Array<PaginatedObject<T>>) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PaginatedResponse<*>

            if (`object` != other.`object`) return false
            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = `object`.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }

        data class PaginatedObject<T>(val `object`: String, val attributes: LinkedTreeMap<String, Any>) {
            fun get(type: Type): T {
                return gson.fromJson<T>(gson.toJson(attributes), type)
            }
        }
    }
}
fun Response.strBody(): String {
    return body().asString(headers["Content-Type"].lastOrNull())
}
inline fun <reified T> Response.jsonBody(type: Type): T {
    return gson.fromJson(strBody(), type) as T
}