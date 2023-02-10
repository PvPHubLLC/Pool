package co.pvphub.pool.ptero.modules

import co.pvphub.pool.ptero.HttpClient
import co.pvphub.pool.ptero.Module
import co.pvphub.pool.ptero.jsonBody
import com.github.kittinunf.fuel.Fuel
import com.google.gson.annotations.SerializedName

class Account(client: HttpClient, baseUrl: String, appToken: String, clientToken: String): Module(client, baseUrl, appToken, clientToken) {
    fun getClientDetails(): Account {
        return Fuel.get("$baseUrl/api/client/account")
            .header("Authorization", "Bearer $clientToken")
            .response()
            .second
            .jsonBody<HttpClient.PaginatedResponse.PaginatedObject<Account>>(HttpClient.PaginatedResponse.PaginatedObject::class.java).get(
                Account::class.java)
    }
    data class Account(val id: Int, val admin: Boolean, val username: String, val email: String, @SerializedName("first_name") val firstName: String, @SerializedName("last_name") val lastName: String, val language: String)

}