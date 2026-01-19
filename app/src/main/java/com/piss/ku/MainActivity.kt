package com.piss.ku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.FirebaseApp
import com.piss.ku.ui.auth.LoginScreen
import com.piss.ku.ui.auth.SignupScreen
import com.piss.ku.ui.chat.ChatScreen
import com.piss.ku.ui.profile.ProfileScreen
import com.piss.ku.ui.theme.PissAppTheme
import com.piss.ku.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        
        setContent {
            PissAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PissApp()
                }
            }
        }
    }
}

@Composable
fun PissApp() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.authState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = if (authViewModel.isUserLoggedIn()) "chat" else "login"
    ) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = { navController.navigate("chat") },
                onSignupClick = { navController.navigate("signup") },
                viewModel = authViewModel
            )
        }
        
        composable("signup") {
            SignupScreen(
                onSignupSuccess = { navController.navigate("chat") },
                onLoginClick = { navController.navigate("login") },
                viewModel = authViewModel
            )
        }
        
        composable("chat") {
            ChatScreen(
                onLogout = {
                    authViewModel.signOut()
                    navController.navigate("login")
                },
                onProfileClick = { navController.navigate("profile") }
            )
        }
        
        composable("profile") {
            ProfileScreen(
                onBackClick = { navController.popBackStack() },
                viewModel = authViewModel
            )
        }
    }
}