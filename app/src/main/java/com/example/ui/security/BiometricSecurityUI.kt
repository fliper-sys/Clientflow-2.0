package com.example.ui.security

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.security.BiometricStatus
import com.example.ui.ClientFlowViewModel
import com.example.ui.theme.SageGreen

@Composable
fun BiometricSettingsCard(
    viewModel: ClientFlowViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val settings by viewModel.settingsState.collectAsState()

    val biometricStatus = remember { viewModel.biometricAuthManager.checkBiometricStatus() }
    var statusMessage by remember { mutableStateOf("") }

    val isMasterBiometricOn = settings?.biometricLockEnabled ?: false
    val isJournalLockedOn = settings?.journalBiometricLocked ?: false
    val isClientDataLockedOn = settings?.clientDataBiometricLocked ?: false

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SageGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Fingerprint,
                        contentDescription = "Biometric Protection",
                        tint = SageGreen,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Biometric Security",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (biometricStatus) {
                            BiometricStatus.AVAILABLE -> "Fingerprint / Face ID Hardware Ready"
                            BiometricStatus.NOT_ENROLLED -> "Biometrics Available (Enroll in Android Settings)"
                            BiometricStatus.NO_HARDWARE -> "No Biometric Hardware detected (PIN fallback active)"
                            else -> "Hardware status: ${biometricStatus.name.lowercase().replace("_", " ")}"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Master Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable Biometric Authentication",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Require fingerprint/face unlock to access protected sections",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isMasterBiometricOn,
                    onCheckedChange = { enabled ->
                        viewModel.toggleBiometricMasterLock(enabled)
                        if (enabled && activity != null) {
                            viewModel.authenticateWithBiometrics(
                                activity = activity,
                                title = "Confirm Biometric Setup",
                                subtitle = "Authenticate to enable biometric protection",
                                onSuccess = {
                                    Toast.makeText(context, "Biometric security enabled", Toast.LENGTH_SHORT).show()
                                },
                                onError = { err: String ->
                                    statusMessage = err
                                }
                            )
                        }
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SageGreen)
                )
            }

            AnimatedVisibility(visible = isMasterBiometricOn) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Lock Journal Entries Option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Secure Personal Journal Entries",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Require biometric unlock when opening private reflections",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isJournalLockedOn,
                            onCheckedChange = { viewModel.toggleJournalBiometricLock(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SageGreen)
                        )
                    }

                    // Lock Client Records Option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Secure Client Records & Caseload",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Protect confidential practitioner records with biometrics",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isClientDataLockedOn,
                            onCheckedChange = { viewModel.toggleClientDataBiometricLock(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SageGreen)
                        )
                    }

                    // Test Biometric Authentication Prompt Button
                    OutlinedButton(
                        onClick = {
                            if (activity != null) {
                                viewModel.authenticateWithBiometrics(
                                    activity = activity,
                                    title = "Test Biometric Prompt",
                                    subtitle = "Authentication verification test",
                                    onSuccess = {
                                        Toast.makeText(context, "Biometric verification successful!", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { err: String ->
                                        statusMessage = "Test result: $err"
                                    }
                                )
                            } else {
                                Toast.makeText(context, "Biometric test initialized", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Biometric Prompt Live", fontSize = 13.sp)
                    }
                }
            }

            if (statusMessage.isNotBlank()) {
                Text(
                    text = statusMessage,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun BiometricLockOverlay(
    title: String,
    description: String,
    onUnlockBiometric: (FragmentActivity, () -> Unit, (String) -> Unit) -> Unit,
    onUnlockPin: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showPinDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(SageGreen.copy(alpha = 0.15f))
                    .border(2.dp, SageGreen.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = "Secured Content",
                    tint = SageGreen,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (activity != null) {
                        onUnlockBiometric(
                            activity,
                            {
                                errorMessage = null
                            },
                            { err ->
                                errorMessage = err
                            }
                        )
                    } else {
                        // Fallback trigger
                        onUnlockPin("")
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SageGreen),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(52.dp)
            ) {
                Icon(Icons.Rounded.Fingerprint, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Unlock with Biometrics", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = { showPinDialog = true },
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Use Security Passcode / PIN", fontSize = 13.sp)
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = {
                Text("Enter Passcode", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text("Enter your ClientFlow passcode or emergency PIN to unlock.", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it },
                        label = { Text("Security PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val success = onUnlockPin(pinInput)
                        if (success) {
                            showPinDialog = false
                        } else {
                            Toast.makeText(context, "Invalid Passcode", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SageGreen)
                ) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
