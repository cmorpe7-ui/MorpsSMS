package com.realme.spamblocker.sms.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.realme.spamblocker.sms.util.ContactUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Fast in-memory caches to prevent repeated IPC and disk decoding
private val bitmapCache = LruCache<String, ImageBitmap>(150)
private val photoUriCache = LruCache<String, String>(250)
private val nonExistentPhotos = mutableSetOf<String>()

@Composable
fun ContactAvatar(
    name: String?,
    phoneNumber: String,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    photoUri: String? = null
) {
    val context = LocalContext.current
    val cleanNumber = remember(phoneNumber) { phoneNumber.trim() }

    // Instant in-memory check without touching ContentResolver
    var currentUri by remember(cleanNumber, photoUri) {
        mutableStateOf(photoUri ?: photoUriCache.get(cleanNumber))
    }

    var avatarBitmap by remember(currentUri) {
        mutableStateOf(currentUri?.let { bitmapCache.get(it) })
    }

    // Resolve photo URI strictly in background (IO dispatcher) - NEVER blocks Main UI thread
    LaunchedEffect(cleanNumber, photoUri) {
        if (photoUri != null) {
            currentUri = photoUri
            photoUriCache.put(cleanNumber, photoUri)
        } else if (currentUri == null && !nonExistentPhotos.contains(cleanNumber)) {
            val resolved = withContext(Dispatchers.IO) {
                ContactUtils.getContactPhotoUri(context, cleanNumber)
            }
            if (resolved != null) {
                photoUriCache.put(cleanNumber, resolved)
                currentUri = resolved
            } else {
                nonExistentPhotos.add(cleanNumber)
            }
        }
    }

    // Decode Bitmap strictly on IO thread if not already in cache
    LaunchedEffect(currentUri) {
        val uriStr = currentUri
        if (uriStr != null && avatarBitmap == null) {
            val cached = bitmapCache.get(uriStr)
            if (cached != null) {
                avatarBitmap = cached
            } else {
                withContext(Dispatchers.IO) {
                    try {
                        val stream = context.contentResolver.openInputStream(Uri.parse(uriStr))
                        val bmp = BitmapFactory.decodeStream(stream)
                        val imgBmp = bmp?.asImageBitmap()
                        if (imgBmp != null) {
                            bitmapCache.put(uriStr, imgBmp)
                            withContext(Dispatchers.Main) {
                                avatarBitmap = imgBmp
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    if (avatarBitmap != null) {
        Image(
            bitmap = avatarBitmap!!,
            contentDescription = name ?: phoneNumber,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        // Instant realme UI silhouette - renders at 0ms latency with zero frame drop
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF2C2C2E)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = name ?: phoneNumber,
                tint = Color.White,
                modifier = Modifier.size(size * 0.58f)
            )
        }
    }
}
