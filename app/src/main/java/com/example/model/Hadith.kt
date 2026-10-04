package com.example.model

data class Hadith(
    val id: Int,
    val chapter: Int,
    val chapterName: String,
    val title: String,
    val description: String,
    val arabicText: String,
    val translation: String,
    val narrator: String,
    val reference: String
)

data class Chapter(
    val id: Int,
    val name: String,
    val desc: String,
    val rangeIds: IntRange,
    val colorHex: String,
    val iconName: String
) {
    /**
     * `colorHex` as an ARGB integer suitable for `androidx.compose.ui.graphics.Color`,
     * so the UI renders exactly the palette declared in [com.example.data.HadithsData]
     * instead of keeping a second, drift-prone copy of the same colours.
     */
    val colorArgb: Long
        get() = colorHex.removePrefix("0x").removePrefix("0X").toULong(radix = 16).toLong()

    val hadithCount: Int
        get() = rangeIds.count()
}
