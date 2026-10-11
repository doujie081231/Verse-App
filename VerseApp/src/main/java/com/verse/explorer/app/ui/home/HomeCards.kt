package com.verse.explorer.app.ui.home

import androidx.compose.ui.unit.IntOffset
import com.verse.explorer.cardgrid.model.CardLimits
import com.verse.explorer.cardgrid.model.CardType

/**
 * 主页卡片注册表：声明网格可用的卡片类型与默认跨度。
 */
object HomeCards {

    /** 版本卡片的类型 id */
    const val VERSION_CARD_TYPE_ID = "version_card"

    private val versionCardType = CardType(
        typeId = VERSION_CARD_TYPE_ID,
        defaultSpan = IntOffset(10, 6),
        limits = CardLimits(
            minWidth = 10,
            minHeight = 4,
            maxHeight = 12
        ),
        content = { cardId ->
            VersionCardContent(cardId)
        }
    )

    /** 全部用户卡片类型 */
    val userCardTypes: List<CardType> = listOf(versionCardType)

    /** 版本卡片类型 */
    fun versionCardType(): CardType = versionCardType
}
