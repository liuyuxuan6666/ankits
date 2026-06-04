package com.example.ankits

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ankits.databinding.ItemChatMessageAssistantBinding
import com.example.ankits.databinding.ItemChatMessageUserBinding
import com.example.ankits.databinding.ItemTypingIndicatorBinding

class ChatMessageAdapter(
    private val messages: MutableList<ChatMessage> = mutableListOf()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_USER = 0
        private const val VIEW_TYPE_ASSISTANT = 1
        private const val VIEW_TYPE_TYPING = 2
    }

    private var showTyping = false

    override fun getItemViewType(position: Int): Int {
        return when {
            showTyping && position == messages.size -> VIEW_TYPE_TYPING
            messages[position].role == "user" -> VIEW_TYPE_USER
            else -> VIEW_TYPE_ASSISTANT
        }
    }

    override fun getItemCount() = messages.size + if (showTyping) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val view = inflater.inflate(R.layout.item_chat_message_user, parent, false)
                UserViewHolder(view)
            }
            VIEW_TYPE_ASSISTANT -> {
                val view = inflater.inflate(R.layout.item_chat_message_assistant, parent, false)
                AssistantViewHolder(view)
            }
            VIEW_TYPE_TYPING -> {
                val view = inflater.inflate(R.layout.item_typing_indicator, parent, false)
                TypingViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is UserViewHolder -> {
                val msg = messages[position]
                holder.bind(msg)
            }
            is AssistantViewHolder -> {
                val msg = messages[position]
                holder.bind(msg)
            }
            is TypingViewHolder -> {
            }
        }
    }

    fun appendMessage(msg: ChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    fun updateLastMessage(msg: ChatMessage) {
        if (messages.isNotEmpty()) {
            messages[messages.size - 1] = msg
            notifyItemChanged(messages.size - 1)
        }
    }

    fun showTypingIndicator() {
        if (!showTyping) {
            showTyping = true
            notifyItemInserted(messages.size)
        }
    }

    fun clearTypingIndicator() {
        if (showTyping) {
            showTyping = false
            notifyItemRemoved(messages.size)
        }
    }

    fun setMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        showTyping = false
        notifyDataSetChanged()
    }

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val binding = ItemChatMessageUserBinding.bind(view)
        private val messageText: TextView = binding.messageText
        private val imagePreview: ImageView = binding.imagePreview

        fun bind(msg: ChatMessage) {
            messageText.text = msg.content
            if (!msg.imageBase64.isNullOrEmpty()) {
                val bytes = android.util.Base64.decode(msg.imageBase64, android.util.Base64.DEFAULT)
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                imagePreview.setImageBitmap(bitmap)
                imagePreview.visibility = View.VISIBLE
            } else {
                imagePreview.visibility = View.GONE
            }
        }
    }

    class AssistantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val binding = ItemChatMessageAssistantBinding.bind(view)
        private val messageText: TextView = binding.messageText

        fun bind(msg: ChatMessage) {
            val formatted = MarkdownParser.formatMessage(msg.content)
            messageText.text = formatted
        }
    }

    class TypingViewHolder(view: View) : RecyclerView.ViewHolder(view)
}
