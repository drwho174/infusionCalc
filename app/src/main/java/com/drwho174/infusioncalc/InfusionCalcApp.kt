package com.drwho174.infusioncalc

import android.app.Application
import com.drwho174.infusioncalc.data.seed.CatalogSeeder
import com.drwho174.infusioncalc.di.appModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class InfusionCalcApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val koinApplication =
            startKoin {
                androidContext(this@InfusionCalcApp)
                modules(appModule)
            }
        appScope.launch {
            koinApplication.koin.get<CatalogSeeder>().seedMissing()
        }
    }
}
