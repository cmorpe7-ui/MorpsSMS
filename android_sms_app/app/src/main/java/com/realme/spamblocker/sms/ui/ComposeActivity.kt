package com.realme.spamblocker.sms.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realme.spamblocker.sms.SpamBlockerApp
import com.realme.spamblocker.sms.ui.components.ContactAvatar
import com.realme.spamblocker.sms.ui.screens.ConversationActivity
import com.realme.spamblocker.sms.ui.theme.RealmeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ComposeActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as SpamBlockerApp
        val repository = app.repository

        // Read destination from intent data if opened from phone dialer or contacts
        val dataUri = intent.data
        val recipientFromIntent = dataUri?.schemeSpecificPart ?: ""

        setContent {
            RealmeTheme {
                val context = LocalContext.current
                var recipient by remember { mutableStateOf(recipientFromIntent) }
                var messageBody by remember { mutableStateOf("") }
                var isSending by remember { mutableStateOf(false) }
                var showScheduleDialog by remember { mutableStateOf(false) }

                // Contact Picker Launcher
                val contactPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickContact()
                ) { uri: Uri? ->
                    uri?.let {
                        val number = resolveContactNumber(it)
                        if (number != null) {
                            recipient = number
                        }
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("New Message", fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = recipient,
                            onValueChange = { recipient = it },
                            label = { Text("To (Phone Number or Name)") },
                            singleLine = true,
                            leadingIcon = if (recipient.isNotBlank()) {
                                {
                                    ContactAvatar(name = null, phoneNumber = recipient, size = 28.dp)
                                }
                            } else null,
                            trailingIcon = {
                                IconButton(onClick = { contactPickerLauncher.launch(null) }) {
                                    Icon(
                                        imageVector = Icons.Default.Contacts,
                                        contentDescription = "Pick Contact",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = messageBody,
                            onValueChange = { messageBody = it },
                            label = { Text("Text Message") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showScheduleDialog = true },
                                enabled = recipient.isNotBlank() && messageBody.isNotBlank() && !isSending,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Schedule", fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = {
                                    if (recipient.isNotBlank() && messageBody.isNotBlank() && !isSending) {
                                        isSending = true
                                        val target = recipient.trim()
                                        val body = messageBody.trim()

                                        CoroutineScope(Dispatchers.Main).launch {
                                            val result = repository.sendOutgoingSms(
                                                context = this@ComposeActivity,
                                                recipient = target,
                                                body = body,
                                                subId = -1
                                            )
                                            isSending = false
                                            if (result.isSuccess) {
                                                Toast.makeText(this@ComposeActivity, "Message sent", Toast.LENGTH_SHORT).show()
                                                ConversationActivity.start(this@ComposeActivity, target, null, false)
                                                finish()
                                            } else {
                                                Toast.makeText(
                                                    this@ComposeActivity,
                                                    "Failed to send: ${result.exceptionOrNull()?.message}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                },
                                enabled = recipient.isNotBlank() && messageBody.isNotBlank() && !isSending,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(50.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isSending) "Sending..." else "Send SMS", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Schedule SMS Dialog in Compose Screen
                if (showScheduleDialog) {
                    AlertDialog(
                        onDismissRequest = { showScheduleDialog = false },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Schedule SMS")
                            }
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Send this message to $recipient at a future time:",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                listOf(
                                    "In 15 minutes" to (15 * 60 * 1000L),
                                    "In 1 hour" to (60 * 60 * 1000L),
                                    "Tomorrow morning (9:00 AM)" to (12 * 60 * 60 * 1000L)
                                ).forEach { (label, delayMs) ->
                                    OutlinedButton(
                                        onClick = {
                                            if (recipient.isNotBlank() && messageBody.isNotBlank()) {
                                                val target = recipient.trim()
                                                val msg = messageBody.trim()
                                                showScheduleDialog = false
                                                val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.realme.spamblocker.sms.worker.ScheduledSmsWorker>()
                                                    .setInitialDelay(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                                                    .setInputData(
                                                        androidx.work.workDataOf(
                                                            com.realme.spamblocker.sms.worker.ScheduledSmsWorker.KEY_RECIPIENT to target,
                                                            com.realme.spamblocker.sms.worker.ScheduledSmsWorker.KEY_BODY to msg,
                                                            com.realme.spamblocker.sms.worker.ScheduledSmsWorker.KEY_SUB_ID to -1
                                                        )
                                                    )
                                                    .build()
                                                androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
                                                Toast.makeText(context, "Scheduled: $label ⏰", Toast.LENGTH_SHORT).show()
                                                finish()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(label)
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                Button(
                                    onClick = {
                                        if (recipient.isNotBlank() && messageBody.isNotBlank()) {
                                            val target = recipient.trim()
                                            val msg = messageBody.trim()
                                            showScheduleDialog = false
                                            val currentCal = Calendar.getInstance()
                                            val datePicker = android.app.DatePickerDialog(
                                                context,
                                                { _, year, month, dayOfMonth ->
                                                    val timePicker = android.app.TimePickerDialog(
                                                        context,
                                                        { _, hourOfDay, minute ->
                                                            val targetCal = Calendar.getInstance().apply {
                                                                set(Calendar.YEAR, year)
                                                                set(Calendar.MONTH, month)
                                                                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                                                set(Calendar.HOUR_OF_DAY, hourOfDay)
                                                                set(Calendar.MINUTE, minute)
                                                                set(Calendar.SECOND, 0)
                                                            }
                                                            val delayMs = targetCal.timeInMillis - System.currentTimeMillis()
                                                            if (delayMs > 0) {
                                                                val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.realme.spamblocker.sms.worker.ScheduledSmsWorker>()
                                                                    .setInitialDelay(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                                                                    .setInputData(
                                                                        androidx.work.workDataOf(
                                                                            com.realme.spamblocker.sms.worker.ScheduledSmsWorker.KEY_RECIPIENT to target,
                                                                            com.realme.spamblocker.sms.worker.ScheduledSmsWorker.KEY_BODY to msg,
                                                                            com.realme.spamblocker.sms.worker.ScheduledSmsWorker.KEY_SUB_ID to -1
                                                                        )
                                                                    )
                                                                    .build()
                                                                androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
                                                                val fmt = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
                                                                Toast.makeText(context, "Scheduled for ${fmt.format(targetCal.time)} ⏰", Toast.LENGTH_LONG).show()
                                                                finish()
                                                            } else {
                                                                Toast.makeText(context, "Please pick a future time", Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                        currentCal.get(Calendar.HOUR_OF_DAY),
                                                        currentCal.get(Calendar.MINUTE) + 5,
                                                        false
                                                    )
                                                    timePicker.show()
                                                },
                                                currentCal.get(Calendar.YEAR),
                                                currentCal.get(Calendar.MONTH),
                                                currentCal.get(Calendar.DAY_OF_MONTH)
                                            )
                                            datePicker.datePicker.minDate = System.currentTimeMillis() - 1000L
                                            datePicker.show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Pick Custom Date & Time...", fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { showScheduleDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }

    private fun resolveContactNumber(contactUri: Uri): String? {
        var phoneNumber: String? = null
        val cursor = contentResolver.query(contactUri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                val hasPhoneIndex = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                val id = if (idIndex != -1) it.getString(idIndex) else null
                val hasPhone = if (hasPhoneIndex != -1) it.getInt(hasPhoneIndex) > 0 else false

                if (hasPhone && id != null) {
                    val phoneCursor = contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(id),
                        null
                    )
                    phoneCursor?.use { pCursor ->
                        if (pCursor.moveToFirst()) {
                            val numberIndex = pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            if (numberIndex != -1) {
                                phoneNumber = pCursor.getString(numberIndex)
                            }
                        }
                    }
                }
            }
        }
        return phoneNumber
    }
}
