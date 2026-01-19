package com.piss.ku.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

data class Message(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val timestamp: Date = Date(),
    val isCurrentUser: Boolean = false
)

class ChatViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = Firebase.firestore
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    init {
        loadMessages()
        listenForMessages()
    }
    
    private fun loadMessages() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val querySnapshot = db.collection("messages")
                    .orderBy("timestamp", Query.Direction.ASCENDING)
                    .get()
                    .await()
                
                val messagesList = querySnapshot.documents.mapNotNull { document ->
                    document.toMessage()
                }
                _messages.value = messagesList
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private fun listenForMessages() {
        db.collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                
                snapshot?.let { querySnapshot ->
                    val messagesList = querySnapshot.documents.mapNotNull { document ->
                        document.toMessage()
                    }
                    _messages.value = messagesList
                }
            }
    }
    
    fun sendMessage(text: String) {
        val currentUser = auth.currentUser ?: return
        val message = hashMapOf(
            "text" to text,
            "senderId" to currentUser.uid,
            "senderName" to currentUser.displayName ?: "Anonymous",
            "timestamp" to Timestamp.now()
        )
        
        viewModelScope.launch {
            try {
                db.collection("messages").add(message).await()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    private fun com.google.firebase.firestore.DocumentSnapshot.toMessage(): Message {
        val currentUserId = auth.currentUser?.uid ?: ""
        return Message(
            id = id,
            text = getString("text") ?: "",
            senderId = getString("senderId") ?: "",
            senderName = getString("senderName") ?: "",
            timestamp = getTimestamp("timestamp")?.toDate() ?: Date(),
            isCurrentUser = getString("senderId") == currentUserId
        )
    }
}