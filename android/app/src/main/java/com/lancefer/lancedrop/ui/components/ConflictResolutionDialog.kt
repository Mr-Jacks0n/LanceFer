package com.lancefer.lancedrop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lancefer.lancedrop.ui.theme.LanceDropBlue
import com.lancefer.lancedrop.ui.theme.LanceDropCardLight
import com.lancefer.lancedrop.ui.theme.LanceDropTextMainLight
import com.lancefer.lancedrop.ui.theme.LanceDropTextMutedLight

@Composable
fun ConflictResolutionDialog(
    fileName: String,
    onResolve: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LanceDropCardLight),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Doublon détecté",
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(44.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Fichier déjà existant",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = LanceDropTextMainLight
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Un fichier nommé \"$fileName\" existe déjà dans votre dossier de réception.",
                    fontSize = 13.sp,
                    color = LanceDropTextMutedLight,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Bouton 1 : Conserver les deux (Renommer)
                Button(
                    onClick = { onResolve("RENAME") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = LanceDropBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Conserver les deux (renommer)", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bouton 2 : Écraser
                OutlinedButton(
                    onClick = { onResolve("OVERWRITE") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Écraser le fichier", color = LanceDropTextMainLight)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bouton 3 : Ignorer
                TextButton(
                    onClick = { onResolve("SKIP") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ignorer ce fichier", color = LanceDropTextMutedLight)
                }
            }
        }
    }
}
