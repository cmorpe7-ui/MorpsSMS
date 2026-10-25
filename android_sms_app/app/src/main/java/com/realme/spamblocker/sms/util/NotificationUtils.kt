package com.realme.spamblocker.sms.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.realme.spamblocker.sms.R
import com.realme.spamblocker.sms.receiver.NotificationActionReceiver
import com.realme.spamblocker.sms.ui.screens.ConversationActivity

object NotificationUtils {

    private const val CHANNEL_ID_VIBRATE = "inbox_sms_channel_vibrate"
    private const val CHANNEL_ID_SILENT = "inbox_sms_channel_silent"

    fun showNotification(context: Context, sender: String, messageSnippet: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val settings = com.realme.spamblocker.sms.data.local.prefs.AppSettingsManager.getInstance(context)
        val privacy = settings.notificationPrivacy.value
        val vibrate = settings.notificationVibration.value
        val targetChannelId = if (vibrate) CHANNEL_ID_VIBRATE else CHANNEL_ID_SILENT

        // Create Channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                targetChannelId,
                if (vibrate) "Incoming Messages" else "Incoming Messages (Silent)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for legitimate personal SMS"
                enableVibration(vibrate)
                if (!vibrate) {
                    vibrationPattern = longArrayOf(0)
                }
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notificationId = sender.hashCode()

        // 1. Content Intent: Opens ConversationActivity directly!
        val openIntent = Intent(context, ConversationActivity::class.java).apply {
            putExtra(ConversationActivity.EXTRA_SENDER, sender)
            putExtra(ConversationActivity.EXTRA_IS_SPAM, false)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 2. Action: Mark as Read
        val markReadIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_READ
            putExtra(NotificationActionReceiver.EXTRA_SENDER, sender)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            markReadIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val markReadAction = NotificationCompat.Action.Builder(
            R.drawable.ic_mark_read,
            "Mark as read",
            markReadPendingIntent
        ).build()

        // 3. Action: Direct Inline Quick Reply (RemoteInput)
        val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_TEXT_REPLY)
            .setLabel("Reply...")
            .build()

        val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_REPLY
            putExtra(NotificationActionReceiver.EXTRA_SENDER, sender)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 2,
            replyIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_send_sms,
            "Reply",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .build()

        val (titleText, contentSnippet) = when (privacy) {
            com.realme.spamblocker.sms.data.local.prefs.NotificationPrivacy.FULL -> {
                Pair(sender, messageSnippet)
            }
            com.realme.spamblocker.sms.data.local.prefs.NotificationPrivacy.SENDER_ONLY -> {
                Pair(sender, "New message received")
            }
            com.realme.spamblocker.sms.data.local.prefs.NotificationPrivacy.HIDE -> {
                Pair("Messages", "New message received")
            }
        }

        // Build notification
        val builder = NotificationCompat.Builder(context, targetChannelId)
            .setSmallIcon(R.drawable.ic_message_sms)
            .setContentTitle(titleText)
            .setContentText(contentSnippet)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPendingIntent)
            .addAction(replyAction)
            .addAction(markReadAction)
            .setAutoCancel(true)

        if (privacy == com.realme.spamblocker.sms.data.local.prefs.NotificationPrivacy.FULL) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(contentSnippet))
        }

        if (!vibrate) {
            builder.setVibrate(longArrayOf(0))
        }

        notificationManager.notify(notificationId, builder.build())
    }
}
