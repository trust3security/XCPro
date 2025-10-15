package com.example.ui1.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseAuth

@Composable
fun ChangePasswordScreen(navController: NavHostController) {
    val auth = FirebaseAuth.getInstance()
    var newPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = { Text("New Password") },
            visualTransformation = PasswordVisualTransformation()
        )
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = {
                loading = true
                auth.currentUser?.updatePassword(newPassword)?.addOnCompleteListener { task ->
                    loading = false
                    if (task.isSuccessful) {
                        navController.popBackStack()
                    } else {
                        error = task.exception?.message
                    }
                }
            },
            enabled = !loading && newPassword.length >= 6
        ) {
            Text("Update Password")
        }
    }
}