package com.drwho174.infusioncalc.data.seed

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.drwho174.infusioncalc.data.RoomDrugRepository
import com.drwho174.infusioncalc.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Проверяет содержимое БД после сидинга: количество, уникальность и идемпотентность. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CatalogSeederTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: RoomDrugRepository

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db =
            Room
                .inMemoryDatabaseBuilder<AppDatabase>(context)
                .setDriver(AndroidSQLiteDriver())
                .allowMainThreadQueries()
                .build()
        repository = RoomDrugRepository(db.drugDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `seed creates unique drugs exactly once`() =
        runBlocking {
            CatalogSeeder(repository).seedMissing()
            val drugs = repository.observeDrugs().first()

            assertEquals(CatalogSeed.drugs().size, drugs.size)

            val ids = drugs.map { it.id }
            assertEquals("duplicate drug ids", ids.size, ids.toSet().size)

            val names = drugs.map { it.nameRu.trim().lowercase() }
            assertEquals("duplicate drug names", names.size, names.toSet().size)

            val schemeIds = drugs.flatMap { it.schemes }.map { it.id }
            assertEquals("duplicate scheme ids", schemeIds.size, schemeIds.toSet().size)

            val presetIds = drugs.flatMap { it.presets }.map { it.id }
            assertEquals("duplicate preset ids", presetIds.size, presetIds.toSet().size)

            drugs.forEach { drug ->
                assertEquals("${drug.id}: default presets", 1, drug.presets.count { it.isDefault })
            }
        }

    @Test
    fun `second seed run does not duplicate rows`() =
        runBlocking {
            val seeder = CatalogSeeder(repository)
            seeder.seedMissing()
            val first = repository.observeDrugs().first()

            seeder.seedMissing()
            val second = repository.observeDrugs().first()

            assertEquals(first.size, second.size)
            assertEquals(first.map { it.id }.toSet(), second.map { it.id }.toSet())
        }
}
