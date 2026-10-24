package com.verse.explorer.app.terracotta

import com.google.gson.annotations.SerializedName

/**
 * 陶瓦联机玩家类型
 */
enum class ProfileKind {
    @SerializedName("HOST")
    HOST,
    @SerializedName("LOCAL")
    LOCAL,
    @SerializedName("GUEST")
    GUEST
}

/**
 * 陶瓦联机玩家档案
 */
data class TerracottaProfile(
    @SerializedName("machine_id") val machineID: String,
    @SerializedName("name") val name: String,
    @SerializedName("vendor") val vendor: String,
    @SerializedName("kind") val type: ProfileKind
)