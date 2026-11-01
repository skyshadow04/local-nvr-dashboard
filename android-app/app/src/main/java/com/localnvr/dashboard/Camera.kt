package com.localnvr.dashboard

import org.json.JSONObject

data class Camera(
    val id: String,
    val name: String,
    val type: String,
    val ip: String,
    val channel: Int,
    val stream: String,
) {
    companion object {
        fun fromJson(json: JSONObject): Camera = Camera(
            id = json.getString("id"),
            name = json.getString("name"),
            type = json.getString("type"),
            ip = json.getString("ip"),
            channel = json.getInt("channel"),
            stream = json.getString("stream"),
        )
    }
}
