package com.realme.spamblocker.sms.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.realme.spamblocker.sms.data.local.AppDatabase
import com.realme.spamblocker.sms.util.SmsSender

class ScheduledSmsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val recipient = inputData.getString(KEY_RECIPIENT) ?: return Result.failure()
        val body = inputData.getString(KEY_BODY) ?: return Result.failure()
        val subId = inputData.getInt(KEY_SUB_ID, -1)

        return try {
            val database = AppDatabase.getInstance(applicationContext)
            val sendResult = SmsSender.sendAndSaveSms(applicationContext, database, recipient, body, subId)
            if (sendResult.isSuccess) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_RECIPIENT = "key_recipient"
        const val KEY_BODY = "key_body"
        const val KEY_SUB_ID = "key_sub_id"
    }
}
