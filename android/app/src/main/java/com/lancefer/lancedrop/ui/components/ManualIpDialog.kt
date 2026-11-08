package com.lancefer.lancedrop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lancefer.lancedrop.ui.theme.LanceDropBlue

@Composable
fun ManualIpDialog(
    onDismiss: () -> Unit,
    onConfirm: (ip: String, port: Int) -> Unit
) {
    var ipInput by remember { mutableStateOf("") }
    var portInput by remember { mutableStateOf("42420") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Computer, contentDescription = null, tint = LanceDropBlue, modifier = Modifier.size(36.dp))
        },
        title = {
            Text(
                text = "Connexion Directe par IP",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Saisissez l'adresse IPv4 locale du PC distant affichée sur l'application LanceDrop Desktop.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = ipInput,
                    onValueChange = {
                        ipInput = it.trim()
                        errorMessage = null
                    },
                    label = { Text("Adresse IP (ex: 192.168.1.45)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = portInput,
                    onValueChange = { portInput = it.trim() },
                    label = { Text("Port TCP (défaut : 42420)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (ipInput.isBlank() || !ipInput.contains('.')) {
                        errorMessage = "Veuillez entrer une adresse IPv4 valide"
                        return@Button
                    }
                    val port = portInput.toIntOrNull() ?: 42420
                    onConfirm(ipInput, port)
                },
                colors = ButtonDefaults.buttonColors(containerColor = LanceDropBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Connecter", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Annuler")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
