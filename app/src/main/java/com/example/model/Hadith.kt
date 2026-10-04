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
)
