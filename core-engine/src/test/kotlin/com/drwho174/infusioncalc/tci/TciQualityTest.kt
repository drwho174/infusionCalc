package com.drwho174.infusioncalc.tci

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Архитектурный запрет D-TCI-10 / ⛔-TCI-7: в TCI-движке нет плавающей точки и Math.exp/round. */
class TciQualityTest {

    private val forbidden = listOf("Double", "Float", "Math.exp", "Math.round")

    private fun sourceDir(): File {
        val candidates =
            listOf(
                File("src/main/kotlin"),
                File("core-engine/src/main/kotlin"),
            )
        return candidates.firstOrNull { it.isDirectory } ?: error("TCI source directory not found")
    }

    @Test
    fun `core engine sources do not use floating point or Math exp or round`() {
        val files = sourceDir().walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("expected sources but none found", files.isNotEmpty())
        for (file in files) {
            // Собственная BigDecimal-математика TciMath.* — разрешена; java.lang.Math.* — нет.
            val text = file.readText().replace("TciMath.", "")
            for (token in forbidden) {
                assertFalse("${file.name} contains forbidden '$token'", text.contains(token))
            }
        }
    }
}
