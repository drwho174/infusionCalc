package com.drwho174.infusioncalc.data.local

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class FolderDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: FolderDao

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db =
            Room
                .inMemoryDatabaseBuilder<AppDatabase>(context)
                .setDriver(AndroidSQLiteDriver())
                .allowMainThreadQueries()
                .build()
        dao = db.folderDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `folders are ordered by name`() =
        runBlocking {
            dao.insertFolder(FolderEntity(id = "f2", name = "Седация", nameLower = "седация", createdAt = 2))
            dao.insertFolder(FolderEntity(id = "f1", name = "Вазопрессоры", nameLower = "вазопрессоры", createdAt = 1))

            assertEquals(
                listOf("Вазопрессоры", "Седация"),
                dao.observeFolders().first().map { it.name },
            )
        }

    @Test
    fun `name uniqueness is case insensitive`() =
        runBlocking {
            dao.insertFolder(FolderEntity(id = "f1", name = "ОРИТ", nameLower = "орит", createdAt = 1))
            assertEquals(1, dao.countByName("орит"))
            assertEquals(0, dao.countByName("седация"))
        }

    @Test
    fun `membership is saved, replaced and cascade deleted with folder`() =
        runBlocking {
            db.drugDao().insertDrug(drug("d1"))
            dao.insertFolder(FolderEntity(id = "f1", name = "ОРИТ", nameLower = "орит", createdAt = 1))
            dao.insertFolder(FolderEntity(id = "f2", name = "Седация", nameLower = "седация", createdAt = 2))

            dao.setDrugFolders("d1", setOf("f1", "f2"))
            assertEquals(setOf("f1", "f2"), dao.folderIds("d1").toSet())

            dao.setDrugFolders("d1", setOf("f1"))
            assertEquals(setOf("f1"), dao.folderIds("d1").toSet())

            dao.deleteFolder("f1")
            assertEquals(emptySet<String>(), dao.folderIds("d1").toSet())
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
}
