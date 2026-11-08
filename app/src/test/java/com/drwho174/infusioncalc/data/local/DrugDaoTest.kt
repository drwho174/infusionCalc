package com.drwho174.infusioncalc.data.local

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DrugDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: DrugDao

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db =
            Room
                .inMemoryDatabaseBuilder<AppDatabase>(context)
                .setDriver(AndroidSQLiteDriver())
                .allowMainThreadQueries()
                .build()
        dao = db.drugDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `saveWithDetails stores drug with schemes and presets`() =
        runBlocking {
            dao.saveWithDetails(drug("d1"), schemes("s1"), presets("p1"))

            val loaded = dao.getDrug("d1")
            assertNotNull(loaded)
            assertEquals(listOf("s1"), loaded!!.schemes.map { it.id })
            assertEquals(listOf("p1"), loaded.presets.map { it.id })
        }

    @Test
    fun `saveWithDetails replaces previous schemes and presets`() =
        runBlocking {
            dao.saveWithDetails(drug("d1"), schemes("s1"), presets("p1"))
            dao.saveWithDetails(drug("d1"), schemes("s2"), presets("p2"))

            val loaded = dao.getDrug("d1")
            assertEquals(listOf("s2"), loaded!!.schemes.map { it.id })
            assertEquals(listOf("p2"), loaded.presets.map { it.id })
        }

    private fun drug(id: String) =
        DrugEntity(
            id = id,
            nameRu = "Тест",
            nameEn = null,
            drugClass = "OTHER",
            highAlert = false,
            route = "в/в",
            defaultSchemeId = null,
            isBuiltIn = false,
            derivedFromId = null,
            verification = "USER",
            notes = null,
            incompatibilities = null,
            revision = 1,
            catalogVersion = 0,
            updatedAt = 0L,
            archived = false,
        )

    private fun schemes(id: String) =
        listOf(
            DoseSchemeEntity(
                id = id,
                drugId = "d1",
                amountUnit = "MCG",
                perKg = false,
                perTime = "PER_MINUTE",
                indication = null,
                doseMin = "0.1",
                doseMax = "1",
                titrationStep = null,
                maxDose = null,
                maxRate = null,
                editedByUser = false,
                schemeOrder = 0,
                roundingOverride = null,
            ),
        )

    private fun presets(id: String) =
        listOf(
            ConcentrationPresetEntity(
                id = id,
                drugId = "d1",
                mode = "MASS_VOLUME",
                amount = "4",
                amountUnit = "MG",
                volumeMl = "1000",
                ampouleConcentration = null,
                ampouleVolumeMl = null,
                isDefault = true,
                label = null,
            ),
        )
}
