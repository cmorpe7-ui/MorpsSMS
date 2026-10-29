package com.realme.spamblocker.sms.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realme.spamblocker.sms.SpamBlockerApp
import com.realme.spamblocker.sms.data.local.entity.MessageEntity
import com.realme.spamblocker.sms.data.local.prefs.AppSettingsManager
import com.realme.spamblocker.sms.data.local.prefs.BubbleShape
import com.realme.spamblocker.sms.data.repository.SmsRepository
import com.realme.spamblocker.sms.ui.components.ContactAvatar
import com.realme.spamblocker.sms.ui.theme.*
import com.realme.spamblocker.sms.util.SimInfo
import com.realme.spamblocker.sms.util.SimUtils
import com.realme.spamblocker.sms.util.SmartMessageAnalyzer
import com.realme.spamblocker.sms.util.UrlInspection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ConversationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sender = intent.getStringExtra(EXTRA_SENDER) ?: ""
        val senderName = intent.getStringExtra(EXTRA_SENDER_NAME)
        val isSpam = intent.getBooleanExtra(EXTRA_IS_SPAM, false)

        val app = application as SpamBlockerApp

        setContent {
            RealmeTheme {
                ConversationScreen(
                    sender = sender,
                    senderName = senderName,
                    initialIsSpam = isSpam,
                    repository = app.repository,
                    onBackClick = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_SENDER_NAME = "extra_sender_name"
        const val EXTRA_IS_SPAM = "extra_is_spam"

        fun start(context: Context, sender: String, senderName: String?, isSpam: Boolean) {
            val intent = Intent(context, ConversationActivity::class.java).apply {
                putExtra(EXTRA_SENDER, sender)
                putExtra(EXTRA_SENDER_NAME, senderName)
                putExtra(EXTRA_IS_SPAM, isSpam)
            }
            context.startActivity(intent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    sender: String,
    senderName: String?,
    initialIsSpam: Boolean,
    repository: SmsRepository,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val messages by repository.getConversationMessages(sender).collectAsState(initial = emptyList())

    var textInput by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    // SIM management
    val availableSims = remember { SimUtils.getAvailableSims(context) }
    var selectedSimIndex by remember { mutableIntStateOf(0) }
    val currentSim: SimInfo? = availableSims.getOrNull(selectedSimIndex)

    var activeMessageForAction by remember { mutableStateOf<MessageEntity?>(null) }
    var inspectingUrl by remember { mutableStateOf<UrlInspection?>(null) }
    var showScheduleDialog by remember { mutableStateOf(false) }

    // Quick Canned Reply Templates
    val quickTemplates = listOf(
        "On my way! 🚗",
        "Can't talk now, text you later.",
        "Received, thank you! 👍",
        "Please call me back.",
        "Got it, sounds good!",
        "Where are you?"
    )

    // Mark as read when opening conversation
    LaunchedEffect(sender) {
        repository.markThreadAsRead(sender)
    }

    // Scroll to latest message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }

                    // Contact Avatar with actual contact photo
                    val displayName = senderName ?: sender
                    ContactAvatar(
                        name = senderName,
                        phoneNumber = sender,
                        photoUri = messages.firstOrNull()?.contactPhotoUri,
                        size = 40.dp
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (senderName != null) {
                            Text(
                                text = sender,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Direct Phone Dialing
                    IconButton(onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:$sender")
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Direct Spam / Block / Restore Action in Top Bar
                    if (initialIsSpam) {
                        IconButton(onClick = {
                            val sample = messages.lastOrNull()
                            if (sample != null) {
                                scope.launch {
                                    repository.markAsNotSpam(sample)
                                    Toast.makeText(context, "Restored to Inbox & Unblocked", Toast.LENGTH_SHORT).show()
                                    onBackClick()
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Restore to Inbox",
                                tint = RealmeHamGreen
                            )
                        }
                    } else {
                        IconButton(onClick = {
                            val sample = messages.lastOrNull()
                            if (sample != null) {
                                scope.launch {
                                    repository.markAsSpam(sample)
                                    Toast.makeText(context, "Reported Spam & Number Blocked 🛡️", Toast.LENGTH_SHORT).show()
                                    onBackClick()
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Report Spam & Block",
                                tint = RealmeSpamRed
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                // Quick Canned Templates Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickTemplates.forEach { template ->
                        SuggestionChip(
                            onClick = { textInput = template },
                            label = { Text(template, fontSize = 12.sp) }
                        )
                    }
                }

                // Dual-SIM Indicator
                if (availableSims.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SimCard,
                            contentDescription = "SIM",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sending from:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        availableSims.forEachIndexed { index, sim ->
                            val isSelected = (index == selectedSimIndex)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .clickable { selectedSimIndex = index }
                            ) {
                                Text(
                                    text = "${sim.displayName} (${sim.carrierName})",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // Input Bar with Schedule Button and Send
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showScheduleDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Schedule SMS",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("Text message") },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    FloatingActionButton(
                        onClick = {
                            if (textInput.isNotBlank() && !isSending) {
                                isSending = true
                                val messageToSend = textInput.trim()
                                textInput = ""
                                scope.launch {
                                    val result = repository.sendOutgoingSms(
                                        context = context,
                                        recipient = sender,
                                        body = messageToSend,
                                        subId = currentSim?.subscriptionId ?: -1
                                    )
                                    isSending = false
                                    if (result.isFailure) {
                                        Toast.makeText(
                                            context,
                                            "Failed to send: ${result.exceptionOrNull()?.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.Black,
                        shape = CircleShape,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                ChatBubble(
                    message = message,
                    onLongClick = { activeMessageForAction = message },
                    onInspectUrl = { inspectingUrl = it }
                )
            }
        }
    }

    // SCHEDULE SMS DIALOG
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select when to automatically send this message:")
                    listOf(
                        "In 15 minutes" to (15 * 60 * 1000L),
                        "In 1 hour" to (60 * 60 * 1000L),
                        "Tomorrow morning (9:00 AM)" to (12 * 60 * 60 * 1000L)
                    ).forEach { (label, delayMs) ->
                        OutlinedButton(
                            onClick = {
                                if (textInput.isNotBlank()) {
                                    val msg = textInput.trim()
                                    textInput = ""
                                    showScheduleDialog = false
                                    scope.launch {
                                        Toast.makeText(context, "Scheduled: '$msg' ($label)", Toast.LENGTH_LONG).show()
                                        delay(delayMs)
                                        repository.sendOutgoingSms(context, sender, msg, currentSim?.subscriptionId ?: -1)
                                    }
                                } else {
                                    Toast.makeText(context, "Please write a message first", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label)
                        }
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

    // URL INSPECTOR MODAL
    if (inspectingUrl != null) {
        val inspection = inspectingUrl!!
        AlertDialog(
            onDismissRequest = { inspectingUrl = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (inspection.riskLevel == "High") Icons.Default.Warning else Icons.Default.Link,
                        contentDescription = null,
                        tint = if (inspection.riskLevel == "High") RealmeSpamRed else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("URL Security Inspector")
                }
            },
            text = {
                Column {
                    Text(
                        text = inspection.url,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Risk Level: ${inspection.riskLevel}", fontWeight = FontWeight.Bold, color = if (inspection.riskLevel == "High") RealmeSpamRed else RealmeHamGreen)
                    if (inspection.warningReason != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Reason: ${inspection.warningReason}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("URL", inspection.url))
                    Toast.makeText(context, "URL copied", Toast.LENGTH_SHORT).show()
                    inspectingUrl = null
                }) {
                    Text("Copy URL")
                }
            },
            dismissButton = {
                TextButton(onClick = { inspectingUrl = null }) {
                    Text("Close")
                }
            }
        )
    }

    // LONG PRESS MESSAGE OPTIONS
    activeMessageForAction?.let { msg ->
        ModalBottomSheet(
            onDismissRequest = { activeMessageForAction = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Message Options",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 1. Star / Unstar
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newStarred = !msg.isStarred
                            scope.launch {
                                repository.toggleStar(msg.id, newStarred)
                                Toast.makeText(
                                    context,
                                    if (newStarred) "Message starred ⭐" else "Message unstarred",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            activeMessageForAction = null
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (msg.isStarred) Icons.Default.StarBorder else Icons.Default.Star,
                            contentDescription = "Star",
                            tint = Color(0xFFFFB300)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(if (msg.isStarred) "Remove Star" else "Star Message", fontWeight = FontWeight.Medium)
                    }
                }

                // 2. Copy Text
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("SMS", msg.body))
                            Toast.makeText(context, "Message copied to clipboard", Toast.LENGTH_SHORT).show()
                            activeMessageForAction = null
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Copy Text", fontWeight = FontWeight.Medium)
                    }
                }

                // 3. Share / Forward
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, msg.body)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Forward message"))
                            activeMessageForAction = null
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Forward / Share", fontWeight = FontWeight.Medium)
                    }
                }

                // 4. Report Spam & Block / Restore
                if (initialIsSpam) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    repository.markAsNotSpam(msg)
                                    Toast.makeText(context, "Restored to Inbox & Unblocked", Toast.LENGTH_SHORT).show()
                                    onBackClick()
                                }
                                activeMessageForAction = null
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Restore", tint = RealmeHamGreen)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Restore to Inbox (Not Spam)", fontWeight = FontWeight.SemiBold, color = RealmeHamGreen)
                                Text("Learns message as legitimate and unblocks sender", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    repository.markAsSpam(msg)
                                    Toast.makeText(context, "Reported Spam & Number Blocked 🛡️", Toast.LENGTH_SHORT).show()
                                    onBackClick()
                                }
                                activeMessageForAction = null
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = RealmeSpamRedBg
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = "Report Spam", tint = RealmeSpamRed)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Report Spam & Block Sender", fontWeight = FontWeight.SemiBold, color = RealmeSpamRed)
                                Text("Learns message patterns and auto-blocks this phone number", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // 5. Delete Single Message
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                repository.deleteMessage(msg)
                                Toast.makeText(context, "Message deleted", Toast.LENGTH_SHORT).show()
                            }
                            activeMessageForAction = null
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RealmeSpamRed)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Delete Message", fontWeight = FontWeight.Medium, color = RealmeSpamRed)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: MessageEntity,
    onLongClick: () -> Unit,
    onInspectUrl: (UrlInspection) -> Unit = {}
) {
    val context = LocalContext.current
    val isOutgoing = message.isOutgoing
    val alignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart

    val settings = AppSettingsManager.getInstance(context)
    val bubbleShapeMode by settings.bubbleShape.collectAsState()
    val textSizeMode by settings.chatTextSize.collectAsState()
    val accentPalette by settings.accentColor.collectAsState()

    val bubbleShape = remember(bubbleShapeMode, isOutgoing) {
        when (bubbleShapeMode) {
            BubbleShape.COLOROS -> RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isOutgoing) 18.dp else 4.dp,
                bottomEnd = if (isOutgoing) 4.dp else 18.dp
            )
            BubbleShape.PILL -> RoundedCornerShape(22.dp)
            BubbleShape.COMPACT -> RoundedCornerShape(8.dp)
        }
    }

    val bubbleColor = if (isOutgoing) {
        Color(accentPalette)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isOutgoing) {
        if (accentPalette == 0xFFFFC915) Color.Black else Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val timeFormatted = remember(message.timestamp) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp))
    }

    val detectedOtp = remember(message.body) { SmartMessageAnalyzer.extractOtp(message.body) }
    val urls = remember(message.body) { SmartMessageAnalyzer.inspectUrls(message.body) }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Surface(
                shape = bubbleShape,
                color = bubbleColor,
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = message.body,
                        color = textColor,
                        fontSize = textSizeMode.sizeSp.sp,
                        lineHeight = (textSizeMode.sizeSp + 6).sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (message.isStarred) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Starred",
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        Text(
                            text = timeFormatted,
                            fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.65f)
                        )

                        if (isOutgoing) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "Delivered",
                                modifier = Modifier.size(14.dp),
                                tint = textColor.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }

            // OTP Quick Copy Chip
            if (!isOutgoing && detectedOtp != null) {
                Spacer(modifier = Modifier.height(4.dp))
                AssistChip(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("OTP Code", detectedOtp)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            clip.description.extras = android.os.PersistableBundle().apply {
                                putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                            }
                        }
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied OTP: $detectedOtp", Toast.LENGTH_SHORT).show()
                    },
                    label = { Text("📋 Copy OTP: $detectedOtp", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }

            // Phishing URL Inspection Chip
            val suspiciousUrl = urls.firstOrNull { it.riskLevel == "High" } ?: urls.firstOrNull()
            if (!isOutgoing && suspiciousUrl != null) {
                Spacer(modifier = Modifier.height(4.dp))
                AssistChip(
                    onClick = { onInspectUrl(suspiciousUrl) },
                    label = {
                        Text(
                            if (suspiciousUrl.riskLevel == "High") "⚠️ Suspicious Link" else "🔗 Inspect Link",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (suspiciousUrl.riskLevel == "High") RealmeSpamRedBg else MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = if (suspiciousUrl.riskLevel == "High") RealmeSpamRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}
