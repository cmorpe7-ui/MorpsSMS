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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.realme.spamblocker.sms.SpamBlockerApp
import com.realme.spamblocker.sms.ui.components.ContactAvatar
import com.realme.spamblocker.sms.ui.screens.ConversationActivity
import com.realme.spamblocker.sms.ui.theme.RealmeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isSending) "Sending..." else "Send SMS", fontWeight = FontWeight.SemiBold)
                        }
                    }
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
