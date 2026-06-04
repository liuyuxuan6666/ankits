package com.example.ankits

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ankits.databinding.ActivityChatSessionListBinding
import com.example.ankits.databinding.ItemChatSessionBinding

class ChatSessionListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatSessionListBinding
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatSessionListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.rootLayout.setPadding(0, statusBar.top, 0, navBar.bottom)
            WindowInsetsCompat.CONSUMED
        }

        binding.sessionList.layoutManager = LinearLayoutManager(this)

        binding.backBtn.setOnClickListener { finish() }
        binding.newChatBtn.setOnClickListener { createNewChat() }
    }

    override fun onResume() {
        super.onResume()
        refreshSessions()
    }

    private fun refreshSessions() {
        Thread {
            val list = ChatStorage.listSessions(this)
            handler.post {
                if (list.isEmpty()) {
                    binding.emptyView.visibility = View.VISIBLE
                    binding.sessionList.visibility = View.GONE
                } else {
                    binding.emptyView.visibility = View.GONE
                    binding.sessionList.visibility = View.VISIBLE
                    binding.sessionList.adapter = SessionAdapter(list,
                        onItemClick = { meta -> openSession(meta.id) },
                        onItemLongClick = { meta -> confirmDelete(meta) }
                    )
                }
            }
        }.start()
    }

    private fun createNewChat() {
        Thread {
            val session = ChatStorage.createSession(this)
            handler.post {
                openSession(session.id)
            }
        }.start()
    }

    private fun openSession(sessionId: String) {
        startActivity(Intent(this, ChatActivity::class.java).apply {
            putExtra("session_id", sessionId)
        })
    }

    private fun confirmDelete(meta: ChatStorage.SessionMeta) {
        AlertDialog.Builder(this)
            .setTitle(meta.title)
            .setMessage(R.string.chat_delete_confirm)
            .setPositiveButton(R.string.chat_delete) { _, _ ->
                Thread {
                    ChatStorage.deleteSession(this, meta.id)
                    handler.post { refreshSessions() }
                }.start()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

class SessionAdapter(
    private var sessions: List<ChatStorage.SessionMeta>,
    private val onItemClick: (ChatStorage.SessionMeta) -> Unit,
    private val onItemLongClick: (ChatStorage.SessionMeta) -> Unit
) : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = ItemChatSessionBinding.bind(view).sessionTitle
        val dateText: TextView = ItemChatSessionBinding.bind(view).sessionDate
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_session, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = sessions[position]
        holder.titleText.text = session.title
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(session.updatedAt))
        holder.dateText.text = dateStr
        holder.itemView.setOnClickListener { onItemClick(session) }
        holder.itemView.setOnLongClickListener { onItemLongClick(session); true }
    }

    override fun getItemCount() = sessions.size

    fun updateList(newSessions: List<ChatStorage.SessionMeta>) {
        sessions = newSessions
        notifyDataSetChanged()
    }
}
