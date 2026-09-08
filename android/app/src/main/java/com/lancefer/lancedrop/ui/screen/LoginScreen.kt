package com.lancefer.lancedrop.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lancefer.lancedrop.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("john.d@gmail.com") }
    var password by remember { mutableStateOf("••••••••••••") }
    var rememberMe by remember { mutableStateOf(true) }
    var passwordVisible by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Haut : Logo & Titres
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                // Logo Nuage
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(LanceDropBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Logo",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "LanceDrop",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = LanceDropTextMainLight
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Connectez-vous à votre compte",
                    fontSize = 14.sp,
                    color = LanceDropTextMutedLight
                )

                Spacer(modifier = Modifier.height(36.dp))

                // Champ Identifiant / Email
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Adresse e-mail ou numéro de téléphone") },
                    leadingIcon = {
                        Icon(Icons.Default.PersonOutline, contentDescription = null, tint = LanceDropTextMutedLight)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LanceDropBlue,
                        unfocusedBorderColor = LanceDropCardBorderLight
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Champ Mot de Passe
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Mot de passe") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = LanceDropTextMutedLight)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = LanceDropTextMutedLight
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LanceDropBlue,
                        unfocusedBorderColor = LanceDropCardBorderLight
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Checkbox Se souvenir de moi & Mot de passe oublié
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = { rememberMe = it },
                            colors = CheckboxDefaults.colors(checkedColor = LanceDropBlue)
                        )
                        Text(
                            text = "Se souvenir de moi",
                            fontSize = 12.sp,
                            color = LanceDropTextMutedLight
                        )
                    }

                    TextButton(onClick = {}) {
                        Text(
                            text = "Mot de passe oublié ?",
                            fontSize = 12.sp,
                            color = LanceDropBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bouton Se Connecter
                Button(
                    onClick = onLoginSuccess,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LanceDropBlue)
                ) {
                    Text(
                        text = "Se connecter",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Séparateur ou
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = LanceDropCardBorderLight)
                    Text(
                        text = "ou",
                        modifier = Modifier.padding(horizontal = 14.dp),
                        fontSize = 12.sp,
                        color = LanceDropTextMutedLight
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = LanceDropCardBorderLight)
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Boutons Sociaux (Google / Microsoft)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onLoginSuccess,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, LanceDropCardBorderLight)
                    ) {
                        Text("Google", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LanceDropTextMainLight)
                    }

                    OutlinedButton(
                        onClick = onLoginSuccess,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, LanceDropCardBorderLight)
                    ) {
                        Text("Microsoft", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LanceDropTextMainLight)
                    }
                }
            }

            // Bas : Inscription
            Row(
                modifier = Modifier.padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Vous n'avez pas de compte ? ",
                    fontSize = 13.sp,
                    color = LanceDropTextMutedLight
                )
                Text(
                    text = "S'inscrire",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = LanceDropBlue
                )
            }
        }
    }
}
