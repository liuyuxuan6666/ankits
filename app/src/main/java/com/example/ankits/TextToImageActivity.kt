package com.example.ankits

import android.content.ContentValues
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ankits.databinding.ActivityTextToImageBinding

class TextToImageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTextToImageBinding
    private var previewBitmap: Bitmap? = null
    private val handler = Handler(Looper.getMainLooper())
    private val debounceDelay = 300L
    private val renderRunnable = Runnable { renderPreview() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTextToImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleWindowInsets()

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.fontSizeSeek.progress = 20
        binding.canvasWidthSeek.progress = 680
        updateFontSizeLabel(binding.fontSizeSeek.progress)
        updateCanvasWidthLabel(binding.canvasWidthSeek.progress)

        binding.fontSizeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateFontSizeLabel(progress)
                scheduleRender()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.canvasWidthSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateCanvasWidthLabel(progress)
                scheduleRender()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.textInput.addTextChangedListener(SimpleTextWatcher {
            scheduleRender()
        })

        binding.bgColorInput.addTextChangedListener(SimpleTextWatcher {
            updateSwatch(binding.bgColorSwatch, binding.bgColorInput.text.toString())
            scheduleRender()
        })
        binding.textColorInput.addTextChangedListener(SimpleTextWatcher {
            updateSwatch(binding.textColorSwatch, binding.textColorInput.text.toString())
            scheduleRender()
        })

        updateSwatch(binding.bgColorSwatch, "#FFFFFF")
        updateSwatch(binding.textColorSwatch, "#333333")

        binding.exportBtn.setOnClickListener { exportToGallery() }
    }

    private fun scheduleRender() {
        handler.removeCallbacks(renderRunnable)
        handler.postDelayed(renderRunnable, debounceDelay)
    }

    private fun handleWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { view, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(0, statusBar.top, 0, navBar.bottom)
            insets
        }
    }

    private fun updateFontSizeLabel(progress: Int) {
        val sp = 20 + progress
        binding.fontSizeLabel.text = "${sp}sp"
    }

    private fun updateCanvasWidthLabel(progress: Int) {
        val px = 400 + progress
        binding.canvasWidthLabel.text = "${px}px"
    }

    private fun updateSwatch(view: android.view.View, hex: String) {
        try {
            val color = Color.parseColor(hex)
            ViewCompat.setBackgroundTintList(view, ColorStateList.valueOf(color))
        } catch (_: Exception) { }
    }

    private fun parseColorSafely(hex: String, fallback: Int): Int {
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            fallback
        }
    }

    private fun renderPreview() {
        val text = binding.textInput.text?.toString() ?: ""
        if (text.isEmpty()) {
            previewBitmap = null
            binding.previewImage.setImageBitmap(null)
            return
        }

        val canvasWidth = 400 + binding.canvasWidthSeek.progress
        val fontSizeSp = 20 + binding.fontSizeSeek.progress
        val bgColor = parseColorSafely(binding.bgColorInput.text.toString(), Color.WHITE)
        val textColor = parseColorSafely(binding.textColorInput.text.toString(), Color.parseColor("#333333"))
        val padding = 48
        val textWidth = canvasWidth - padding * 2

        val fontSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            fontSizeSp.toFloat(),
            resources.displayMetrics
        )
        val textPaint = TextPaint().apply {
            color = textColor
            textSize = fontSizePx
            isAntiAlias = true
        }

        val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder
                .obtain(text, 0, text.length, textPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.2f)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, textPaint, textWidth, Layout.Alignment.ALIGN_NORMAL, 1.2f, 0f, false)
        }

        val totalHeight = layout.height + padding * 2

        previewBitmap = Bitmap.createBitmap(canvasWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(previewBitmap!!)
        canvas.drawColor(bgColor)
        canvas.save()
        canvas.translate(padding.toFloat(), padding.toFloat())
        layout.draw(canvas)
        canvas.restore()

        binding.previewImage.setImageBitmap(previewBitmap)
    }

    private fun exportToGallery() {
        val bitmap = previewBitmap
        if (bitmap == null) {
            Toast.makeText(this, "请先输入文本", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val filename = "ankits_${System.currentTimeMillis()}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Ankits")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    .toString() + "/Ankits/$filename"
                java.io.File(path).parentFile?.mkdirs()
                java.io.FileOutputStream(path).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }

            Toast.makeText(this, R.string.export_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "${getString(R.string.export_failed)}: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

private class SimpleTextWatcher(private val onChange: () -> Unit) :
    android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: android.text.Editable?) { onChange() }
}
