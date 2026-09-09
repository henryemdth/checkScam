package com.checkscam.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.checkscam.app.CheckScamApplication
import com.checkscam.app.data.SettingsRepository
import com.checkscam.app.ui.theme.CheckScamTheme
import com.checkscam.calldetection.PermissionHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CheckScamTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as CheckScamApplication
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsRepository(context) }
    val detectionService = remember { app.callDetectionService }
    val audioCoordinator = remember { app.callAudioCoordinator }
    val historyStore = remember { app.alertHistoryStore }

    var settingsLoaded by remember { mutableStateOf(false) }
    var consentAccepted by remember { mutableStateOf(false) }
    var permissionsGranted by remember {
        mutableStateOf(PermissionHelper.areAllPermissionsGranted(context))
    }

    val pipelineStatus by audioCoordinator.status.collectAsStateWithLifecycle()
    val history by historyStore.history.collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(Unit) {
        consentAccepted = settings.isDetectionEnabled()
        settingsLoaded = true
        permissionsGranted = PermissionHelper.areAllPermissionsGranted(context)
        if (consentAccepted && permissionsGranted) {
            detectionService.start()
            audioCoordinator.start()
        }
    }

    if (!settingsLoaded) return

    if (!consentAccepted) {
        ConsentScreen(
            modifier = modifier,
            onAccept = {
                settings.setDetectionEnabled(true)
                consentAccepted = true
            }
        )
        return
    }

    if (!permissionsGranted) {
        PermissionGuideScreen(
            modifier = modifier,
            onAllPermissionsGranted = {
                permissionsGranted = PermissionHelper.areAllPermissionsGranted(context)
                if (permissionsGranted) {
                    detectionService.start()
                    audioCoordinator.start()
                }
            }
        )
        return
    }

    DashboardScreen(
        modifier = modifier,
        status = pipelineStatus,
        history = history,
        onClearHistory = {
            scope.launch { historyStore.clear() }
        },
        onDisable = {
            audioCoordinator.stop()
            detectionService.stop()
            settings.setDetectionEnabled(false)
            consentAccepted = false
        }
    )
}

@Composable
fun ConsentScreen(
    modifier: Modifier = Modifier,
    onAccept: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to CheckScam",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "CheckScam detects phone scam patterns during calls and raises local alerts. " +
                "When enabled, it listens to call state and captures audio through the microphone " +
                "with the speakerphone enabled to analyze the conversation.\n\n" +
                "All processing happens 100% on your device. No audio, text, or metadata leaves your phone. " +
                "You can disable detection at any time.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("I understand and accept")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ConsentScreenPreview() {
    CheckScamTheme {
        ConsentScreen(onAccept = {})
    }
}