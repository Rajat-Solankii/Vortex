package com.example.ui.screens.login

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.data.remote.SupabaseService
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.Google
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VortexDarkBackground)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = VortexTextPrimary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome Back",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Sign in to access your Watchlist.",
            color = VortexTextSecondary,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                scope.launch {
                    try {
                        SupabaseService.client.auth.signInWith(Google)
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "Google Sign-in failed", Toast.LENGTH_LONG).show()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
        ) {
            Text("Continue with Google", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = VortexDarkCardBorder)
            Text(
                text = "OR",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = VortexTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = VortexDarkCardBorder)
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email address", color = VortexTextMuted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VortexPurple,
                unfocusedBorderColor = VortexDarkCardBorder,
                focusedTextColor = VortexTextPrimary,
                unfocusedTextColor = VortexTextPrimary,
                cursorColor = VortexPurple
            ),
            shape = RoundedCornerShape(12.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password", color = VortexTextMuted) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VortexPurple,
                unfocusedBorderColor = VortexDarkCardBorder,
                focusedTextColor = VortexTextPrimary,
                unfocusedTextColor = VortexTextPrimary,
                cursorColor = VortexPurple
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isLoading = true
                scope.launch {
                    try {
                        val userEmail = email
                        val userPassword = password
                        SupabaseService.client.auth.signInWith(Email) {
                            this.email = userEmail
                            this.password = userPassword
                        }
                        onLoginSuccess()
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "Authentication failed", Toast.LENGTH_LONG).show()
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VortexPurple),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = VortexTextPrimary, modifier = Modifier.size(24.dp))
            } else {
                Text(
                    text = "Sign In",
                    color = VortexTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        TextButton(onClick = { /* Keep placeholder for sign up later */ }) {
            Text(
                text = "Don't have an account? Sign up",
                color = VortexPurpleLight
            )
        }
        }
    }
}
