package com.musicbandhub.app.data

import org.json.JSONObject

class BandRepository(private val api: SupabaseClient = SupabaseClient()) {
    fun bands(token: String): List<Band> {
        val a = api.parseArray(api.post("/rest/v1/rpc/my_bands", token, JSONObject()))
        return (0 until a.length()).map { i ->
            val x = a.getJSONObject(i)
            Band(
                x.getString("id"),
                x.getString("name"),
                x.optString("description"),
                x.optString("invite_code")
            )
        }
    }

    fun create(token: String, name: String, description: String): Band {
        val x = api.parseObject(
            api.post(
                "/rest/v1/rpc/create_band",
                token,
                JSONObject().put("p_name", name).put("p_description", description)
            )
        )
        return Band(
            x.getString("id"),
            x.getString("name"),
            x.optString("description"),
            x.optString("invite_code")
        )
    }

    fun join(token: String, code: String) {
        api.post(
            "/rest/v1/rpc/join_band",
            token,
            JSONObject().put("p_invite_code", code)
        )
    }
}

data class Song(
    val id: String,
    val bandId: String,
    val title: String,
    val status: String,
    val bpm: Int?,
    val musicalKey: String?,
    val notes: String
)

class SongRepository(private val api: SupabaseClient = SupabaseClient()) {
    fun list(token: String, bandId: String): List<Song> {
        val a = api.parseArray(
            api.get(
                "/rest/v1/songs?band_id=eq.$bandId&order=updated_at.desc",
                token
            )
        )
        return (0 until a.length()).map { i ->
            val x = a.getJSONObject(i)
            Song(
                x.getString("id"),
                x.getString("band_id"),
                x.getString("title"),
                x.getString("status"),
                if (x.isNull("bpm")) null else x.getInt("bpm"),
                x.optString("musical_key").ifBlank { null },
                x.optString("notes")
            )
        }
    }

    fun create(
        token: String,
        bandId: String,
        title: String,
        status: String = "IDEA"
    ): Song {
        val body = JSONObject()
            .put("band_id", bandId)
            .put("title", title)
            .put("status", status)

        val a = api.parseArray(
            api.post("/rest/v1/songs", token, body)
        )
        val x = a.getJSONObject(0)
        return Song(
            x.getString("id"),
            x.getString("band_id"),
            x.getString("title"),
            x.getString("status"),
            if (x.isNull("bpm")) null else x.getInt("bpm"),
            x.optString("musical_key").ifBlank { null },
            x.optString("notes")
        )
    }
}
