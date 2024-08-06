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

