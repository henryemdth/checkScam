package com.checkscam.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.checkscam.calldetection.NativeCallAccessibilityService
import com.checkscam.calldetection.PermissionHelper

@Composable
fun PermissionGuideScreen(
    modifier: Modifier = Modifier,
    onAllPermissionsGranted: () -> Unit = {}
) {
    val context = LocalContext.current
    var accessibilityEnabled by remember {
        mutableStateOf(PermissionHelper.isAccessibilityServiceEnabled(context, NativeCallAccessibilityService::class.java))
    }
    var notificationListenerEnabled by remember {
        mutableStateOf(PermissionHelper.isNotificationListenerEnabled(context))
    }
    var phoneStateGranted by remember {
        mutableStateOf(PermissionHelper.hasPhoneStatePermission(context))
    }
    var notificationsGranted by remember {
        mutableStateOf(hasPostNotificationsPermission(context))
    }

    val phoneStateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        phoneStateGranted = granted
    }

    val postNotificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "CheckScam needs permissions to detect calls",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        PermissionItem(
            title = "Phone State",
            description = "Allows detecting native calls via the telephony service",
            enabled = phoneStateGranted,
            onOpenSettings = {
                if (!phoneStateGranted) {
                    phoneStateLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionItem(
            title = "Accessibility Service",
            description = "Allows detecting when you are on a native phone call",
            enabled = accessibilityEnabled,
            onOpenSettings = {
                context.startActivity(PermissionHelper.openAccessibilitySettings(context))
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionItem(
            title = "Notification Listener",
            description = "Allows detecting calls from WhatsApp, Telegram, Zoom, and Meet",
            enabled = notificationListenerEnabled,
            onOpenSettings = {
                context.startActivity(PermissionHelper.openNotificationListenerSettings(context))
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionItem(
            title = "Notifications",
            description = "Enables heads-up scam alerts (CRITICAL pulls over the dialer)",
            enabled = notificationsGranted,
            onOpenSettings = {
                if (!notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else if (!notificationsGranted) {
                    // Pre-API-33 the permission is granted at install time.
                    notificationsGranted = true
                }
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                accessibilityEnabled = PermissionHelper.isAccessibilityServiceEnabled(context, NativeCallAccessibilityService::class.java)
                notificationListenerEnabled = PermissionHelper.isNotificationListenerEnabled(context)
                phoneStateGranted = PermissionHelper.hasPhoneStatePermission(context)
                notificationsGranted = hasPostNotificationsPermission(context)
                if (!phoneStateGranted) {
                    phoneStateLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                } else if (accessibilityEnabled && notificationListenerEnabled) {
                    onAllPermissionsGranted()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Check permissions")
        }
    }
}

private fun hasPostNotificationsPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun PermissionItem(
    title: String,
    description: String,
    enabled: Boolean,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        OutlinedButton(onClick = onOpenSettings) {
            Text(if (enabled) "Enabled" else "Enable")
        }
    }
}
