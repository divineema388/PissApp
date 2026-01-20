package com.piss.ku.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class User(
    val uid: String = "",
    val email: String = "",
    val username: String = "",
    val profileImageUrl: String = ""
)

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState
    
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser
    
    init {
        checkCurrentUser()
    }
    
    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }
    
    private fun checkCurrentUser() {
        val firebaseUser = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                val userDoc = db.collection("users").document(firebaseUser.uid).get().await()
                val user = if (userDoc.exists()) {
                    userDoc.toObject(User::class.java) ?: User(
                        uid = firebaseUser.uid,
                        email = firebaseUser.email ?: "",
                        username = firebaseUser.displayName ?: "",
                        profileImageUrl = firebaseUser.photoUrl?.toString() ?: ""
                    )
                } else {
                    User(
                        uid = firebaseUser.uid,
                        email = firebaseUser.email ?: "",
                        username = firebaseUser.displayName ?: "",
                        profileImageUrl = firebaseUser.photoUrl?.toString() ?: ""
                    )
                }
                _currentUser.value = user
                _authState.value = AuthState.Success(user)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Error fetching user data")
            }
        }
    }
    
    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                checkCurrentUser()
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Sign in failed")
            }
        }
    }
    
    fun signUp(email: String, password: String, username: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user ?: throw Exception("User creation failed")
                
                // Update profile
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(username)
                    .build()
                user.updateProfile(profileUpdates).await()
                
                // Save user to Firestore
                val newUser = User(
                    uid = user.uid,
                    email = email,
                    username = username
                )
                db.collection("users").document(user.uid).set(newUser).await()
                
                _currentUser.value = newUser
                _authState.value = AuthState.Success(newUser)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Sign up failed")
            }
        }
    }
    
    fun signOut() {
        auth.signOut()
        _currentUser.value = null
        _authState.value = AuthState.Idle
    }
    
    suspend fun updateProfile(username: String, profileImageUri: Uri? = null): Boolean {
        return try {
            val user = auth.currentUser ?: return false
            var profileImageUrl = _currentUser.value?.profileImageUrl ?: ""
            
            // Upload image if provided
            profileImageUri?.let { uri ->
                val storageRef = storage.reference
                val imageRef = storageRef.child("profile_images/${user.uid}/${UUID.randomUUID()}")
                val uploadTask = imageRef.putFile(uri).await()
                val downloadUrl = uploadTask.metadata?.reference?.downloadUrl?.await()
                profileImageUrl = downloadUrl?.toString() ?: ""
            }
            
            // Update Firebase Auth profile
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(username)
                .apply {
                    if (profileImageUrl.isNotEmpty()) {
                        setPhotoUri(Uri.parse(profileImageUrl))
                    }
                }
                .build()
            user.updateProfile(profileUpdates).await()
            
            // Update Firestore
            val updatedUser = User(
                uid = user.uid,
                email = user.email ?: "",
                username = username,
                profileImageUrl = profileImageUrl
            )
            db.collection("users").document(user.uid).set(updatedUser).await()
            
            _currentUser.value = updatedUser
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}