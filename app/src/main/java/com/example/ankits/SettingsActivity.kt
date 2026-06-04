package com.example.ankits

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.content.Intent
import com.example.ankits.databinding.ActivitySettingsBinding
import com.example.ankits.databinding.ItemSettingGroupBinding
import com.example.ankits.databinding.ItemSettingNavigateBinding
import com.example.ankits.databinding.ItemSettingToggleBinding
import com.google.android.material.materialswitch.MaterialSwitch

sealed class SettingItem(val key: String) {
    class Group(
        key: String,
        val title: String,
        val children: List<SettingItem>
    ) : SettingItem(key)

    class Toggle(
        key: String,
        val title: String,
        val prefKey: String,
        val enabled: Boolean
    ) : SettingItem(key)

    class Navigate(
        key: String,
        val title: String,
        val targetActivity: Class<*>
    ) : SettingItem(key)
}

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var adapter: SettingsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.rootLayout.setPadding(0, statusBar.top, 0, navBar.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val tools = listOf(
            "text_to_image" to getString(R.string.tool_text_to_image),
            "metronome" to getString(R.string.tool_metronome),
            "tuner" to getString(R.string.tool_tuner),
            "speech_to_text" to getString(R.string.tool_speech_to_text),
            "ocr" to getString(R.string.tool_ocr),
            "sleep_aid" to getString(R.string.tool_sleep_aid),
            "pomodoro" to getString(R.string.tool_pomodoro),
            "chat" to getString(R.string.tool_chat)
        )

        val featureToggles = tools.map { (key, name) ->
            SettingItem.Toggle(
                key = key,
                title = name,
                prefKey = key,
                enabled = SettingsManager.isToolEnabled(this, key)
            )
        }

        val masterItems = listOf(
            SettingItem.Group(
                key = "feature_toggles",
                title = getString(R.string.settings_feature_toggles),
                children = featureToggles
            )
        )

        adapter = SettingsAdapter(masterItems)
        binding.settingsList.layoutManager = LinearLayoutManager(this)
        binding.settingsList.adapter = adapter

        binding.backBtn.setOnClickListener { finish() }
    }
}

class SettingsAdapter(
    private val masterItems: List<SettingItem>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_GROUP = 0
        private const val VIEW_TYPE_TOGGLE = 1
        private const val VIEW_TYPE_NAVIGATE = 2
    }

    private val expandedGroups = mutableMapOf<String, Boolean>()
    private val displayItems = mutableListOf<SettingItem>()

    init {
        rebuildDisplayList()
    }

    private fun rebuildDisplayList() {
        displayItems.clear()
        for (item in masterItems) {
            displayItems.add(item)
            if (item is SettingItem.Group && expandedGroups[item.key] == true) {
                displayItems.addAll(item.children)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayItems[position]) {
            is SettingItem.Group -> VIEW_TYPE_GROUP
            is SettingItem.Toggle -> VIEW_TYPE_TOGGLE
            is SettingItem.Navigate -> VIEW_TYPE_NAVIGATE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_GROUP -> {
                val view = inflater.inflate(R.layout.item_setting_group, parent, false)
                GroupViewHolder(view)
            }
            VIEW_TYPE_TOGGLE -> {
                val view = inflater.inflate(R.layout.item_setting_toggle, parent, false)
                ToggleViewHolder(view)
            }
            VIEW_TYPE_NAVIGATE -> {
                val view = inflater.inflate(R.layout.item_setting_navigate, parent, false)
                NavigateViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = displayItems[position]) {
            is SettingItem.Group -> (holder as GroupViewHolder).bind(item)
            is SettingItem.Toggle -> (holder as ToggleViewHolder).bind(item)
            is SettingItem.Navigate -> (holder as NavigateViewHolder).bind(item)
        }
    }

    override fun getItemCount() = displayItems.size

    inner class GroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleText: TextView = ItemSettingGroupBinding.bind(view).groupTitle
        private val chevronText: TextView = ItemSettingGroupBinding.bind(view).groupChevron

        fun bind(group: SettingItem.Group) {
            titleText.text = group.title
            val expanded = expandedGroups[group.key] == true
            chevronText.text = if (expanded) "\u25BE" else "\u25B8"

            itemView.setOnClickListener {
                val nowExpanded = expandedGroups[group.key] == true
                expandedGroups[group.key] = !nowExpanded
                rebuildDisplayList()
                notifyDataSetChanged()
            }
        }
    }

    inner class ToggleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleText: TextView = ItemSettingToggleBinding.bind(view).toggleTitle
        private val switch: MaterialSwitch = ItemSettingToggleBinding.bind(view).toggleSwitch

        fun bind(toggle: SettingItem.Toggle) {
            titleText.text = toggle.title
            switch.setOnCheckedChangeListener(null)
            switch.isChecked = toggle.enabled
            switch.setOnCheckedChangeListener { _, isChecked ->
                SettingsManager.setToolEnabled(itemView.context, toggle.prefKey, isChecked)
            }
        }
    }

    inner class NavigateViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleText: TextView = ItemSettingNavigateBinding.bind(view).navTitle
        private val chevronText: TextView = ItemSettingNavigateBinding.bind(view).navChevron

        fun bind(item: SettingItem.Navigate) {
            titleText.text = item.title
            chevronText.text = "\u25B8"
            itemView.setOnClickListener {
                itemView.context.startActivity(Intent(itemView.context, item.targetActivity))
            }
        }
    }
}
