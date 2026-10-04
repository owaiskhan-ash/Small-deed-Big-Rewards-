package com.example

import com.example.data.HadithsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Local unit tests, executed on the development machine (host JVM).
 *
 * These deliberately avoid the network and avoid writing into `src/main/res`:
 * an earlier revision downloaded a logo from a remote bucket here, which both
 * failed without egress and, on success, dropped a `small_deed.png` next to the
 * existing `small_deed.webp`, tripping an AAPT2 duplicate-resource error on the
 * following build. Icon assets are now produced by tools/make_icons.py instead.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {

  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun dataset_isCompleteAndContiguous() {
    assertEquals("expected one hundred hadiths", 100, HadithsData.totalHadiths)
    val ids = HadithsData.hadiths.map { it.id }
    assertEquals("ids must be unique", ids.toSet().size, ids.size)
    assertEquals("ids must run 1..100 in order", (1..100).toList(), ids.sorted())
  }

  @Test
  fun chapterRanges_coverEveryHadithExactlyOnce() {
    val covered = HadithsData.chapters.flatMap { it.rangeIds.toList() }
    assertEquals("ranges must not overlap", covered.toSet().size, covered.size)
    assertEquals("ranges must cover 1..100", (1..100).toList(), covered.sorted())
    for (chapter in HadithsData.chapters) {
      val members = HadithsData.hadiths.count { it.chapter == chapter.id }
      assertEquals(
        "chapter ${chapter.id} declares ${chapter.hadithCount} hadiths but holds $members",
        chapter.hadithCount,
        members,
      )
    }
  }

  @Test
  fun dailyHadith_isDeterministicPerDay() {
    val day = Calendar.getInstance()
    val first = HadithsData.hadithForDay(day)
    val second = HadithsData.hadithForDay(Calendar.getInstance().apply { time = day.time })
    assertSame("same day must select the same hadith", first, second)

    val tomorrow = Calendar.getInstance().apply {
      time = day.time
      add(Calendar.DAY_OF_YEAR, 1)
    }
    // Not a guarantee for every calendar pair, but over a 100-item pool two
    // consecutive days colliding is a 1-in-100 event; across a week of offsets
    // at least one must differ or the seeding is broken.
    val week = (1..7).map { offset ->
      Calendar.getInstance().apply {
        time = day.time
        add(Calendar.DAY_OF_YEAR, offset)
      }.let(HadithsData::hadithForDay)
    }
    assertTrue("the daily pick must rotate over a week", week.any { it != first })
  }

  @Test
  fun lookups_fallBackSafely() {
    assertNotNull(HadithsData.hadithById(-1))
    assertNotNull(HadithsData.hadithById(Int.MAX_VALUE))
    assertSame("unknown id falls back to the first hadith",
      HadithsData.hadiths.first(), HadithsData.hadithById(-1))
    assertNotNull(HadithsData.chapterById(null))
    assertEquals(1, HadithsData.chapterById(999).id)
  }
}
