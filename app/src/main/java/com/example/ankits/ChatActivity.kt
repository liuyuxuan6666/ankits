package com.example.ankits

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ankits.databinding.ActivityChatBinding
import java.io.ByteArrayOutputStream

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: ChatMessageAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var currentSession: ChatSession? = null
    private var apiClient: ChatApiClient? = null
    private var streamingMessage: ChatMessage? = null
    private val streamingContent = StringBuilder()
    private var pendingImageBase64: String? = null

    companion object {
        private const val EXTRA_SESSION_ID = "session_id"
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> handleImageSelected(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = maxOf(navBar.bottom, ime.bottom)
            binding.rootLayout.setPadding(0, statusBar.top, 0, bottomInset)
            binding.bottomInputBar.setPadding(
                binding.bottomInputBar.paddingLeft,
                binding.bottomInputBar.paddingTop,
                binding.bottomInputBar.paddingRight,
                if (ime.bottom > 0) 0 else navBar.bottom
            )
            WindowInsetsCompat.CONSUMED
        }

        adapter = ChatMessageAdapter()
        binding.chatMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.chatMessages.adapter = adapter

        binding.sendBtn.setOnClickListener { sendMessage() }
        binding.backBtn.setOnClickListener { finish() }
        binding.menuBtn.setOnClickListener { showOverflowMenu() }
        binding.attachBtn.setOnClickListener { showImagePicker() }
        binding.removeImageBtn.setOnClickListener { removeImage() }

        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        Thread {
            val session = if (sessionId != null) {
                ChatStorage.loadSession(this, sessionId) ?: ChatStorage.createSession(this)
            } else {
                ChatStorage.createSession(this)
            }
            handler.post { onSessionLoaded(session) }
        }.start()
    }

    private fun onSessionLoaded(session: ChatSession) {
        currentSession = session
        binding.toolbarTitle.text = session.title
        adapter.setMessages(session.messages.toMutableList())
        scrollToBottom()
        initApiClient()
    }

    private fun initApiClient() {
        val prefs = getSharedPreferences("ankits_settings", MODE_PRIVATE)
        val endpoint = prefs.getString("chat_endpoint", "https://api.openai.com") ?: "https://api.openai.com"
        val apiKey = prefs.getString("chat_api_key", "") ?: ""
        val model = currentSession?.model?.takeIf { it.isNotEmpty() }
            ?: prefs.getString("chat_model", "gpt-4o-mini") ?: "gpt-4o-mini"
        val streaming = prefs.getBoolean("chat_streaming", true)

        apiClient = ChatApiClient(endpoint, apiKey, model, streaming, handler).apply {
            onToken = { token ->
                streamingContent.append(token)
                streamingMessage?.let { msg ->
                    val updatedMsg = msg.copy(content = streamingContent.toString())
                    streamingMessage = updatedMsg
                    handler.post { adapter.updateLastMessage(updatedMsg) }
                }
            }
            onComplete = { fullContent ->
                streamingMessage?.let { msg ->
                    val completedMsg = msg.copy(content = fullContent)
                    currentSession?.messages?.add(completedMsg)
                    streamingMessage = null
                    streamingContent.clear()
                    currentSession?.let { session ->
                        session.updatedAt = System.currentTimeMillis()
                        Thread { ChatStorage.saveSession(this@ChatActivity, session) }.start()
                    }
                    handler.post {
                        adapter.updateLastMessage(completedMsg)
                        adapter.clearTypingIndicator()
                        scrollToBottom()
                    }
                }
            }
            onError = { errorMsg ->
                streamingMessage?.let { msg ->
                    val errorMsgObj = msg.copy(
                        content = "Error: $errorMsg",
                        role = "assistant"
                    )
                    currentSession?.messages?.add(errorMsgObj)
                    streamingMessage = null
                    streamingContent.clear()
                    handler.post {
                        adapter.updateLastMessage(errorMsgObj)
                        adapter.clearTypingIndicator()
                        scrollToBottom()
                    }
                }
            }
        }
    }

    private fun sendMessage() {
        val text = binding.inputEditText.text.toString().trim()
        if (text.isEmpty() && pendingImageBase64 == null) return
        if (apiClient == null) {
            Toast.makeText(this, "Please configure API first", Toast.LENGTH_SHORT).show()
            return
        }
        if (streamingMessage != null) return

        val userMsg = ChatMessage(
            role = "user",
            content = text,
            imageBase64 = pendingImageBase64
        )

        currentSession?.messages?.add(userMsg)
        adapter.appendMessage(userMsg)

        if (currentSession?.messages?.size == 1 && text.isNotEmpty()) {
            val title = text.take(40).substringBefore('\n')
            currentSession?.title = title
            binding.toolbarTitle.text = title
        }

        binding.inputEditText.text.clear()
        pendingImageBase64 = null
        updateImagePreview()

        val session = currentSession ?: return
        val maxTokens = session.maxContextTokens
        val prefs = getSharedPreferences("ankits_settings", MODE_PRIVATE)
        val systemPrompt = session.systemPrompt.ifEmpty {
            prefs.getString("chat_system_prompt", "You are a helpful assistant.") ?: ""
        }

        val messages = buildContextMessages(session.messages, systemPrompt, maxTokens)

        adapter.showTypingIndicator()

        streamingContent.clear()
        streamingMessage = ChatMessage(
            id = java.util.UUID.randomUUID().toString(),
            role = "assistant",
            content = "",
            timestamp = System.currentTimeMillis()
        )
        adapter.appendMessage(streamingMessage!!)

        apiClient?.sendMessage(messages, systemPrompt)
        scrollToBottom()
    }

    private fun buildContextMessages(
        allMessages: List<ChatMessage>,
        systemPrompt: String,
        maxTokens: Int
    ): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()
        var totalTokens = TokenEstimator.estimate(systemPrompt)

        // Walk backwards, exclude the last streaming message placeholder
        val eligible = allMessages.filter { it.role != "assistant" || it.content.isNotEmpty() }

        for (i in eligible.indices.reversed()) {
            val msg = eligible[i]
            val tokens = TokenEstimator.estimate(msg)
            if (totalTokens + tokens > maxTokens) break
            result.add(0, msg)
            totalTokens += tokens
        }

        return result
    }

    private fun scrollToBottom() {
        binding.chatMessages.post {
            val adapter = binding.chatMessages.adapter
            if (adapter != null && adapter.itemCount > 0) {
                binding.chatMessages.smoothScrollToPosition(adapter.itemCount - 1)
            }
        }
    }

    private fun showImagePicker() {
        val options = arrayOf(getString(R.string.chat_take_photo), getString(R.string.chat_choose_gallery))
        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val intent = android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                        if (intent.resolveActivity(packageManager) != null) {
                            // Use simple pick approach since camera intent with FileProvider adds complexity
                            imagePickerLauncher.launch("image/*")
                        } else {
                            imagePickerLauncher.launch("image/*")
                        }
                    }
                    1 -> imagePickerLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun handleImageSelected(uri: Uri?) {
        if (uri == null) return
        Thread {
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                val maxDim = 1024f
                val scale = minOf(maxDim / bitmap.width, maxDim / bitmap.height, 1f)
                val resized = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
                val baos = ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                handler.post {
                    pendingImageBase64 = base64
                    updateImagePreview()

                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    val thumb = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    binding.attachedImage.setImageBitmap(thumb)
                }
            } catch (e: Exception) {
                handler.post {
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun updateImagePreview() {
        if (pendingImageBase64 != null) {
            binding.imagePreviewBar.visibility = View.VISIBLE
        } else {
            binding.imagePreviewBar.visibility = View.GONE
        }
    }

    private fun removeImage() {
        pendingImageBase64 = null
        updateImagePreview()
    }

    private fun showOverflowMenu() {
        val popup = PopupMenu(this, binding.menuBtn)
        popup.menu.add(0, 1, 0, R.string.chat_rename)
        popup.menu.add(0, 2, 0, R.string.chat_sessions_title)
        popup.menu.add(0, 3, 0, R.string.chat_delete)
        popup.menu.add(0, 4, 0, R.string.chat_config_title)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> renameSession()
                2 -> startActivity(Intent(this, ChatSessionListActivity::class.java))
                3 -> confirmDelete()
                4 -> startActivity(Intent(this, ChatConfigActivity::class.java))
            }
            true
        }
        popup.show()
    }

    private fun renameSession() {
        val input = EditText(this)
        input.setText(currentSession?.title ?: "")
        AlertDialog.Builder(this)
            .setTitle(R.string.chat_rename)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newTitle = input.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    currentSession?.title = newTitle
                    binding.toolbarTitle.text = newTitle
                    currentSession?.let { session ->
                        session.updatedAt = System.currentTimeMillis()
                        Thread { ChatStorage.saveSession(this, session) }.start()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setMessage(R.string.chat_delete_confirm)
            .setPositiveButton(R.string.chat_delete) { _, _ ->
                currentSession?.let { session ->
                    Thread { ChatStorage.deleteSession(this, session.id) }.start()
                }
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        apiClient?.cancel()
        handler.removeCallbacksAndMessages(null)
    }
}
