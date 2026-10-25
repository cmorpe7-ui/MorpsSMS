package com.realme.spamblocker.sms.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realme.spamblocker.sms.SpamBlockerApp
import com.realme.spamblocker.sms.data.local.prefs.*
import com.realme.spamblocker.sms.data.repository.SmsRepository
import com.realme.spamblocker.sms.ui.theme.RealmeHamGreen
import com.realme.spamblocker.sms.ui.theme.RealmeSpamRed
import com.realme.spamblocker.sms.util.AppUpdateInfo
import com.realme.spamblocker.sms.util.AppUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CustomizationAndSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SpamBlockerApp
    val repository = app.repository
    val wordStatDao = app.database.wordStatDao()
    val scope = rememberCoroutineScope()
    val settings = AppSettingsManager.getInstance(context)

    // Observed Preferences
    val themeMode by settings.themeMode.collectAsState()
    val currentAccent by settings.accentColor.collectAsState()
    val bubbleShape by settings.bubbleShape.collectAsState()
    val chatTextSize by settings.chatTextSize.collectAsState()
    val spamThreshold by settings.spamThreshold.collectAsState()
    val whitelistContacts by settings.whitelistContacts.collectAsState()
    val philippineShield by settings.philippineShield.collectAsState()
    val notificationPrivacy by settings.notificationPrivacy.collectAsState()
    val notificationVibration by settings.notificationVibration.collectAsState()

    var selectedSettingsTab by remember { mutableIntStateOf(0) }
    var spamWordsCount by remember { mutableIntStateOf(0) }
    var hamWordsCount by remember { mutableIntStateOf(0) }

    val blockedNumbers by repository.getBlockedNumbers().collectAsState(initial = emptyList())
    var newNumberToBlock by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            spamWordsCount = wordStatDao.getTotalSpamWords() ?: 0
            hamWordsCount = wordStatDao.getTotalHamWords() ?: 0
        }
    }

    val tabs = listOf("Appearance", "AI & Filters", "Blacklist", "Notifications", "Data & Backup", "Update")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Settings & Customization",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable/Flexible Tab Header
                ScrollableTabRow(
                    selectedTabIndex = selectedSettingsTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 0.dp
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedSettingsTab == index,
                            onClick = { selectedSettingsTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedSettingsTab == index) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }
                }
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                when (selectedSettingsTab) {
                    0 -> AppearanceTab(
                        themeMode = themeMode,
                        currentAccent = currentAccent,
                        bubbleShape = bubbleShape,
                        chatTextSize = chatTextSize,
                        onThemeChange = { settings.setThemeMode(it) },
                        onAccentChange = { settings.setAccentColor(it) },
                        onBubbleChange = { settings.setBubbleShape(it) },
                        onFontChange = { settings.setChatTextSize(it) }
                    )
                    1 -> AiFiltersTab(
                        spamThreshold = spamThreshold,
                        whitelistContacts = whitelistContacts,
                        philippineShield = philippineShield,
                        spamWordsCount = spamWordsCount,
                        hamWordsCount = hamWordsCount,
                        onThresholdChange = { settings.setSpamThreshold(it) },
                        onWhitelistChange = { settings.setWhitelistContacts(it) },
                        onShieldChange = { settings.setPhilippineShield(it) },
                        onRescanClick = {
                            scope.launch {
                                repository.syncExistingSms(context, forceFullSync = true)
                                Toast.makeText(context, "Rescanned phone SMS history", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    2 -> BlacklistTab(
                        blockedNumbers = blockedNumbers.map { it.phoneNumber to it.reason },
                        newNumber = newNumberToBlock,
                        onNumberChange = { newNumberToBlock = it },
                        onAddBlock = {
                            if (newNumberToBlock.isNotBlank()) {
                                val num = newNumberToBlock.trim()
                                newNumberToBlock = ""
                                scope.launch {
                                    repository.blockNumber(num)
                                    Toast.makeText(context, "Number added to blacklist", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onUnblock = { num ->
                            scope.launch {
                                repository.unblockNumber(num)
                                Toast.makeText(context, "Unblocked", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    3 -> NotificationsTab(
                        privacy = notificationPrivacy,
                        vibration = notificationVibration,
                        onPrivacyChange = { settings.setNotificationPrivacy(it) },
                        onVibrationChange = { settings.setNotificationVibration(it) }
                    )
                    4 -> DataBackupTab(
                        repository = repository
                    )
                    5 -> AboutAndUpdateTab()
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun AppearanceTab(
    themeMode: AppThemeMode,
    currentAccent: Long,
    bubbleShape: BubbleShape,
    chatTextSize: ChatTextSize,
    onThemeChange: (AppThemeMode) -> Unit,
    onAccentChange: (Long) -> Unit,
    onBubbleChange: (BubbleShape) -> Unit,
    onFontChange: (ChatTextSize) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // 1. THEME MODE
        item {
            Text("Theme Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AppThemeMode.values().forEach { mode ->
                    val isSelected = themeMode == mode
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onThemeChange(mode) }
                    ) {
                        Text(
                            text = mode.title.split(" ")[0],
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        // 2. ACCENT COLOR PALETTE
        item {
            Text("Accent Color Palette", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(AvailableAccents) { palette ->
                    val isSelected = currentAccent == palette.colorValue
                    val color = Color(palette.colorValue)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onAccentChange(palette.colorValue) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (isSelected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (palette.colorValue == 0xFFFFC915) Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = palette.name.split(" ")[0],
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 3. CHAT BUBBLE SHAPE
        item {
            Text("Chat Bubble Style", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BubbleShape.values().forEach { shape ->
                    val isSelected = bubbleShape == shape
                    Surface(
                        shape = RoundedCornerShape(shape.radiusDp.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onBubbleChange(shape) }
                    ) {
                        Text(
                            text = shape.title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        // 4. CHAT FONT SIZE
        item {
            Text("Message Text Size", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChatTextSize.values().forEach { size ->
                    val isSelected = chatTextSize == size
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onFontChange(size) }
                    ) {
                        Text(
                            text = "${size.title} (${size.sizeSp})",
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiFiltersTab(
    spamThreshold: Float,
    whitelistContacts: Boolean,
    philippineShield: Boolean,
    spamWordsCount: Int,
    hamWordsCount: Int,
    onThresholdChange: (Float) -> Unit,
    onWhitelistChange: (Boolean) -> Unit,
    onShieldChange: (Boolean) -> Unit,
    onRescanClick: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            Text("AI Sensitivity Level", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val levels = listOf("Aggressive" to 0.60f, "Balanced" to 0.70f, "Strict" to 0.85f)
                levels.forEach { (label, value) ->
                    val isSelected = spamThreshold == value
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onThresholdChange(value) }
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        item {
            HorizontalDivider()
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Whitelist Saved Contacts", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Contacts are never flagged as spam", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = whitelistContacts,
                    onCheckedChange = onWhitelistChange
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Philippine Scam Shield", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Active detection for GCash, Maya, Casino & Loan scam patterns", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = philippineShield,
                    onCheckedChange = onShieldChange
                )
            }
        }

        item {
            HorizontalDivider()
        }

        item {
            Text("On-Device Vocabulary", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Spam Words", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$spamWordsCount words", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RealmeSpamRed)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Ham Words", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$hamWordsCount words", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RealmeHamGreen)
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onRescanClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Re-scan Phone History")
            }
        }
    }
}

@Composable
private fun BlacklistTab(
    blockedNumbers: List<Pair<String, String>>,
    newNumber: String,
    onNumberChange: (String) -> Unit,
    onAddBlock: () -> Unit,
    onUnblock: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newNumber,
                onValueChange = onNumberChange,
                placeholder = { Text("e.g. +639941234567") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onAddBlock,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Block")
            }
        }

        if (blockedNumbers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No phone numbers manually blocked",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(modifier = Modifier.height(200.dp)) {
                items(blockedNumbers) { (phone, reason) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(phone, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(reason, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onUnblock(phone) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Unblock", tint = RealmeSpamRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationsTab(
    privacy: NotificationPrivacy,
    vibration: Boolean,
    onPrivacyChange: (NotificationPrivacy) -> Unit,
    onVibrationChange: (Boolean) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Notification Privacy & Preview", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

        NotificationPrivacy.values().forEach { option ->
            val isSelected = privacy == option
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPrivacyChange(option) }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onPrivacyChange(option) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = option.title,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                }
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Vibrate on Incoming SMS", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("Play haptic vibration when receiving message", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = vibration,
                onCheckedChange = onVibrationChange
            )
        }
    }
}

@Composable
private fun DataBackupTab(repository: SmsRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var restoreText by remember { mutableStateOf("") }
    var isExporting by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Local Data Backup & Restore", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(
            "Safeguard your messages and AI training locally. No cloud transmission; your data remains 100% private on your device.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 1. EXPORT
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Export Backup", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Generates a JSON backup string containing your messages and settings.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        isExporting = true
                        scope.launch {
                            val json = repository.exportBackupJson()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("SMS_Backup", json))
                            Toast.makeText(context, "Backup copied to clipboard!", Toast.LENGTH_LONG).show()
                            isExporting = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isExporting) "Exporting..." else "Copy Backup JSON to Clipboard")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        isExporting = true
                        scope.launch {
                            val json = repository.exportBackupJson()
                            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Realme_SMS_Backup.json")
                                putExtra(android.content.Intent.EXTRA_TEXT, json)
                            }
                            context.startActivity(android.content.Intent.createChooser(sendIntent, "Share or Save Backup"))
                            isExporting = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share / Save Backup File")
                }
            }
        }

        // 2. RESTORE
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Restore Backup", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Paste a previously exported JSON backup to restore messages.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = restoreText,
                    onValueChange = { restoreText = it },
                    placeholder = { Text("Paste JSON backup here...") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        if (restoreText.isNotBlank()) {
                            scope.launch {
                                val success = repository.importBackupJson(restoreText.trim())
                                if (success) {
                                    Toast.makeText(context, "Backup restored successfully!", Toast.LENGTH_SHORT).show()
                                    restoreText = ""
                                } else {
                                    Toast.makeText(context, "Invalid backup format", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    enabled = restoreText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restore from Pasted JSON")
                }
            }
        }
    }
}

@Composable
private fun AboutAndUpdateTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val currentVersion = remember { AppUpdateManager.getCurrentVersion(context) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // App Info Header Card
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("MorpsSMS", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "realme UI & ColorOS Spam Blocker",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "v$currentVersion (Installed)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Software Update Card
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Software Update", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "Check GitHub Releases for the latest features, security patches, and spam engine updates.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (updateResult != null) {
                        val info = updateResult!!
                        if (info.hasUpdate) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.NewReleases,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "New Update: ${info.latestVersion}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (info.releaseNotes.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = info.releaseNotes.take(180) + if (info.releaseNotes.length > 180) "..." else "",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            if (info.downloadUrl != null) {
                                Button(
                                    onClick = {
                                        AppUpdateManager.startDownload(context, info.downloadUrl, "MorpsSMS_${info.latestVersion}.apk")
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Download & Install Update")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = RealmeHamGreen.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = RealmeHamGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "You're on the latest version ($currentVersion)!",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = RealmeHamGreen
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = "Error: $errorMessage",
                            color = RealmeSpamRed,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Check for Updates Button
                    OutlinedButton(
                        onClick = {
                            isChecking = true
                            errorMessage = null
                            scope.launch {
                                val result = AppUpdateManager.checkForUpdate(context)
                                isChecking = false
                                result.onSuccess { info ->
                                    updateResult = info
                                    if (!info.hasUpdate) {
                                        Toast.makeText(context, "MorpsSMS is up to date!", Toast.LENGTH_SHORT).show()
                                    }
                                }.onFailure { e ->
                                    errorMessage = e.localizedMessage ?: "Failed to reach GitHub"
                                    Toast.makeText(context, "Update check failed: $errorMessage", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isChecking,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking GitHub...")
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Check for Updates")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            AppUpdateManager.openBrowser(context, "https://github.com/cmorpe7-ui/MorpsSMS/releases")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View Releases on GitHub")
                    }
                }
            }
        }

        // About Project Card
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("About Project", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "MorpsSMS is an open-source, privacy-first SMS messenger with local machine learning spam detection. Licensed under MIT.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Repository: github.com/cmorpe7-ui/MorpsSMS",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            AppUpdateManager.openBrowser(context, "https://github.com/cmorpe7-ui/MorpsSMS")
                        }
                    )
                }
            }
        }
    }
}


