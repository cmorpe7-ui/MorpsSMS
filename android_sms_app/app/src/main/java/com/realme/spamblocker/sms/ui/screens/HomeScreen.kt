package com.realme.spamblocker.sms.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realme.spamblocker.sms.data.local.entity.MessageEntity
import com.realme.spamblocker.sms.ui.components.CustomizationAndSettingsDialog
import com.realme.spamblocker.sms.ui.components.RealmeMessageCard
import com.realme.spamblocker.sms.ui.theme.RealmeHamGreen
import com.realme.spamblocker.sms.ui.theme.RealmeHamGreenBg
import com.realme.spamblocker.sms.ui.theme.RealmeSpamRed
import com.realme.spamblocker.sms.ui.theme.RealmeSpamRedBg
import com.realme.spamblocker.sms.ui.viewmodel.MessagesViewModel
import com.realme.spamblocker.sms.util.MessageCategory
import com.realme.spamblocker.sms.util.SmartMessageAnalyzer
import com.realme.spamblocker.sms.util.TransactionInfo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MessagesViewModel,
    onComposeClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isViewingSpam by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSimulatorDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showDigestDialog by remember { mutableStateOf(false) }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    val inboxMessages by viewModel.inboxMessages.collectAsState()
    val spamMessages by viewModel.spamMessages.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    val spamCount by viewModel.spamCount.collectAsState()

    val selectedThreads by viewModel.selectedThreads.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val starredMessages by viewModel.starredMessages.collectAsState()

    var selectedCategory by remember { mutableStateOf(MessageCategory.ALL) }

    val filteredInboxList = remember(inboxMessages, starredMessages, selectedCategory) {
        when (selectedCategory) {
            MessageCategory.ALL -> inboxMessages
            MessageCategory.STARRED -> {
                if (starredMessages.isEmpty()) {
                    emptyList()
                } else {
                    val starredSenders = starredMessages.map { it.sender }.toSet()
                    val matchedInbox = inboxMessages.filter { starredSenders.contains(it.sender) }
                    val matchedSenders = matchedInbox.map { it.sender }.toSet()
                    val otherStarred = starredMessages.filter { !matchedSenders.contains(it.sender) }.distinctBy { it.sender }
                    matchedInbox + otherStarred
                }
            }
            MessageCategory.PERSONAL -> {
                inboxMessages.filter { msg ->
                    SmartMessageAnalyzer.determineCategory(
                        sender = msg.sender,
                        senderName = msg.senderName,
                        body = msg.body,
                        isSpam = msg.isSpam,
                        isStarred = false
                    ) == MessageCategory.PERSONAL
                }
            }
            MessageCategory.TRANSACTIONS -> {
                inboxMessages.filter { msg ->
                    SmartMessageAnalyzer.determineCategory(
                        sender = msg.sender,
                        senderName = msg.senderName,
                        body = msg.body,
                        isSpam = msg.isSpam,
                        isStarred = false
                    ) == MessageCategory.TRANSACTIONS
                }
            }
            MessageCategory.PROMOTIONS -> {
                inboxMessages.filter { msg ->
                    SmartMessageAnalyzer.determineCategory(
                        sender = msg.sender,
                        senderName = msg.senderName,
                        body = msg.body,
                        isSpam = msg.isSpam,
                        isStarred = false
                    ) == MessageCategory.PROMOTIONS
                }
            }
        }
    }

    val currentList = if (isViewingSpam) spamMessages else filteredInboxList

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
            ) {
                // 1. SELECTION MODE TOP BAR
                AnimatedVisibility(
                    visible = isSelectionMode,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = "Close selection", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${selectedThreads.size} selected",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { viewModel.selectAll(currentList.map { it.sender }) }) {
                                Text("Select all", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            }

                            // 📌 PIN CONVERSATION ACTION
                            if (!isViewingSpam) {
                                val selectedMessages = currentList.filter { selectedThreads.contains(it.sender) }
                                val allArePinned = selectedMessages.isNotEmpty() && selectedMessages.all { it.isPinned }

                                IconButton(onClick = {
                                    selectedMessages.forEach { msg ->
                                        viewModel.togglePin(msg)
                                    }
                                    Toast.makeText(
                                        context,
                                        if (allArePinned) "Conversations unpinned" else "Conversations pinned to top",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    viewModel.clearSelection()
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = if (allArePinned) "Unpin" else "Pin",
                                        tint = if (allArePinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }

                            // DELETE ACTION
                            IconButton(onClick = { viewModel.deleteSelectedThreads() }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete selected",
                                    tint = Color.White
                                )
                            }

                            // SPAM OR RESTORE ACTION
                            if (!isViewingSpam) {
                                IconButton(onClick = {
                                    val samples = currentList.filter { selectedThreads.contains(it.sender) }.associate { it.sender to it.body }
                                    viewModel.markSelectedAsSpam(samples)
                                    Toast.makeText(context, "Selected marked as Spam & blocked 🛡️", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.Shield, contentDescription = "Mark as Spam", tint = RealmeSpamRed)
                                }
                            } else {
                                IconButton(onClick = {
                                    viewModel.markSelectedAsNotSpam(currentList)
                                    Toast.makeText(context, "Selected restored to Inbox & unblocked", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Restore to Inbox", tint = RealmeHamGreen)
                                }
                            }
                        }
                    }
                }

                // 2. STANDARD REALME UI HEADER (Matching screenshot 1:1)
                AnimatedVisibility(
                    visible = !isSelectionMode && !isSearching,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Title: "Messages" or "< Spam and blocked"
                        if (isViewingSpam) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { isViewingSpam = false }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Spam and blocked",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        } else {
                            Text(
                                text = "Messages",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        // Right action icons: Search & 3-dots Menu
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.setSearching(true) }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }

                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Options",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    modifier = Modifier.background(Color(0xFF222226))
                                ) {
                                    if (!isViewingSpam) {
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.Shield, contentDescription = null, tint = RealmeSpamRed, modifier = Modifier.size(18.dp))
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Text("Spam and blocked", color = Color.White)
                                                    }
                                                    if (spamCount > 0) {
                                                        Surface(shape = CircleShape, color = RealmeSpamRed) {
                                                            Text(
                                                                text = spamCount.toString(),
                                                                color = Color.White,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            onClick = {
                                                showMenu = false
                                                isViewingSpam = true
                                            }
                                        )
                                    } else {
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Inbox, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text("Back to Inbox", color = Color.White)
                                                }
                                            },
                                            onClick = {
                                                showMenu = false
                                                isViewingSpam = false
                                            }
                                        )
                                    }

                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.DoneAll, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text("Mark all as read", color = Color.White)
                                            }
                                        },
                                        onClick = {
                                            showMenu = false
                                            inboxMessages.forEach { msg ->
                                                viewModel.toggleReadStatus(msg.copy(isRead = false))
                                            }
                                            Toast.makeText(context, "All messages marked as read", Toast.LENGTH_SHORT).show()
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.QueryStats, contentDescription = null, tint = Color(0xFF4C8DFF), modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text("Financial Digest", color = Color.White)
                                            }
                                        },
                                        onClick = {
                                            showMenu = false
                                            showDigestDialog = true
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Science, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text("SMS Simulator", color = Color.White)
                                            }
                                        },
                                        onClick = {
                                            showMenu = false
                                            showSimulatorDialog = true
                                        }
                                    )

                                    HorizontalDivider(color = Color(0xFF333338))

                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Settings, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text("Settings", color = Color.White)
                                            }
                                        },
                                        onClick = {
                                            showMenu = false
                                            showSettingsDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. SEARCH FIELD
                AnimatedVisibility(
                    visible = isSearching && !isSelectionMode,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search messages, contacts...", color = Color.Gray) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
                        },
                        trailingIcon = {
                            IconButton(onClick = { viewModel.setSearching(false) }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Search", tint = Color.White)
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF4C8DFF),
                            unfocusedBorderColor = Color(0xFF333338),
                            focusedContainerColor = Color(0xFF161618),
                            unfocusedContainerColor = Color(0xFF161618),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                // 4. TINY CATEGORIES ROW (Matching realme UI)
                AnimatedVisibility(
                    visible = !isViewingSpam && !isSelectionMode && !isSearching,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MessageCategory.values().forEach { category ->
                            val isSelected = (selectedCategory == category)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (!isSelected) BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline) else null,
                                onClick = { selectedCategory = category },
                                modifier = Modifier.height(28.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val (icon, iconTint) = when (category) {
                                        MessageCategory.ALL -> Icons.Default.AllInbox to (if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        MessageCategory.PERSONAL -> Icons.Default.Person to (if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        MessageCategory.TRANSACTIONS -> Icons.Default.AccountBalanceWallet to (if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        MessageCategory.PROMOTIONS -> Icons.Default.LocalOffer to (if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        MessageCategory.STARRED -> Icons.Default.Star to (if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFFFFB300))
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = iconTint,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (category == MessageCategory.STARRED && starredMessages.isNotEmpty()) "Starred (${starredMessages.size})" else category.title,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        letterSpacing = 0.2.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isSelectionMode && !isViewingSpam) {
                FloatingActionButton(
                    onClick = onComposeClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Message",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (currentList.isEmpty()) {
                EmptyStateView(
                    isSpamTab = isViewingSpam,
                    isSearchActive = searchQuery.isNotBlank(),
                    selectedCategory = if (!isViewingSpam && searchQuery.isBlank()) selectedCategory else null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // Pinned conversations sort first naturally via Room ORDER BY isPinned DESC, timestamp DESC
                    items(currentList, key = { it.id }) { message ->
                        val isSelected = selectedThreads.contains(message.sender)

                        RealmeMessageCard(
                            message = message,
                            isSpamTab = isViewingSpam,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onCardClick = {
                                if (isSelectionMode) {
                                    viewModel.toggleSelectThread(message.sender)
                                } else {
                                    ConversationActivity.start(
                                        context = context,
                                        sender = message.sender,
                                        senderName = message.senderName,
                                        isSpam = isViewingSpam
                                    )
                                }
                            },
                            onLongClick = {
                                viewModel.toggleSelectThread(message.sender)
                            },
                            onTogglePin = {
                                viewModel.togglePin(message)
                                Toast.makeText(
                                    context,
                                    if (message.isPinned) "Unpinned" else "Pinned to top",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onToggleStar = { viewModel.toggleStar(message) },
                            onMarkAsSpam = { viewModel.markAsSpam(message) },
                            onMarkAsNotSpam = { viewModel.markAsNotSpam(message) },
                            onDelete = { viewModel.deleteThread(message.sender) },
                            onDeleteAndSpam = { viewModel.deleteAndMarkSpam(message) },
                            onMarkUnread = {
                                viewModel.toggleReadStatus(message)
                                Toast.makeText(
                                    context,
                                    if (message.isRead) "Marked as unread" else "Marked as read",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
            }
        }
    }

    // FINANCIAL DIGEST / EXPENSE TRACKER MODAL
    if (showDigestDialog) {
        var transactions by remember { mutableStateOf<List<TransactionInfo>>(emptyList()) }
        LaunchedEffect(Unit) {
            val all = viewModel.getAllMessages()
            val parsed = all.mapNotNull { SmartMessageAnalyzer.extractTransaction(it.body, it.timestamp) }
            transactions = parsed.sortedByDescending { it.timestamp }
        }

        AlertDialog(
            onDismissRequest = { showDigestDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color(0xFF4C8DFF))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Financial Spending Digest")
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Aggregated purely on-device from your SMS banking receipts (GCash, Maya, Banks).",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (transactions.isEmpty()) {
                        Text("No payment receipts or transaction texts detected yet.", fontSize = 13.sp)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(transactions) { tx ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF1C1C1E),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(tx.serviceOrMerchant ?: "Payment", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                            val dateStr = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(tx.timestamp))
                                            Text(dateStr, fontSize = 11.sp, color = Color.Gray)
                                        }
                                        Text(
                                            "₱ ${tx.amount}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF4C8DFF)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDigestDialog = false }) {
                    Text("Done", color = Color(0xFF4C8DFF))
                }
            }
        )
    }

    if (showSimulatorDialog) {
        SmsSimulatorDialog(
            onDismiss = { showSimulatorDialog = false },
            onSimulate = { sender, text ->
                viewModel.simulateIncomingSms(sender, text)
                showSimulatorDialog = false
            }
        )
    }

    if (showSettingsDialog) {
        CustomizationAndSettingsDialog(
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
private fun EmptyStateView(
    isSpamTab: Boolean,
    isSearchActive: Boolean,
    selectedCategory: MessageCategory? = null,
    modifier: Modifier = Modifier
) {
    val (emptyIcon, emptyText) = when {
        isSearchActive -> Icons.Default.Search to "No messages match your search"
        isSpamTab -> Icons.Default.Shield to "No spam messages filtered"
        selectedCategory == MessageCategory.STARRED -> Icons.Default.StarOutline to "No starred messages"
        selectedCategory == MessageCategory.TRANSACTIONS -> Icons.Default.AccountBalanceWallet to "No transaction messages"
        selectedCategory == MessageCategory.PROMOTIONS -> Icons.Default.LocalOffer to "No promotional messages"
        selectedCategory == MessageCategory.PERSONAL -> Icons.Default.PersonOutline to "No personal messages"
        else -> Icons.Default.ChatBubbleOutline to "No messages"
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = emptyIcon,
                contentDescription = null,
                tint = Color(0xFF333338),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = emptyText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF8E8E93)
            )
        }
    }
}

@Composable
fun SmsSimulatorDialog(
    onDismiss: () -> Unit,
    onSimulate: (sender: String, body: String) -> Unit
) {
    var senderInput by remember { mutableStateOf("09171234567") }
    var bodyInput by remember { mutableStateOf("GCASH: Congratulations! You won PHP 50,000 in our lucky draw. Click bit.ly/gcash-claim to claim now.") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Science, contentDescription = null, tint = Color(0xFF4C8DFF))
                Spacer(modifier = Modifier.width(8.dp))
                Text("SMS Spam Simulator")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Simulate an incoming SMS to test the on-device Bayesian classifier and Philippine spam heuristics:",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
                OutlinedTextField(
                    value = senderInput,
                    onValueChange = { senderInput = it },
                    label = { Text("Sender Number / ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bodyInput,
                    onValueChange = { bodyInput = it },
                    label = { Text("Message Body") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Quick presets:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = {
                            senderInput = "09998887766"
                            bodyInput = "URGENT BDO ALERT: Your account is suspended due to suspicious activity. Verify immediately at http://bdo-security.top"
                        },
                        label = { Text("Bank Phishing", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            senderInput = "09451122334"
                            bodyInput = "VIP SLOT CASINO: Claim your 500 PHP free bonus now! Register at tinyurl.com/jiliclub"
                        },
                        label = { Text("Casino Spam", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            senderInput = "09185551234"
                            bodyInput = "Hey bro, are you available tomorrow for lunch? Let me know!"
                        },
                        label = { Text("Legit Ham", fontSize = 11.sp) }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (senderInput.isNotBlank() && bodyInput.isNotBlank()) {
                        onSimulate(senderInput.trim(), bodyInput.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4C8DFF))
            ) {
                Text("Test Ingestion", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
