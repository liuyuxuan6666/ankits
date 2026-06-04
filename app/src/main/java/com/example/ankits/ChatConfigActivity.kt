package com.example.ankits

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ankits.databinding.ActivityChatConfigBinding

class ChatConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatConfigBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.rootLayout.setPadding(0, statusBar.top, 0, navBar.bottom)
            WindowInsetsCompat.CONSUMED
        }

        binding.toolbarTitle.text = getString(R.string.chat_config_title)

        binding.endpointInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { updateEndpointPreview() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        loadConfig()

        setupCollapsibleCards()

        binding.maxTokensSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val tokens = (progress + 1) * 1000
                binding.maxTokensLabel.text = tokens.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        binding.saveBtn.setOnClickListener { saveConfig() }
        binding.backBtn.setOnClickListener { finish() }
    }

    private fun updateEndpointPreview() {
        val raw = binding.endpointInput.text.toString().trim()
        val normalized = ChatApiClient.normalizeEndpoint(raw)
        if (normalized.isEmpty()) {
            binding.endpointPreview.text = ""
        } else {
            binding.endpointPreview.text = getString(R.string.chat_config_request_url, "$normalized/v1/chat/completions")
        }
    }

    private fun loadConfig() {
        binding.endpointInput.setText(SettingsManager.getString(this, "chat_endpoint", "https://api.openai.com"))
        binding.apiKeyInput.setText(SettingsManager.getString(this, "chat_api_key", ""))
        binding.modelInput.setText(SettingsManager.getString(this, "chat_model", "gpt-4o-mini"))
        binding.systemPromptInput.setText(SettingsManager.getString(this, "chat_system_prompt", "You are a helpful assistant."))
        binding.streamingSwitch.isChecked = SettingsManager.getBool(this, "chat_streaming", true)
        val maxTokens = SettingsManager.getInt(this, "chat_max_tokens", 4000)
        val progress = (maxTokens / 1000).coerceIn(1, 16) - 1
        binding.maxTokensSeekBar.progress = progress
        binding.maxTokensLabel.text = ((progress + 1) * 1000).toString()
        updateEndpointPreview()
    }

    private fun saveConfig() {
        SettingsManager.setString(this, "chat_endpoint", binding.endpointInput.text.toString().trim())
        SettingsManager.setString(this, "chat_api_key", binding.apiKeyInput.text.toString().trim())
        SettingsManager.setString(this, "chat_model", binding.modelInput.text.toString().trim())
        SettingsManager.setString(this, "chat_system_prompt", binding.systemPromptInput.text.toString().trim())
        SettingsManager.setBool(this, "chat_streaming", binding.streamingSwitch.isChecked)
        val tokens = ((binding.maxTokensSeekBar.progress + 1) * 1000)
        SettingsManager.setInt(this, "chat_max_tokens", tokens)
        Toast.makeText(this, R.string.chat_config_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun setupCollapsibleCards() {
        binding.advancedHeader.setOnClickListener {
            if (binding.advancedContent.visibility == View.GONE) {
                binding.advancedContent.visibility = View.VISIBLE
                binding.advancedChevron.text = "\u25BE"
            } else {
                binding.advancedContent.visibility = View.GONE
                binding.advancedChevron.text = "\u25B8"
            }
        }
    }
}
