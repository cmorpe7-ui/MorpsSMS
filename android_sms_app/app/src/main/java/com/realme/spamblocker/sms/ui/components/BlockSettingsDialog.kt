package com.realme.spamblocker.sms.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
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
import com.realme.spamblocker.sms.ui.theme.RealmeHamGreen
import com.realme.spamblocker.sms.ui.theme.RealmeSpamRed
import com.realme.spamblocker.sms.ui.theme.RealmeYellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BlockSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SpamBlockerApp
    val repository = app.repository
    val wordStatDao = app.database.wordStatDao()
    val scope = rememberCoroutineScope()

    var selectedSettingsTab by remember { mutableIntStateOf(0) } // 0 = Rules, 1 = Blacklist
    var spamWordsCount by remember { mutableIntStateOf(0) }
    var hamWordsCount by remember { mutableIntStateOf(0) }
    var selectedThreshold by remember { mutableFloatStateOf(0.70f) }

    val blockedNumbers by repository.getBlockedNumbers().collectAsState(initial = emptyList())
    var newNumberToBlock by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            spamWordsCount = wordStatDao.getTotalSpamWords() ?: 0
            hamWordsCount = wordStatDao.getTotalHamWords() ?: 0
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = RealmeYellow
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Block & Filter Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                TabRow(
                    selectedTabIndex = selectedSettingsTab,
                    containerColor = Color.Transparent,
                    contentColor = RealmeYellow
                ) {
                    Tab(
                        selected = selectedSettingsTab == 0,
                        onClick = { selectedSettingsTab = 0 },
                        text = { Text("AI Filter Rules", fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedSettingsTab == 1,
                        onClick = { selectedSettingsTab = 1 },
                        text = { Text("Blocked Numbers (${blockedNumbers.size})", fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
        },
        text = {
            if (selectedSettingsTab == 0) {
                // TAB 1: AI RULES & STATS
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Filter Sensitivity",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val levels = listOf("Aggressive (60%)" to 0.60f, "Balanced (70%)" to 0.70f, "Relaxed (85%)" to 0.85f)
                        levels.forEach { (label, value) ->
                            val isSelected = selectedThreshold == value
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) RealmeYellow else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.weight(1f)
                            ) {
                                TextButton(
                                    onClick = { selectedThreshold = value },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label.split(" ")[0],
                                        color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Divider()

                    Text(
                        text = "On-Device Learning Status",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Spam Words Learned", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$spamWordsCount words", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RealmeSpamRed)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Ham Words Learned", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$hamWordsCount words", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RealmeHamGreen)
                        }
                    }

                    Divider()

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                repository.syncExistingSms(context)
                                Toast.makeText(context, "Rescanned phone SMS history", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Re-scan Phone SMS History")
                    }
                }
            } else {
                // TAB 2: MANUAL NUMBER BLACKLIST
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newNumberToBlock,
                            onValueChange = { newNumberToBlock = it },
                            placeholder = { Text("e.g. +639941234567") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newNumberToBlock.isNotBlank()) {
                                    val num = newNumberToBlock.trim()
                                    newNumberToBlock = ""
                                    scope.launch {
                                        repository.blockNumber(num)
                                        Toast.makeText(context, "Number added to blacklist", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RealmeYellow, contentColor = Color.Black),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Block")
                        }
                    }

                    if (blockedNumbers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No phone numbers manually blocked", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.height(180.dp)) {
                            items(blockedNumbers, key = { it.phoneNumber }) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(item.phoneNumber, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text(item.reason, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                repository.unblockNumber(item.phoneNumber)
                                                Toast.makeText(context, "Unblocked", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Unblock", tint = RealmeSpamRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = RealmeYellow, contentColor = Color.Black)
            ) {
                Text("Done")
            }
        }
    )
}
