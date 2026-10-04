package com.example

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun checkLogoValidity() {
    val logoUrl = "https://sblbbrvhsyrryfoxiqna.supabase.co/storage/v1/object/public/The%20Date%20Farm/SDBR/Small-Deed.jpg"
    val destPngFile = File("src/main/res/drawable/small_deed.png")
    
    println("Downloading and converting logo to PNG from $logoUrl...")
    val url = java.net.URL(logoUrl)
    val image = javax.imageio.ImageIO.read(url)
    assertNotNull("Downloaded image should not be null", image)
    
    val success = javax.imageio.ImageIO.write(image, "png", destPngFile)
    assertTrue("Should successfully convert image to PNG", success)
    
    val oldJpgFile = File("src/main/res/drawable/small_deed.jpg")
    if (oldJpgFile.exists()) {
      oldJpgFile.delete()
    }

    assertTrue("PNG logo file must exist", destPngFile.exists())
    val size = destPngFile.length()
    println("PNG logo size: $size bytes")
    assertTrue("PNG logo must be non-empty", size > 0)

    val fis = destPngFile.inputStream()
    val header = ByteArray(8)
    val readBytes = fis.read(header)
    fis.close()

    assertEquals("Must read 8 bytes of header", 8, readBytes)
    val expectedHeader = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte())
    assertArrayEquals("File must be a valid PNG (correct magic bytes)", expectedHeader, header)
  }
}
