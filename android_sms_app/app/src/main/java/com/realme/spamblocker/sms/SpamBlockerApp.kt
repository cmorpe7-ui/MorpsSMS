package com.realme.spamblocker.sms

import android.app.Application
import com.realme.spamblocker.sms.data.local.AppDatabase
import com.realme.spamblocker.sms.data.repository.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SpamBlockerApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: SmsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        repository = SmsRepository(database, this)

        // Seed default vocabulary and Philippine spam rules asynchronously on launch
        CoroutineScope(Dispatchers.IO).launch {
            repository.ensureSeeded()
        }
    }
}
