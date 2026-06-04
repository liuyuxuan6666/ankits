package com.example.ankits

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ankits.databinding.ActivityMainBinding
import com.example.ankits.databinding.ItemToolCardBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var toolAdapter: ToolAdapter
    private var isFavoritesTab = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleWindowInsets()

        val hasFavorites = allTools.any {
            SettingsManager.isToolEnabled(this, it.key) && SettingsManager.isFavorite(this, it.key)
        }
        if (hasFavorites) {
            isFavoritesTab = true
            val primary = ContextCompat.getColor(this, R.color.primary)
            val variant = ContextCompat.getColor(this, R.color.on_surface_variant)
            binding.iconFavorites.setColorFilter(primary)
            binding.labelFavorites.setTextColor(primary)
            binding.iconTools.setColorFilter(variant)
            binding.labelTools.setTextColor(variant)
        }

        toolAdapter = ToolAdapter(getVisibleTools()) { tool ->
            val fav = !SettingsManager.isFavorite(this, tool.key)
            SettingsManager.setFavorite(this, tool.key, fav)
            toolAdapter.updateTools(getVisibleTools())
        }
        binding.toolList.layoutManager = LinearLayoutManager(this)
        binding.toolList.adapter = toolAdapter

        binding.settingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.tabFavorites.setOnClickListener { selectTab(true) }
        binding.tabTools.setOnClickListener { selectTab(false) }
    }

    private fun selectTab(favorites: Boolean) {
        if (isFavoritesTab == favorites) return
        isFavoritesTab = favorites

        val primary = ContextCompat.getColor(this, R.color.primary)
        val variant = ContextCompat.getColor(this, R.color.on_surface_variant)

        if (favorites) {
            binding.iconFavorites.setColorFilter(primary)
            binding.labelFavorites.setTextColor(primary)
            binding.iconTools.setColorFilter(variant)
            binding.labelTools.setTextColor(variant)
        } else {
            binding.iconTools.setColorFilter(primary)
            binding.labelTools.setTextColor(primary)
            binding.iconFavorites.setColorFilter(variant)
            binding.labelFavorites.setTextColor(variant)
        }
        toolAdapter.updateTools(getVisibleTools())
    }

    override fun onResume() {
        super.onResume()
        toolAdapter.updateTools(getVisibleTools())
    }

    private fun getVisibleTools(): List<Tool> {
        val enabled = allTools.filter { SettingsManager.isToolEnabled(this, it.key) }
        return if (isFavoritesTab) {
            enabled.filter { SettingsManager.isFavorite(this, it.key) }
        } else {
            enabled
        }
    }

    private val allTools: List<Tool>
        get() = listOf(
            Tool(
                key = "text_to_image",
                name = getString(R.string.tool_text_to_image),
                desc = getString(R.string.tool_text_to_image_desc),
                icon = R.drawable.ic_text_to_image,
                targetActivity = TextToImageActivity::class.java
            ),
            Tool(
                key = "metronome",
                name = getString(R.string.tool_metronome),
                desc = getString(R.string.tool_metronome_desc),
                icon = R.drawable.ic_metronome,
                targetActivity = MetronomeActivity::class.java
            ),
            Tool(
                key = "tuner",
                name = getString(R.string.tool_tuner),
                desc = getString(R.string.tool_tuner_desc),
                icon = R.drawable.ic_tuner,
                targetActivity = TunerActivity::class.java
            ),
            Tool(
                key = "speech_to_text",
                name = getString(R.string.tool_speech_to_text),
                desc = getString(R.string.tool_speech_to_text_desc),
                icon = R.drawable.ic_speech_to_text,
                targetActivity = SpeechToTextActivity::class.java
            ),
            Tool(
                key = "ocr",
                name = getString(R.string.tool_ocr),
                desc = getString(R.string.tool_ocr_desc),
                icon = R.drawable.ic_ocr,
                targetActivity = OcrActivity::class.java
            ),
            Tool(
                key = "sleep_aid",
                name = getString(R.string.tool_sleep_aid),
                desc = getString(R.string.tool_sleep_aid_desc),
                icon = R.drawable.ic_sleep_aid,
                targetActivity = SleepAidActivity::class.java
            ),
            Tool(
                key = "pomodoro",
                name = getString(R.string.tool_pomodoro),
                desc = getString(R.string.tool_pomodoro_desc),
                icon = R.drawable.ic_pomodoro,
                targetActivity = PomodoroActivity::class.java
            ),
            Tool(
                key = "chat",
                name = getString(R.string.tool_chat),
                desc = getString(R.string.tool_chat_desc),
                icon = R.drawable.ic_chat,
                targetActivity = ChatActivity::class.java
            ),
            Tool(
                key = "tts",
                name = getString(R.string.tool_tts),
                desc = getString(R.string.tool_tts_desc),
                icon = R.drawable.ic_tts,
                targetActivity = TtsActivity::class.java
            ),
            Tool(
                key = "image_stitch",
                name = getString(R.string.tool_image_stitch),
                desc = getString(R.string.tool_image_stitch_desc),
                icon = R.drawable.ic_image_stitch,
                targetActivity = ImageStitchActivity::class.java
            )
        )

    private fun handleWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.rootLayout.setPadding(0, statusBar.top, 0, navBar.bottom)
            binding.bottomBar.setPadding(
                navBar.left,
                0,
                navBar.right,
                0
            )
            WindowInsetsCompat.CONSUMED
        }
    }
}

data class Tool(
    val key: String,
    val name: String,
    val desc: String,
    val icon: Int,
    val targetActivity: Class<*>
)

class ToolAdapter(
    private var tools: List<Tool>,
    private val onFavoriteClick: (Tool) -> Unit
) : RecyclerView.Adapter<ToolAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val toolIcon: ImageView = ItemToolCardBinding.bind(view).toolIcon
        val toolName: TextView = ItemToolCardBinding.bind(view).toolName
        val toolDesc: TextView = ItemToolCardBinding.bind(view).toolDesc
        val favButton: ImageView = ItemToolCardBinding.bind(view).favButton
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tool_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tool = tools[position]
        holder.toolIcon.setImageResource(tool.icon)
        holder.toolName.text = tool.name
        holder.toolDesc.text = tool.desc

        val ctx = holder.itemView.context
        val isFav = SettingsManager.isFavorite(ctx, tool.key)
        holder.favButton.setImageResource(
            if (isFav) R.drawable.ic_favorites else R.drawable.ic_favorite_border
        )
        if (isFav) {
            holder.favButton.setColorFilter(android.graphics.Color.parseColor("#F5A623"))
        } else {
            holder.favButton.colorFilter = null
        }
        holder.favButton.setOnClickListener {
            onFavoriteClick(tool)
        }

        holder.itemView.setOnClickListener {
            ctx.startActivity(Intent(ctx, tool.targetActivity))
        }
    }

    override fun getItemCount() = tools.size

    fun updateTools(newTools: List<Tool>) {
        tools = newTools
        notifyDataSetChanged()
    }
}
