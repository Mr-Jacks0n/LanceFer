package com.lancefer.lancedrop.ui.screen

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lancefer.lancedrop.ui.theme.*

data class ClipboardSnippet(
    val id: String,
    val text: String,
    val sender: String,
    val timeAgo: String,
    val isIncoming: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardScreen(
    onNavigateBack: () -> Unit,
    onSendTextToPc: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var inputText by remember { mutableStateOf("") }

    val snippets = remember {
        mutableStateListOf<ClipboardSnippet>()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Presse-papier partagé",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = LanceDropTextMainLight
                        )
                        Text(
                            text = "Synchronisation instantanée",
                            fontSize = 11.sp,
                            color = LanceDropGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = LanceDropTextMainLight
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LanceDropBgLight)
            )
        },
        containerColor = LanceDropBgLight
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ─────────────────────────────────────────────────────────────
            // 1. CARTE D'ENVOI DU TEXTE / COLLAGE
            // ─────────────────────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = LanceDropCardLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(LanceDropBlueLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = null,
                                        tint = LanceDropBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Envoyer vers l'ordinateur",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = LanceDropTextMainLight
                                    )
                                    Text(
                                        text = "Le texte apparaîtra directement sur le PC",
                                        fontSize = 11.sp,
                                        color = LanceDropTextMutedLight
                                    )
                                }
                            }

                            // Bouton "Coller" rapide
                            Button(
                                onClick = {
                                    val clip = clipboardManager.getText()
                                    if (clip != null && clip.text.isNotBlank()) {
                                        inputText = clip.text
                                        Toast.makeText(context, "Texte collé depuis le presse-papier !", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Presse-papier vide", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = LanceDropBlueLight),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = LanceDropBlue, modifier = Modifier.size(14.dp))
                                    Text("Coller", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LanceDropBlue)
                                }
                            }
                        }

                        // Zone de saisie
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp),
                            placeholder = {
                                Text(
                                    text = "Collez un lien, un texte, une adresse ou une note...",
                                    fontSize = 13.sp,
                                    color = LanceDropTextMutedLight
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = LanceDropBlue,
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                focusedContainerColor = Color(0xFFF8FAFC),
                                unfocusedContainerColor = Color(0xFFF8FAFC)
                            )
                        )

                        // Bas de carte : Compteur et Bouton Envoyer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${inputText.length} caractères",
                                fontSize = 11.sp,
                                color = LanceDropTextMutedLight
                            )

                            Button(
                                onClick = {
                                    if (inputText.isNotBlank()) {
                                        val newSnippet = ClipboardSnippet(
                                            id = System.currentTimeMillis().toString(),
                                            text = inputText,
                                            sender = android.os.Build.MODEL,
                                            timeAgo = "À l'instant",
                                            isIncoming = false
                                        )
                                        snippets.add(0, newSnippet)
                                        onSendTextToPc(inputText)
                                        Toast.makeText(context, "Texte envoyé au PC !", Toast.LENGTH_SHORT).show()
                                        inputText = ""
                                    } else {
                                        Toast.makeText(context, "Veuillez saisir ou coller un texte", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = LanceDropBlue),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Text("Envoyer au PC", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 2. TITRE SECTION RÉCEPTIONS
            // ─────────────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Textes récents & partagés",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = LanceDropTextMainLight
                    )
                    Text(
                        text = "${snippets.size} éléments",
                        fontSize = 12.sp,
                        color = LanceDropTextMutedLight
                    )
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 3. LISTE DES SNIPPETS OU ÉTAT VIDE
            // ─────────────────────────────────────────────────────────────
            if (snippets.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = LanceDropCardLight),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF1F5F9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.ContentPaste,
                                    contentDescription = null,
                                    tint = LanceDropTextMutedLight,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Aucun texte partagé",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = LanceDropTextMainLight
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Copiez du texte sur cet appareil ou envoyez-en un depuis votre ordinateur connecté pour le voir apparaître instantanément.",
                                fontSize = 12.sp,
                                color = LanceDropTextMutedLight,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            } else {
                items(snippets, key = { it.id }) { snippet ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = LanceDropCardLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (snippet.isIncoming) LanceDropBlueLight else Color(0xFFDCFCE7))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = snippet.sender,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (snippet.isIncoming) LanceDropBlue else LanceDropGreen
                                    )
                                }
                                Text(
                                    text = snippet.timeAgo,
                                    fontSize = 11.sp,
                                    color = LanceDropTextMutedLight
                                )
                            }

                            // Bouton Copier dans le presse-papier Android
                            FilledTonalButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(snippet.text))
                                    Toast.makeText(context, "✔ Copié dans le presse-papier !", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = LanceDropBlueLight,
                                    contentColor = LanceDropBlue
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Text("Copier", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Contenu du texte
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF8FAFC))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = snippet.text,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF1E293B),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
        }
    }
}
