package com.checkscam.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.checkscam.app.CheckScamApplication
import com.checkscam.app.data.SettingsRepository
import com.checkscam.app.ui.theme.CheckScamTheme
import com.checkscam.calldetection.PermissionHelper

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
    val settings = remember { SettingsRepository(context) }
    val detectionService = remember { app.callDetectionService }
    val audioCoordinator = remember { app.callAudioCoordinator }

    var settingsLoaded by remember { mutableStateOf(false) }
    var consentAccepted by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var permissionsGranted by remember {
        mutableStateOf(PermissionHelper.areAllPermissionsGranted(context))
    }

    LaunchedEffect(Unit) {
        consentAccepted = settings.isDetectionEnabled()
        settingsLoaded = true
        permissionsGranted = PermissionHelper.areAllPermissionsGranted(context)
        if (consentAccepted && permissionsGranted) {
            detectionService.start()
            audioCoordinator.start()
            running = true
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
                    running = true
                }
            }
        )
        return
    }

    DetectionActiveScreen(
        modifier = modifier,
        running = running,
        onDisable = {
            audioCoordinator.stop()
            detectionService.stop()
            running = false
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

@Composable
fun DetectionActiveScreen(
    modifier: Modifier = Modifier,
    running: Boolean,
    onDisable: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Scam Detection",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (running) "Detection is active" else "Detection is ready",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedButton(onClick = onDisable) {
                Text("Disable detection")
            }
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