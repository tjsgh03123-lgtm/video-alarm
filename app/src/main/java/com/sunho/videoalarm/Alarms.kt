package com.sunho.videoalarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Alarm(
    var id: Int = 0,
    var hour: Int = 7,
    var minute: Int = 0,
    var days: Int = 0, // bit0=월 ... bit6=일, 0=한 번만
    var enabled: Boolean = true,
    var label: String = "",
    var mode: String = "", // "", "file", "youtube"
    var fileUri: String = "",
    var fileName: String = "",
    var youtubeUrl: String = ""
) {
    fun videoLabel() = when (mode) {
        "file" -> "📁 $fileName"
        "youtube" -> "▶ $youtubeUrl"
        else -> "영상 없음"
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("hour", hour).put("minute", minute).put("days", days)
        .put("enabled", enabled).put("label", label).put("mode", mode)
        .put("fileUri", fileUri).put("fileName", fileName).put("youtubeUrl", youtubeUrl)

    companion object {
        fun fromJson(o: JSONObject) = Alarm(
            o.optInt("id"), o.optInt("hour", 7), o.optInt("minute", 0), o.optInt("days", 0),
            o.optBoolean("enabled", true), o.optString("label"), o.optString("mode"),
            o.optString("fileUri"), o.optString("fileName"), o.optString("youtubeUrl")
        )
    }
}

object AlarmStore {
    private fun sp(c: Context) = c.getSharedPreferences("alarms", Context.MODE_PRIVATE)

    fun all(c: Context): MutableList<Alarm> {
        val arr = JSONArray(sp(c).getString("list", "[]"))
        return MutableList(arr.length()) { Alarm.fromJson(arr.getJSONObject(it)) }
    }

    private fun write(c: Context, list: List<Alarm>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp(c).edit().putString("list", arr.toString()).apply()
    }

    fun get(c: Context, id: Int): Alarm? = all(c).firstOrNull { it.id == id }

    fun save(c: Context, a: Alarm) {
        val l = all(c)
        val i = l.indexOfFirst { it.id == a.id }
        if (i >= 0) l[i] = a else l.add(a)
        write(c, l)
    }

    fun delete(c: Context, id: Int) = write(c, all(c).filter { it.id != id })

    fun newId(c: Context): Int = (all(c).maxOfOrNull { it.id } ?: 0) + 1
}

fun extractYoutubeId(s: String): String? {
    val t = s.trim()
    if (Regex("^[A-Za-z0-9_-]{11}$").matches(t)) return t
    return Regex("(?:v=|youtu\\.be/|embed/|shorts/|live/)([A-Za-z0-9_-]{11})").find(t)?.groupValues?.get(1)
}
