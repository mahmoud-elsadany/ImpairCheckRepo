package com.impaircheck.models

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject

class userData : ArrayList<userDataItem>()

data class userDataItem(
    val id: Int,
    val imageLink: String,
    val name: String
)

object userDataUtils{
    fun convertToJsonArray(data: Map<String, Map<String, Any>>): String {
        val gson = Gson()
        val jsonArray = JsonArray()

        data.forEach { (_, value) ->
            val jsonObject = JsonObject()
            value.forEach { (key, fieldValue) ->
                when (fieldValue) {
                    is Number -> jsonObject.addProperty(key, fieldValue)
                    is String -> jsonObject.addProperty(key, fieldValue)
                    else -> jsonObject.addProperty(key, fieldValue.toString())
                }
            }
            jsonArray.add(jsonObject)
        }

        return gson.toJson(jsonArray)
    }
}
