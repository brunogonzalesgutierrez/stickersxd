package com.tu.stickerapp

data class StickerPack(
    val identifier: String,
    val name: String,
    val publisher: String,
    val trayImageFileName: String,
    val stickers: List<Sticker>,
    val isAnimated: Boolean = true
)

data class Sticker(
    val filename: String,
    val emojis: List<String> = listOf("😄")
)
