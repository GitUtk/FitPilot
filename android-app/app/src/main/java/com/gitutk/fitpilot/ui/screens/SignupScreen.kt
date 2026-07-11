package com.gitutk.fitpilot.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitutk.fitpilot.R
import com.gitutk.fitpilot.ui.FitPilotViewModel
import com.gitutk.fitpilot.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    viewModel: FitPilotViewModel,
    onSignupSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = onNavigateBack) {
                    Text("←", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Slate900)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Logo & Title
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "FitPilot Logo",
                modifier = Modifier
                    .size(70.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Create Account",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Slate900
            )
            
            Text(
                text = "Sign up to start tracking workouts and meals",
                fontSize = 13.sp,
                color = Slate500,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Inputs
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it; validationError = null },
                label = { Text("Full Name", color = Slate500) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Slate900,
                    unfocusedBorderColor = Slate200,
                    focusedLabelColor = Slate900,
                    cursorColor = Slate900
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; validationError = null },
                label = { Text("Email Address", color = Slate500) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Slate900,
                    unfocusedBorderColor = Slate200,
                    focusedLabelColor = Slate900,
                    cursorColor = Slate900
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; validationError = null },
                label = { Text("Password", color = Slate500) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Slate900,
                    unfocusedBorderColor = Slate200,
                    focusedLabelColor = Slate900,
                    cursorColor = Slate900
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; validationError = null },
                label = { Text("Confirm Password", color = Slate500) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Slate900,
                    unfocusedBorderColor = Slate200,
                    focusedLabelColor = Slate900,
                    cursorColor = Slate900
                )
            )

            // Error Display
            val errorToDisplay = validationError ?: viewModel.authError
            if (errorToDisplay != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorToDisplay,
                    color = Red500,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Submit Button
            Button(
                onClick = {
                    if (email.trim().isEmpty() || password.trim().isEmpty() || fullName.trim().isEmpty()) {
                        validationError = "Please fill in all fields"
                        return@Button
                    }
                    if (password != confirmPassword) {
                        validationError = "Passwords do not match"
                        return@Button
                    }
                    viewModel.signup(email.trim(), password, fullName.trim()) {
                        onSignupSuccess()
                    }
                },
                enabled = !viewModel.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Slate900,
                    contentColor = Color.White
                )
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(size = 20.dp, color = Color.White)
                } else {
                    Text(
                        text = "Create Account",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Login Prompt
            Row(
                modifier = Modifier.padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(text = "Already have an account? ", color = Slate500, fontSize = 13.sp)
                Text(
                    text = "Sign In",
                    color = Slate900,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNavigateToLogin() }
                )
            }
        }
    }
}
