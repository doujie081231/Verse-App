package com.verse.explorer.app.terracotta

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

/**
 * 陶瓦联机状态模型
 *
 */
sealed class TerracottaState {
    abstract val index: Int

    open val stateName: String
        get() = when (this) {
            is Waiting -> "waiting"
            is HostScanning -> "host-scanning"
            is HostStarting -> "host-starting"
            is HostOK -> "host-ok"
            is GuestConnecting -> "guest-connecting"
            is GuestStarting -> "guest-starting"
            is GuestOK -> "guest-ok"
            is ExceptionState -> "exception"
        }

    data class Waiting(override val index: Int) : TerracottaState()
    data class HostScanning(override val index: Int) : TerracottaState()
    data class HostStarting(override val index: Int) : TerracottaState()

    data class HostOK(
        override val index: Int,
        val code: String,
        val profileIndex: Int,
        val profiles: List<TerracottaProfile>
    ) : TerracottaState() {
        override val stateName: String = "host-ok"

        /**
         * 界面只需刷新玩家列表，不必整体切换状态视图。
         */
        fun isForkOf(other: TerracottaState): Boolean =
            other is HostOK && index - other.index <= profileIndex
    }

    data class GuestConnecting(override val index: Int) : TerracottaState()

    data class GuestStarting(
        override val index: Int,
        val difficulty: Difficulty
    ) : TerracottaState() {
        override val stateName: String = "guest-starting"

        enum class Difficulty { UNKNOWN, EASIEST, SIMPLE, MEDIUM, TOUGH }
    }

    data class GuestOK(
        override val index: Int,
        val url: String?,
        val profileIndex: Int,
        val profiles: List<TerracottaProfile>
    ) : TerracottaState() {
        override val stateName: String = "guest-ok"

        /**
         * 界面只需刷新玩家列表，不必整体切换状态视图。
         */
        fun isForkOf(other: TerracottaState): Boolean =
            other is GuestOK && index - other.index <= profileIndex
    }

    data class ExceptionState(
        override val index: Int,
        val type: ExceptionType
    ) : TerracottaState() {
        override val stateName: String = "exception"

        enum class ExceptionType {
            PING_HOST_FAIL,
            PING_HOST_RST,
            GUEST_ET_CRASH,
            HOST_ET_CRASH,
            PING_SERVER_RST,
            SCAFFOLDING_INVALID_RESPONSE
        }
    }

    companion object {
        private val profileListType = com.google.gson.reflect.TypeToken.getParameterized(
            List::class.java,
            TerracottaProfile::class.java
        ).type

        /**
         * 解析原生后端返回的状态 JSON
         */
        fun parse(json: String): TerracottaState {
            val element: JsonElement = JsonParser.parseString(json)
            val obj: JsonObject = element.asJsonObject
            val index = obj.get("index")?.asInt ?: -1
            val state = obj.get("state")?.asString ?: ""

            return when (state) {
                "waiting" -> Waiting(index)
                "host-scanning" -> HostScanning(index)
                "host-starting" -> HostStarting(index)
                "host-ok" -> {
                    val code = obj.get("room")?.asString ?: ""
                    val profileIndex = obj.get("profile_index")?.asInt ?: 0
                    val profiles = parseProfiles(obj.get("profiles"))
                    HostOK(index, code, profileIndex, profiles)
                }
                "guest-connecting" -> GuestConnecting(index)
                "guest-starting" -> {
                    val difficulty = parseDifficulty(obj.get("difficulty")?.asString)
                    GuestStarting(index, difficulty)
                }
                "guest-ok" -> {
                    val url = obj.get("url")?.asString
                    val profileIndex = obj.get("profile_index")?.asInt ?: 0
                    val profiles = parseProfiles(obj.get("profiles"))
                    GuestOK(index, url, profileIndex, profiles)
                }
                "exception" -> {
                    val type = parseExceptionType(obj.get("type")?.asInt ?: -1)
                    ExceptionState(index, type)
                }
                else -> throw JsonParseException("Unknown terracotta state: $state")
            }
        }

        private fun parseProfiles(element: JsonElement?): List<TerracottaProfile> {
            if (element == null || element.isJsonNull) return emptyList()
            return try {
                com.google.gson.Gson().fromJson(element, profileListType)
            } catch (_: Exception) {
                emptyList()
            }
        }

        private fun parseDifficulty(value: String?): GuestStarting.Difficulty {
            return try {
                GuestStarting.Difficulty.valueOf(value ?: "UNKNOWN")
            } catch (_: Exception) {
                GuestStarting.Difficulty.UNKNOWN
            }
        }

        private fun parseExceptionType(value: Int): ExceptionState.ExceptionType {
            val values = ExceptionState.ExceptionType.values()
            return if (value in values.indices) values[value] else ExceptionState.ExceptionType.PING_HOST_FAIL
        }
    }
}

/**
 * 手动 TypeAdapter 用于 Gson 多态反序列化（备用，当前解析走 [TerracottaState.parse]）
 */
object TerracottaStateAdapter : TypeAdapter<TerracottaState>() {
    override fun write(out: JsonWriter, value: TerracottaState) {
        out.value(value.stateName)
    }

    override fun read(input: JsonReader): TerracottaState {
        val json = JsonParser.parseReader(input).toString()
        return TerracottaState.parse(json)
    }
}