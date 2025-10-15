package com.example.ui1.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

@Composable
fun EditProfileScreen(navController: NavHostController) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    val firestore = FirebaseFirestore.getInstance()
    var name by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(user) {
        user?.uid?.let { uid ->
            val document = firestore.collection("users").document(uid).get().await()
            name = document.getString("name") ?: ""
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") }
        )
        Button(
            onClick = {
                loading = true
                user?.uid?.let { uid ->
                    firestore.collection("users").document(uid)
                        .set(mapOf("name" to name, "email" to user.email), SetOptions.merge())
                        .addOnSuccessListener { navController.popBackStack() }
                        .addOnFailureListener { /* Handle error */ }
                        .addOnCompleteListener { loading = false }
                }
            },
            enabled = !loading
        ) {
            Text("Save")
        }
    }
}