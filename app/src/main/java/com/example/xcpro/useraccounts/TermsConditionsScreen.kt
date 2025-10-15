package com.example.ui1.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@Composable
fun TermsConditionsScreen(navController: NavHostController) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Terms & Conditions", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Your terms and conditions text here...",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://yourcompany.com/terms"))
            context.startActivity(intent)
        }) {
            Text("View Full Terms")
        }
    }
}