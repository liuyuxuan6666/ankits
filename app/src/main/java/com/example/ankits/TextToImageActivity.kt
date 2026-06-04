package com.example.ankits

import android.app.Dialog
import android.content.ContentValues
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
import com.example.ankits.databinding.DialogPreviewBinding
import com.google.android.material.chip.Chip
import android.graphics.Typeface

class TextToImageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTextToImageBinding
    private var previewBitmap: Bitmap? = null
    private val handler = Handler(Looper.getMainLooper())
    private val debounceDelay = 300L
    private val renderRunnable = Runnable { renderPreview() }
    private var currentTemplate: Template = Templates.SIMPLE
    private var currentSize: ImageSize = ImageSizes.WECHAT
    private var currentLineSpacing: Float = 1.3f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTextToImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleWindowInsets()

        binding.backBtn.setOnClickListener { finish() }
        binding.settingsBtn.setOnClickListener { /* TODO: open settings */ }

        binding.fontSizeSeek.progress = 20
        updateFontSizeLabel(binding.fontSizeSeek.progress)

        binding.fontSizeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateFontSizeLabel(progress)
                scheduleRender()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.lineSpacingSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateLineSpacingLabel(progress)
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

        binding.exportBtn.setOnClickListener { exportToGallery() }
        binding.previewBtn.setOnClickListener { showPreviewDialog() }

        setupSizeChips()
        setupTemplateChips()
        selectSize(ImageSizes.WECHAT)
        selectTemplate(Templates.SIMPLE)

        setupCollapsibleCards()
    }

    private fun setupCollapsibleCards() {
        val cards = listOf(
            Triple(binding.sizeHeader, binding.sizeContent, binding.sizeChevron),
            Triple(binding.templateHeader, binding.templateContent, binding.templateChevron),
            Triple(binding.styleHeader, binding.styleContent, binding.styleChevron)
        )
        for ((header, content, chevron) in cards) {
            header.setOnClickListener {
                if (content.visibility == android.view.View.GONE) {
                    content.visibility = android.view.View.VISIBLE
                    chevron.text = "▾"
                } else {
                    content.visibility = android.view.View.GONE
                    chevron.text = "▸"
                }
            }
        }
    }

    private fun setupSizeChips() {
        val chipGroup = binding.sizeChips
        for (size in ImageSizes.ALL) {
            val chip = Chip(this).apply {
                text = size.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                isAllCaps = false
                setEnsureMinTouchTargetSize(false)
                chipMinHeight = (36 * resources.displayMetrics.density).toFloat()
                chipBackgroundColor = ColorStateList.valueOf(Color.TRANSPARENT)
                setTextColor(Color.parseColor("#49454F"))
                chipStrokeWidth = 1f
                chipStrokeColor = ColorStateList.valueOf(Color.parseColor("#CAC4D0"))
                chipCornerRadius = 18f
                setOnClickListener { selectSize(size) }
            }
            chipGroup.addView(chip)
        }
    }

    private fun selectSize(size: ImageSize) {
        currentSize = size
        updateSizeChipStyles()
        scheduleRender()
    }

    private fun updateSizeChipStyles() {
        val chipGroup = binding.sizeChips
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as Chip
            val selected = ImageSizes.ALL[i] == currentSize
            if (selected) {
                chip.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#1A73E8"))
                chip.setTextColor(Color.WHITE)
                chip.chipStrokeWidth = 0f
            } else {
                chip.chipBackgroundColor = ColorStateList.valueOf(Color.TRANSPARENT)
                chip.setTextColor(Color.parseColor("#49454F"))
                chip.chipStrokeWidth = 1f
                chip.chipStrokeColor = ColorStateList.valueOf(Color.parseColor("#CAC4D0"))
            }
        }
    }

    private fun setupTemplateChips() {
        val chipGroup = binding.templateChips
        for (template in Templates.ALL) {
            val chip = Chip(this).apply {
                text = template.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                isAllCaps = false
                setEnsureMinTouchTargetSize(false)
                chipMinHeight = (40 * resources.displayMetrics.density).toFloat()
                chipBackgroundColor = ColorStateList.valueOf(Color.TRANSPARENT)
                setTextColor(Color.parseColor("#49454F"))
                chipStrokeWidth = 1f
                chipStrokeColor = ColorStateList.valueOf(Color.parseColor("#CAC4D0"))
                chipCornerRadius = 20f
                setOnClickListener { selectTemplate(template) }
            }
            chipGroup.addView(chip)
        }
    }

    private fun selectTemplate(template: Template) {
        currentTemplate = template

        val mainStyle = template.mainStyle
        binding.bgColorInput.setText(colorToHex(mainStyle.bgColor))
        binding.textColorInput.setText(colorToHex(mainStyle.textColor))
        val sp = mainStyle.bodySizeSp.toInt()
        val progress = (sp - 20).coerceIn(0, 60)
        binding.fontSizeSeek.progress = progress
        updateFontSizeLabel(progress)

        val ls = template.bodyLineSpacing
        val lsProgress = ((ls - 0.8f) / 0.025f).toInt().coerceIn(0, 88)
        binding.lineSpacingSeek.progress = lsProgress
        updateLineSpacingLabel(lsProgress)

        updateChipStyles()
        scheduleRender()
    }

    private fun updateChipStyles() {
        val chipGroup = binding.templateChips
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as Chip
            val selected = Templates.ALL[i] == currentTemplate
            if (selected) {
                chip.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#1A73E8"))
                chip.setTextColor(Color.WHITE)
                chip.chipStrokeWidth = 0f
            } else {
                chip.chipBackgroundColor = ColorStateList.valueOf(Color.TRANSPARENT)
                chip.setTextColor(Color.parseColor("#49454F"))
                chip.chipStrokeWidth = 1f
                chip.chipStrokeColor = ColorStateList.valueOf(Color.parseColor("#CAC4D0"))
            }
        }
    }

    private fun colorToHex(color: Int): String {
        if (color == Color.TRANSPARENT) return "#FFFFFF"
        return String.format("#%06X", 0xFFFFFF and color)
    }

    private fun scheduleRender() {
        handler.removeCallbacks(renderRunnable)
        handler.postDelayed(renderRunnable, debounceDelay)
    }

    private fun handleWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { view, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(0, statusBar.top, 0, 0)
            binding.bottomBar.setPadding(
                binding.bottomBar.paddingLeft,
                binding.bottomBar.paddingTop,
                binding.bottomBar.paddingRight,
                navBar.bottom
            )
            insets
        }
    }

    private fun updateFontSizeLabel(progress: Int) {
        val sp = 20 + progress
        binding.fontSizeLabel.text = "${sp}sp"
    }

    private fun updateLineSpacingLabel(progress: Int) {
        val mult = 0.8f + progress * 0.025f
        currentLineSpacing = mult
        binding.lineSpacingLabel.text = String.format("%.2fx", mult)
    }

    private fun updateSwatch(view: android.view.View, hex: String) {
        try {
            val color = Color.parseColor(hex)
            ViewCompat.setBackgroundTintList(view, ColorStateList.valueOf(color))
        } catch (_: Exception) { }
    }

    private fun parseColorSafely(hex: String, fallback: Int): Int {
        return try { Color.parseColor(hex) } catch (_: Exception) { fallback }
    }

    private fun renderPreview() {
        val text = binding.textInput.text?.toString() ?: ""
        if (text.isEmpty()) {
            previewBitmap = null
            return
        }

        val canvasWidth = currentSize.width
        val density = resources.displayMetrics.density
        val sections = MarkdownParser.parse(text)
        val template = currentTemplate

        val bodySizeSp = (20 + binding.fontSizeSeek.progress).toFloat()
        val bodyColor = parseColorSafely(binding.bgColorInput.text.toString(), Color.WHITE)
        val bodyTextColor = parseColorSafely(binding.textColorInput.text.toString(), Color.parseColor("#333333"))

        val outerPadding = (template.outerPaddingDp * density).toInt()

        // top padding
        var totalHeight = outerPadding
        val sectionRenderers = mutableListOf<SectionRenderer>()

        val blankLineHeight = (TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, bodySizeSp, resources.displayMetrics
        ) * currentLineSpacing).toInt()

        for (section in sections) {
            // Blanks and HRs are handled specially — no mainStyle needed
            if (section.type == SectionType.BLANK) {
                totalHeight += blankLineHeight
                if (section != sections.lastOrNull()) {
                    totalHeight += (template.sectionGapDp * density).toInt()
                }
                continue
            }

            if (section.type == SectionType.HR) {
                totalHeight += (20f * density).toInt()
                if (section != sections.lastOrNull()) {
                    totalHeight += (template.sectionGapDp * density).toInt()
                }
                continue
            }

            val style = when (section.type) {
                SectionType.HERO -> template.heroStyle
                SectionType.SUB -> template.subStyle
                SectionType.H3 -> template.subStyle.copy(
                    titleSizeSp = template.subStyle.titleSizeSp - 3f
                )
                SectionType.CODE -> template.mainStyle
                SectionType.QUOTE -> template.mainStyle
                SectionType.LIST_ITEM -> template.mainStyle
                SectionType.BODY -> template.mainStyle
                else -> template.mainStyle
            }
            val sectionWidth = (canvasWidth - outerPadding * 2.3).toInt()

            renderer@ val renderer = SectionRenderer()
            renderer.section = section
            renderer.style = style
            renderer.yStart = totalHeight
            renderer.blankLineH = blankLineHeight

            val padLeftPx = (style.paddingLeftDp * density).toInt()
            val padRightPx = (style.paddingRightDp * density).toInt()
            var sectionInnerWidth = sectionWidth - padLeftPx - padRightPx

            // LIST_ITEM and QUOTE: extra indent for bullet/accent
            val indentPx = when (section.type) {
                SectionType.LIST_ITEM -> (20f * density).toInt()
                SectionType.QUOTE -> (16f * density).toInt()
                else -> 0
            }
            sectionInnerWidth -= indentPx

            var sectionHeight = 0
            sectionHeight += (style.paddingTopDp * density).toInt()

            var titleLayout: StaticLayout? = null
            if (section.title != null && section.title.isNotEmpty()) {
                val titlePaint = TextPaint().apply {
                    color = if (style.titleTextColor != 0) style.titleTextColor else style.textColor
                    textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, style.titleSizeSp, resources.displayMetrics)
                    isAntiAlias = true
                    if (style.titleBold) typeface = Typeface.DEFAULT_BOLD
                }
                titleLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    StaticLayout.Builder.obtain(section.title, 0, section.title.length, titlePaint, sectionInnerWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, 1.2f).build()
                } else {
                    @Suppress("DEPRECATION")
                    StaticLayout(section.title, titlePaint, sectionInnerWidth, Layout.Alignment.ALIGN_NORMAL, 1.2f, 0f, false)
                }
                val titlePad = if (style.titleCornerRadiusDp > 0f) (8f * density).toInt() else 0
                sectionHeight += titlePad + titleLayout.height + titlePad
            }

            var bodyLayout: StaticLayout? = null
            if (section.body.isNotEmpty()) {
                val isCode = section.type == SectionType.CODE
                val bodyPaint = TextPaint().apply {
                    color = bodyTextColor
                    textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                        if (isCode) bodySizeSp - 1f else bodySizeSp, resources.displayMetrics)
                    isAntiAlias = true
                    if (isCode) typeface = Typeface.MONOSPACE
                }
                bodyLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    StaticLayout.Builder.obtain(section.body, 0, section.body.length, bodyPaint, sectionInnerWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, if (isCode) 1.1f else currentLineSpacing).build()
                } else {
                    @Suppress("DEPRECATION")
                    StaticLayout(section.body, bodyPaint, sectionInnerWidth, Layout.Alignment.ALIGN_NORMAL,
                        if (isCode) 1.1f else currentLineSpacing, 0f, false)
                }
                val gap = if (titleLayout != null) (6f * density).toInt() else 0
                sectionHeight += gap + bodyLayout.height
            }

            sectionHeight += (style.paddingBottomDp * density).toInt()

            if (section != sections.lastOrNull()) {
                sectionHeight += (template.sectionGapDp * density).toInt()
            }

            renderer.titleLayout = titleLayout
            renderer.bodyLayout = bodyLayout
            renderer.sectionHeight = sectionHeight
            renderer.sectionWidth = sectionWidth
            renderer.indentPx = indentPx

            sectionRenderers.add(renderer)
            totalHeight += sectionHeight
        }

        // bottom padding = same as left/right
        totalHeight += outerPadding

        if (totalHeight <= 0) return

        previewBitmap = Bitmap.createBitmap(canvasWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(previewBitmap!!)

        canvas.drawColor(template.canvasBg)

        for (renderer in sectionRenderers) {
            val section = renderer.section ?: continue
            val style = renderer.style!!
            val x = outerPadding
            var y = renderer.yStart
            val w = renderer.sectionWidth
            val h = renderer.sectionHeight - if (renderer != sectionRenderers.lastOrNull()) (template.sectionGapDp * density).toInt() else 0

            // CODE background
            val sectionBgColor: Int = when {
                section.type == SectionType.CODE -> {
                    val hsv = FloatArray(3)
                    Color.colorToHSV(bodyColor, hsv)
                    hsv[2] = (hsv[2] * 0.92f).coerceAtLeast(0f)
                    Color.HSVToColor(hsv)
                }
                style.bgColor != Color.TRANSPARENT && style.bgColor != 0 -> style.bgColor
                else -> 0
            }

            if (sectionBgColor != 0) {
                val bgPaint = Paint().apply {
                    color = sectionBgColor
                    isAntiAlias = true
                }
                if (style.cornerRadiusDp > 0f) {
                    val radius = style.cornerRadiusDp * density
                    canvas.drawRoundRect(RectF(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat()), radius, radius, bgPaint)
                } else {
                    canvas.drawRect(RectF(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat()), bgPaint)
                }
            }

            if (style.accentWidthDp > 0f) {
                val accentW = style.accentWidthDp * density
                val accentPaint = Paint().apply {
                    color = style.accentColor
                    isAntiAlias = true
                }
                canvas.drawRect(RectF(x.toFloat(), y.toFloat(), x + accentW, (y + h).toFloat()), accentPaint)
            }

            // QUOTE accent bar
            if (section.type == SectionType.QUOTE) {
                val quoteAccentW = 4f * density
                val quoteAccentPaint = Paint().apply {
                    color = Color.parseColor("#1A73E8")
                    isAntiAlias = true
                }
                val padTop = (style.paddingTopDp * density)
                val padBot = (style.paddingBottomDp * density)
                canvas.drawRoundRect(
                    RectF(x.toFloat(), y + padTop, x + quoteAccentW, y + h - padBot),
                    quoteAccentW / 2f, quoteAccentW / 2f, quoteAccentPaint
                )
            }

            if (style.dividerColor != 0) {
                val divPaint = Paint().apply {
                    color = style.dividerColor
                    strokeWidth = 1f * density
                }
                canvas.drawLine(x.toFloat(), (y + h).toFloat(), (x + w).toFloat(), (y + h).toFloat(), divPaint)
            }

            val padLeft = (style.paddingLeftDp * density).toInt()

            // LIST_ITEM bullet
            if (section.type == SectionType.LIST_ITEM && section.meta.isNotEmpty()) {
                val bulletPaint = TextPaint().apply {
                    color = bodyTextColor
                    textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, bodySizeSp, resources.displayMetrics)
                    isAntiAlias = true
                }
                val bulletY = y + (style.paddingTopDp * density)
                canvas.save()
                canvas.translate((x + padLeft).toFloat(), bulletY)
                val bulletText = if (section.meta.length <= 3) "${section.meta} " else "• "
                canvas.drawText(bulletText, 0f, -bulletPaint.ascent(), bulletPaint)
                canvas.restore()
            }

            var cy = y + (style.paddingTopDp * density).toInt()

            renderer.titleLayout?.let { titleLayout ->
                if (style.titleCornerRadiusDp > 0f && style.titleBgColor != 0) {
                    val titlePad = (8f * density).toInt()
                    val titleW = titleLayout.width + titlePad * 2
                    val titleH = titleLayout.height + titlePad * 2
                    val rect = RectF(
                        (x + padLeft + renderer.indentPx - titlePad).toFloat(),
                        (cy - titlePad).toFloat(),
                        (x + padLeft + renderer.indentPx - titlePad + titleW).toFloat(),
                        (cy - titlePad + titleH).toFloat()
                    )
                    val radius = style.titleCornerRadiusDp * density
                    val bgPaint = Paint().apply { color = style.titleBgColor; isAntiAlias = true }
                    canvas.drawRoundRect(rect, radius, radius, bgPaint)
                    cy += titlePad
                }
                canvas.save()
                canvas.translate((x + padLeft + renderer.indentPx).toFloat(), cy.toFloat())
                titleLayout.draw(canvas)
                canvas.restore()
                cy += titleLayout.height + if (style.titleCornerRadiusDp > 0f) (8f * density).toInt() else 0
            }

            renderer.bodyLayout?.let { bodyLayout ->
                val gap = if (renderer.titleLayout != null) (6f * density).toInt() else 0
                cy += gap
                canvas.save()
                canvas.translate((x + padLeft + renderer.indentPx).toFloat(), cy.toFloat())
                bodyLayout.draw(canvas)
                canvas.restore()
            }
        }
    }

    private fun showPreviewDialog() {
        val bitmap = previewBitmap
        if (bitmap == null) {
            Toast.makeText(this, "请先输入文本", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val dialogBinding = DialogPreviewBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.previewToolbar.setNavigationOnClickListener { dialog.dismiss() }

        dialogBinding.previewImageView.post {
            val screenWidth = dialogBinding.previewImageView.width
            if (screenWidth > 0 && bitmap.width > 0) {
                val scale = screenWidth.toFloat() / bitmap.width.toFloat()
                val scaledHeight = (bitmap.height * scale).toInt()
                val scaled = Bitmap.createScaledBitmap(bitmap, screenWidth, scaledHeight, true)
                dialogBinding.previewImageView.setImageBitmap(scaled)
            } else {
                dialogBinding.previewImageView.setImageBitmap(bitmap)
            }
        }

        dialog.show()
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

    private class SectionRenderer {
        var section: MarkdownSection? = null
        var style: SectionStyle? = null
        var yStart: Int = 0
        var sectionHeight: Int = 0
        var sectionWidth: Int = 0
        var titleLayout: StaticLayout? = null
        var bodyLayout: StaticLayout? = null
        var blankLineH: Int = 0
        var indentPx: Int = 0
    }
}

private class SimpleTextWatcher(private val onChange: () -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: android.text.Editable?) { onChange() }
}
