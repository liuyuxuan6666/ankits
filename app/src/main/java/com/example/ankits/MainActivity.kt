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

        val allTools = listOf(
            Tool(
                name = getString(R.string.tool_text_to_image),
                desc = getString(R.string.tool_text_to_image_desc),
                icon = R.drawable.ic_text_to_image,
                targetActivity = TextToImageActivity::class.java
            )
        )

        toolAdapter = ToolAdapter(allTools)
        binding.toolList.layoutManager = LinearLayoutManager(this)
        binding.toolList.adapter = toolAdapter

        binding.settingsBtn.setOnClickListener {
            // TODO: open settings
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
    }

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
    val name: String,
    val desc: String,
    val icon: Int,
    val targetActivity: Class<*>
)

class ToolAdapter(
    private val tools: List<Tool>
) : RecyclerView.Adapter<ToolAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val toolIcon: ImageView = ItemToolCardBinding.bind(view).toolIcon
        val toolName: TextView = ItemToolCardBinding.bind(view).toolName
        val toolDesc: TextView = ItemToolCardBinding.bind(view).toolDesc
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
        holder.itemView.setOnClickListener {
            val ctx = holder.itemView.context
            ctx.startActivity(Intent(ctx, tool.targetActivity))
        }
    }

    override fun getItemCount() = tools.size
}
