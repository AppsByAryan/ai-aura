package com.example.aura.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.HearingDisabled
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aura.data.ActionStatus
import com.example.aura.data.AuraState
import com.example.aura.data.ChatMessage
import com.example.aura.data.MessageSender
import com.example.aura.ui.AuraViewModel
import com.example.aura.ui.components.ActionRequestCard
import com.example.aura.ui.components.AuraOrb
import com.example.aura.ui.components.EmergencyStopBanner
import com.example.aura.ui.components.GlassCard
import com.example.ui.theme.AuraBlack
import com.example.ui.theme.AuraBorderActive
import com.example.ui.theme.AuraBorderGlow
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraDarkSurface
import com.example.ui.theme.AuraElectricViolet
import com.example.ui.theme.AuraError
import com.example.ui.theme.AuraSurfaceElevated
import com.example.ui.theme.AuraTextMuted
import com.example.ui.theme.AuraTextPrimary
import com.example.ui.theme.AuraTextSecondary
import com.example.ui.theme.AuraWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AuraViewModel,
    onNavigateSecurity: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val auraState by viewModel.auraState.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val pendingAction by viewModel.pendingAction.collectAsStateWithLifecycle()
    val isVoiceOutputEnabled by viewModel.isVoiceOutputEnabled.collectAsStateWithLifecycle()
    val isWakeWordEnabled by viewModel.isWakeWordEnabled.collectAsStateWithLifecycle()
    val wakeWordPhrase by viewModel.wakeWordPhrase.collectAsStateWithLifecycle()
    val isWakeWordListening by viewModel.isWakeWordListening.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var showClearChatConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Scroll to bottom when new messages arrive
    LaunchedEffect(chatMessages.size, pendingAction) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceListening()
        }
    }

    fun handleMicClick() {
        if (auraState == AuraState.LISTENING) {
            viewModel.stopVoiceListening()
        } else {
            val permissionCheck = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            )
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                viewModel.startVoiceListening()
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val quickPrompts = listOf(
        "Open YouTube",
        "What's my battery level?",
        "Tell me how much storage I have",
        "Open Settings",
        "Set the volume to 50%",
        "Play music",
        "Pause the music",
        "Open VS Code on my laptop",
        "Take a screenshot",
        "Show my connected devices",
        "Open my Physics website",
        "Open my Downloads"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AuraBlack,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "AURA",
                            color = AuraCyan,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 3.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AuraCyan.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, AuraCyan.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "REMOTE ASSISTANT",
                                color = AuraCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    // Wake Word Status Pill / Quick Toggle
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isWakeWordEnabled) AuraCyan.copy(alpha = 0.15f) else Color(0x33000000),
                        border = BorderStroke(1.dp, if (isWakeWordEnabled) AuraCyan else AuraBorderGlow),
                        modifier = Modifier
                            .clickable {
                                val permissionCheck = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                )
                                if (!isWakeWordEnabled && permissionCheck != PackageManager.PERMISSION_GRANTED) {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    viewModel.toggleWakeWord()
                                }
                            }
                            .testTag("wake_word_quick_toggle_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isWakeWordEnabled) Icons.Default.Hearing else Icons.Default.HearingDisabled,
                                contentDescription = "Wake Word Status",
                                tint = if (isWakeWordEnabled) AuraCyan else AuraTextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isWakeWordEnabled) "\"$wakeWordPhrase\"" else "WAKE: OFF",
                                color = if (isWakeWordEnabled) AuraCyan else AuraTextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Clear Chat History Button
                    IconButton(
                        onClick = { showClearChatConfirm = true },
                        modifier = Modifier.testTag("clear_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Chat History",
                            tint = AuraTextSecondary
                        )
                    }

                    // Voice speech toggle
                    IconButton(
                        onClick = { viewModel.toggleVoiceOutput() },
                        modifier = Modifier.testTag("toggle_voice_output_button")
                    ) {
                        Icon(
                            imageVector = if (isVoiceOutputEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                            contentDescription = if (isVoiceOutputEnabled) "Mute Voice" else "Unmute Voice",
                            tint = if (isVoiceOutputEnabled) AuraCyan else AuraTextMuted
                        )
                    }

                    // Emergency Stop top icon
                    IconButton(
                        onClick = { viewModel.emergencyStop("Emergency stop initiated from top bar.") },
                        modifier = Modifier.testTag("top_emergency_stop_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.StopCircle,
                            contentDescription = "Emergency Stop",
                            tint = AuraError
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AuraDarkSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            // Emergency Stop Banner
            EmergencyStopBanner(
                isVisible = pendingAction != null || auraState == AuraState.EXECUTING,
                onStopClick = { viewModel.emergencyStop("User triggered STOP AURA control.") }
            )

            // Prompt banner if not yet set as default digital assistant
            if (!viewModel.isDefaultAssistant()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { viewModel.openDefaultAssistantSettings() }
                        .testTag("set_default_assistant_banner"),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x3300E5FF),
                    border = BorderStroke(1.dp, AuraCyan.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Assistant,
                                contentDescription = null,
                                tint = AuraCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Set AURA as Default Assistant",
                                color = AuraCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = "SET NOW →",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Upper Deck: Animated AURA Orb
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                AuraOrb(
                    state = auraState,
                    onClick = { handleMicClick() }
                )
            }

            // Quick Command Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickPrompts.forEach { prompt ->
                    Surface(
                        modifier = Modifier
                            .clickable {
                                viewModel.submitCommand(prompt)
                            }
                            .testTag("quick_prompt_${prompt.replace(" ", "_").lowercase()}"),
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0x3300E5FF),
                        border = BorderStroke(1.dp, AuraBorderGlow)
                    ) {
                        Text(
                            text = prompt,
                            color = AuraTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Conversation History (Persistent Room Database)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("chat_messages_list"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item {
                    // Chat History Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = AuraTextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CHAT HISTORY PERSISTED LOCALLY IN ROOM DATABASE",
                            color = AuraTextMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                }

                items(chatMessages, key = { it.id }) { msg ->
                    ChatMessageItem(
                        message = msg,
                        onAllow = { plan -> viewModel.confirmAction(plan) },
                        onCancel = { plan -> viewModel.cancelAction(plan) }
                    )
                }
            }

            // Bottom Input Controls: Microphone and Text Field
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = AuraDarkSurface,
                border = BorderStroke(1.dp, AuraBorderGlow)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Microphone Button
                    Surface(
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("mic_button")
                            .clickable { handleMicClick() },
                        shape = CircleShape,
                        color = if (auraState == AuraState.LISTENING) AuraCyan else Color(0x3300E5FF),
                        border = BorderStroke(
                            1.5.dp,
                            if (auraState == AuraState.LISTENING) Color.White else AuraCyan
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (auraState == AuraState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Voice Input",
                                tint = if (auraState == AuraState.LISTENING) Color.Black else AuraCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Text Field
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("command_input_field"),
                        placeholder = {
                            Text(
                                text = "Ask AURA...",
                                color = AuraTextMuted,
                                fontSize = 14.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AuraCyan,
                            unfocusedBorderColor = AuraBorderGlow,
                            focusedTextColor = AuraTextPrimary,
                            unfocusedTextColor = AuraTextPrimary,
                            cursorColor = AuraCyan
                        ),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Send Button
                    Surface(
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("send_command_button")
                            .clickable {
                                if (inputText.isNotBlank()) {
                                    val cmd = inputText
                                    inputText = ""
                                    viewModel.submitCommand(cmd)
                                }
                            },
                        shape = CircleShape,
                        color = if (inputText.isNotBlank()) AuraCyan else Color(0x2200E5FF),
                        border = BorderStroke(1.dp, if (inputText.isNotBlank()) AuraCyan else AuraBorderGlow)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (inputText.isNotBlank()) Color.Black else AuraTextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Clear Chat Confirmation Dialog
        if (showClearChatConfirm) {
            AlertDialog(
                onDismissRequest = { showClearChatConfirm = false },
                title = {
                    Text(
                        text = "CLEAR CHAT INTERACTIONS?",
                        color = AuraCyan,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                },
                text = {
                    Text(
                        text = "This will clear all past chat messages stored locally in your Room database. Action audit history in the Audit Log will remain intact.",
                        color = AuraTextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearChatHistory()
                            showClearChatConfirm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AuraError)
                    ) {
                        Text("CLEAR", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showClearChatConfirm = false }) {
                        Text("CANCEL", color = AuraTextSecondary)
                    }
                },
                containerColor = AuraDarkSurface
            )
        }
    }
}

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    onAllow: (com.example.aura.data.AuraActionPlan) -> Unit,
    onCancel: (com.example.aura.data.AuraActionPlan) -> Unit
) {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(message.timestamp))

    when (message.sender) {
        MessageSender.USER -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        shape = RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp),
                        color = Color(0xFF132A4A),
                        border = BorderStroke(1.dp, AuraBorderGlow)
                    ) {
                        Text(
                            text = message.text,
                            color = AuraTextPrimary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = formattedTime,
                        color = AuraTextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
        }

        MessageSender.SYSTEM -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0x33000000),
                border = BorderStroke(1.dp, Color(0x22FFFFFF))
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.text,
                        color = AuraTextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formattedTime,
                        color = AuraTextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        MessageSender.AURA -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp),
                    color = Color(0xE60A1326),
                    border = BorderStroke(1.dp, Color(0xFF18325B))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = AuraCyan,
                                    modifier = Modifier.size(6.dp)
                                ) {}
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AURA",
                                    color = AuraCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                            }
                            Text(
                                text = formattedTime,
                                color = AuraTextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = message.text,
                            color = AuraTextPrimary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }

                // If this message contains a pending or historical action card
                if (message.actionPlan != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (message.actionStatus == ActionStatus.PENDING) {
                        ActionRequestCard(
                            plan = message.actionPlan,
                            onAllow = { onAllow(message.actionPlan) },
                            onCancel = { onCancel(message.actionPlan) }
                        )
                    } else {
                        // Historical completed status banner
                        val statusColor = when (message.actionStatus) {
                            ActionStatus.APPROVED, ActionStatus.SUCCESS -> AuraCyan
                            ActionStatus.CANCELLED -> AuraWarning
                            ActionStatus.FAILED, ActionStatus.DENIED -> AuraError
                            else -> AuraTextMuted
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = statusColor.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "Action ${message.actionPlan.description} [${message.actionStatus?.name}]",
                                color = statusColor,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
