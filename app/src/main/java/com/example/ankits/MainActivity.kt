package com.example.ankits

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ankits.databinding.ActivityMainBinding
import com.example.ankits.databinding.ItemToolCardBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleWindowInsets()

        binding.toolList.layoutManager = LinearLayoutManager(this)
        binding.toolList.adapter = ToolAdapter(
            listOf(
                Tool(
                    name = getString(R.string.tool_text_to_image),
                    desc = getString(R.string.tool_text_to_image_desc),
                    icon = R.drawable.ic_text_to_image,
                    targetActivity = TextToImageActivity::class.java
                ),
                Tool(
                    name = getString(R.string.tool_metronome),
                    desc = getString(R.string.tool_metronome_desc),
                    icon = R.drawable.ic_metronome,
                    targetActivity = MetronomeActivity::class.java
                )
            )
        )
    }

    private fun handleWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { view, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(0, statusBar.top, 0, navBar.bottom)
            insets
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
