package com.realme.spamblocker.sms.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realme.spamblocker.sms.data.local.entity.MessageEntity
import com.realme.spamblocker.sms.ml.BayesClassifier
import com.realme.spamblocker.sms.ui.theme.*
import com.realme.spamblocker.sms.util.SmartMessageAnalyzer
import com.realme.spamblocker.sms.util.UrlInspection
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RealmeMessageCard(
    message: MessageEntity,
    isSpamTab: Boolean,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onCardClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onTogglePin: () -> Unit = {},
    onToggleStar: () -> Unit = {},
    onMarkAsSpam: () -> Unit = {},
    onMarkAsNotSpam: () -> Unit = {},
    onDelete: () -> Unit = {},
    onDeleteAndSpam: () -> Unit = {},
    onMarkUnread: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dateString = rememberFormattedDate(message.timestamp)
    val displayName = message.senderName ?: message.sender
    var inspectingUrl by remember { mutableStateOf<UrlInspection?>(null) }

    val detectedOtp = remember(message.body) { SmartMessageAnalyzer.extractOtp(message.body) }
    val isBankImpersonation = remember(message.sender, message.body) {
        SmartMessageAnalyzer.isBankImpersonation(message.sender, message.body)
    }
    val urls = remember(message.body) { SmartMessageAnalyzer.inspectUrls(message.body) }

    val density = LocalDensity.current
    var targetOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    // Auto-close actions when entering selection mode
    LaunchedEffect(isSelectionMode) {
        if (isSelectionMode) {
            targetOffsetX = 0f
            dragOffset = 0f
            isDragging = false
        }
    }

    val animatedOffset by animateFloatAsState(
        targetValue = targetOffsetX,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
        label = "swipeOffset"
    )

    val currentOffset = if (isDragging) dragOffset else animatedOffset
    val maxLeftRevealDp = 210.dp
    val maxLeftRevealPx = with(density) { maxLeftRevealDp.toPx() }
    val rightSwipeThresholdDp = 80.dp
    val rightSwipeThresholdPx = with(density) { rightSwipeThresholdDp.toPx() }

    val draggableState = rememberDraggableState { delta ->
        dragOffset = (dragOffset + delta).coerceIn(-maxLeftRevealPx * 1.15f, rightSwipeThresholdPx * 1.5f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
    ) {
        // 1. BEHIND - LEFT ACTION: SWIPE RIGHT -> UNREAD / READ (iOS Blue)
        if (currentOffset > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFF007AFF))
                    .clickable {
                        targetOffsetX = 0f
                        onMarkUnread()
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.padding(start = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (message.isRead) Icons.Default.MarkChatUnread else Icons.Default.MarkEmailRead,
                        contentDescription = if (message.isRead) "Mark Unread" else "Mark Read",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (message.isRead) "Unread" else "Read",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // 2. BEHIND - RIGHT ACTIONS: SWIPE LEFT -> PIN, SPAM, DELETE (iOS 3-Button Tray)
        if (currentOffset < 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(maxLeftRevealDp)
                    .matchParentSize()
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Button 1: Pin
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFFFF9F0A))
                            .clickable {
                                targetOffsetX = 0f
                                onTogglePin()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = if (message.isPinned) "Unpin" else "Pin",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (message.isPinned) "Unpin" else "Pin",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Button 2: Spam / Restore
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (isSpamTab) RealmeHamGreen else Color(0xFF5E35B1))
                            .clickable {
                                targetOffsetX = 0f
                                if (isSpamTab) onMarkAsNotSpam() else onMarkAsSpam()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isSpamTab) Icons.Default.CheckCircle else Icons.Default.Shield,
                                contentDescription = if (isSpamTab) "Restore" else "Spam",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isSpamTab) "Restore" else "Spam",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Button 3: Delete
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFFFF3B30))
                            .clickable {
                                targetOffsetX = 0f
                                onDelete()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Delete",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 3. FOREGROUND MESSAGE CARD
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(currentOffset.roundToInt(), 0) }
                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.background)
                .combinedClickable(
                    onClick = {
                        if (targetOffsetX != 0f) {
                            targetOffsetX = 0f
                        } else {
                            onCardClick()
                        }
                    },
                    onLongClick = onLongClick
                )
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    enabled = !isSelectionMode,
                    onDragStarted = {
                        isDragging = true
                        dragOffset = targetOffsetX
                    },
                    onDragStopped = { velocity ->
                        isDragging = false
                        if (dragOffset > rightSwipeThresholdPx) {
                            targetOffsetX = 0f
                            onMarkUnread()
                        } else if (dragOffset < -rightSwipeThresholdPx || velocity < -500f) {
                            targetOffsetX = -maxLeftRevealPx
                        } else {
                            targetOffsetX = 0f
                        }
                    }
                )
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Selection Checkbox
                    if (isSelectionMode) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = if (isSelected) "Selected" else "Unselected",
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    // Contact Avatar (Contact photo if present, otherwise exact realme gray circle with white person silhouette)
                    Box {
                        ContactAvatar(
                            name = message.senderName,
                            phoneNumber = message.sender,
                            photoUri = message.contactPhotoUri,
                            size = 46.dp
                        )

                        // Unread Dot Indicator
                        if (!message.isRead && !isSpamTab) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .align(Alignment.TopEnd)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Text Content Column
                    Column(modifier = Modifier.weight(1f)) {
                        // Top Line: Sender Name & Pinned Icon + Date
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = displayName,
                                fontWeight = if (!message.isRead) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Right side: PushPin icon (if pinned) + Date String
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (message.isPinned && !isSpamTab) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = "Pinned",
                                        tint = Color(0xFF8E8E93),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }

                                if (message.isStarred) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Starred",
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }

                                Text(
                                    text = dateString,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color(0xFF8E8E93)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        // Bottom Line: Message preview
                        Text(
                            text = message.body,
                            fontSize = 13.5.sp,
                            fontWeight = if (!message.isRead) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = if (!message.isRead) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 19.sp
                        )

                        // Bank Impersonation Warning Banner
                        if (isBankImpersonation && !isSpamTab) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = RealmeSpamRedBg,
                                contentColor = RealmeSpamRed
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "High Risk: Mobile number impersonating bank",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Smart OTP & Phishing Link Chips
                        if (detectedOtp != null || urls.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (detectedOtp != null) {
                                    AssistChip(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("OTP Code", detectedOtp))
                                            Toast.makeText(context, "Copied OTP: $detectedOtp", Toast.LENGTH_SHORT).show()
                                        },
                                        label = { Text("📋 Copy OTP: $detectedOtp", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = Color(0xFF1E2838),
                                            labelColor = Color(0xFF4C8DFF)
                                        )
                                    )
                                }

                                val suspiciousUrl = urls.firstOrNull { it.riskLevel == "High" } ?: urls.firstOrNull()
                                if (suspiciousUrl != null) {
                                    AssistChip(
                                        onClick = { inspectingUrl = suspiciousUrl },
                                        label = {
                                            Text(
                                                if (suspiciousUrl.riskLevel == "High") "⚠️ Inspect Link" else "🔗 Link",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp
                                            )
                                        },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = if (suspiciousUrl.riskLevel == "High") RealmeSpamRedBg else Color(0xFF222226),
                                            labelColor = if (suspiciousUrl.riskLevel == "High") RealmeSpamRed else Color.LightGray
                                        )
                                    )
                                }
                            }
                        }

                        // Spam percentage tag (Spam tab only)
                        if (isSpamTab) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val percentage = (message.spamProbability * 100).toInt()
                            val threatReason = remember(message.sender, message.body) {
                                BayesClassifier.getThreatReason(message.body, message.sender)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⚠️ $percentage% Spam" + if (threatReason != null) " • $threatReason" else " Detected",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RealmeSpamRed
                                )

                                TextButton(
                                    onClick = onMarkAsNotSpam,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Restore", fontSize = 12.sp, color = RealmeHamGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
        }

        // Realme subtle divider between conversations (indented after avatar)
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 76.dp)
                .align(Alignment.BottomCenter)
        )
    }
}

    // PHISHING URL INSPECTOR MODAL
    if (inspectingUrl != null) {
        val inspection = inspectingUrl!!
        AlertDialog(
            onDismissRequest = { inspectingUrl = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (inspection.riskLevel == "High") Icons.Default.Warning else Icons.Default.Link,
                        contentDescription = null,
                        tint = if (inspection.riskLevel == "High") RealmeSpamRed else Color(0xFF4C8DFF)
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
}

private val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
private val dateFormatter = SimpleDateFormat("M/d/yyyy", Locale.getDefault())

@Composable
private fun rememberFormattedDate(timestamp: Long): String {
    return remember(timestamp) {
        val isToday = android.text.format.DateUtils.isToday(timestamp)
        if (isToday) {
            timeFormatter.format(Date(timestamp))
        } else if (android.text.format.DateUtils.isToday(timestamp + 86400000L)) {
            "Yesterday"
        } else {
            dateFormatter.format(Date(timestamp))
        }
    }
}
