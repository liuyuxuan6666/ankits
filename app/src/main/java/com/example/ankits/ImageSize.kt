package com.example.ankits

data class ImageSize(
    val name: String,
    val description: String,
    val width: Int
)

object ImageSizes {
    val WECHAT = ImageSize(
        name = "微信长图",
        description = "1125px 宽",
        width = 1125
    )

    val WECHAT_110 = ImageSize(
        name = "微信 110%",
        description = "1238px 宽",
        width = 1238
    )

    val WECHAT_125 = ImageSize(
        name = "微信 125%",
        description = "1406px 宽",
        width = 1406
    )

    val INSTAGRAM = ImageSize(
        name = "Instagram",
        description = "1080px 宽",
        width = 1080
    )

    val TWITTER = ImageSize(
        name = "Twitter",
        description = "1600px 宽",
        width = 1600
    )

    val REDBOOK = ImageSize(
        name = "小红书",
        description = "1242px 宽",
        width = 1242
    )

    val ALL = listOf(WECHAT, WECHAT_110, WECHAT_125, INSTAGRAM, TWITTER, REDBOOK)
}
