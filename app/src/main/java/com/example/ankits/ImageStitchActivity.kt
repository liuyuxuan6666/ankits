package com.example.ankits

import android.app.Dialog
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ankits.databinding.ActivityImageStitchBinding
import com.example.ankits.databinding.DialogPreviewBinding

class ImageStitchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageStitchBinding
    private val images = mutableListOf<StitchImage>()
    private val seamOffsets = mutableListOf<Int>()
    private var isVertical = true
    private var previewBitmap: Bitmap? = null
    private val handler = Handler(Looper.getMainLooper())
    private val debounceDelay = 300L
    private val renderRunnable = Runnable { renderPreview() }
    private lateinit var imageAdapter: StitchImageAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var imageContentExpanded = true
    private var seamContentExpanded = true

    private val pickImagesLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris.isNotEmpty()) addImages(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageStitchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleWindowInsets()

        binding.backBtn.setOnClickListener { finish() }

        imageAdapter = StitchImageAdapter(
            onRemove = { index -> removeImage(index) },
            onDragStart = { viewHolder -> itemTouchHelper.startDrag(viewHolder) }
        )
        binding.thumbnailList.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.thumbnailList.adapter = imageAdapter

        itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                return makeMovementFlags(
                    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
                    0
                )
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                imageAdapter.moveImage(from, to)
                images.add(to, images.removeAt(from))
                updateSeamsAfterReorder()
                scheduleRender()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.7f
                } else {
                    viewHolder?.itemView?.alpha = 1.0f
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1.0f
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.thumbnailList)

        binding.addImagesBtn.setOnClickListener { pickImages() }
        binding.exportBtn.setOnClickListener { exportToGallery() }
        binding.previewBtn.setOnClickListener { showPreviewDialog() }

        binding.orientationToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            isVertical = checkedId == R.id.orientationVertical
            rebuildSeamSliders()
            scheduleRender()
        }

        binding.imageHeader.setOnClickListener {
            imageContentExpanded = !imageContentExpanded
            binding.imageContent.visibility =
                if (imageContentExpanded) View.VISIBLE else View.GONE
            binding.imageChevron.text = if (imageContentExpanded) "\u25BE" else "\u25B8"
        }

        binding.seamHeader.setOnClickListener {
            seamContentExpanded = !seamContentExpanded
            binding.seamContent.visibility =
                if (seamContentExpanded) View.VISIBLE else View.GONE
            binding.seamChevron.text = if (seamContentExpanded) "\u25BE" else "\u25B8"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(renderRunnable)
        images.forEach { it.bitmap?.recycle() }
        previewBitmap?.recycle()
    }

    private fun pickImages() {
        pickImagesLauncher.launch("image/*")
    }

    private fun addImages(uris: List<Uri>) {
        for (uri in uris) {
            val bitmap = decodeSampledBitmap(uri, maxDim = 1024)
            if (bitmap != null) {
                images.add(StitchImage(uri, bitmap))
            }
        }
        while (seamOffsets.size < images.size - 1) {
            seamOffsets.add(0)
        }
        while (seamOffsets.size > maxOf(0, images.size - 1)) {
            seamOffsets.removeAt(seamOffsets.lastIndex)
        }
        imageAdapter.notifyDataSetChanged()
        updateUIState()
        rebuildSeamSliders()
        scheduleRender()
    }

    private fun removeImage(index: Int) {
        if (index < 0 || index >= images.size) return
        images[index].bitmap?.recycle()
        images.removeAt(index)
        if (seamOffsets.isNotEmpty() && images.size > 0) {
            if (index >= seamOffsets.size) {
                seamOffsets.removeAt(seamOffsets.lastIndex)
            } else if (index > 0) {
                seamOffsets.removeAt(index - 1)
            } else {
                seamOffsets.removeAt(0)
            }
        }
        if (images.size <= 1) seamOffsets.clear()
        imageAdapter.notifyDataSetChanged()
        updateUIState()
        rebuildSeamSliders()
        scheduleRender()
    }

    private fun updateSeamsAfterReorder() {
        seamOffsets.clear()
        for (i in 0 until maxOf(0, images.size - 1)) {
            seamOffsets.add(0)
        }
        rebuildSeamSliders()
    }

    private fun updateUIState() {
        val count = images.size
        binding.imageCountLabel.text = count.toString()
        binding.emptyHint.visibility = if (count == 0) View.VISIBLE else View.GONE
        binding.orientationCard.visibility = if (count >= 2) View.VISIBLE else View.GONE
        binding.seamCard.visibility = if (count >= 2) View.VISIBLE else View.GONE
        binding.previewCard.visibility = if (count > 0) View.VISIBLE else View.GONE
        binding.exportBtn.isEnabled = count > 0
        binding.previewBtn.isEnabled = count > 0
    }

    private fun rebuildSeamSliders() {
        binding.seamSliders.removeAllViews()
        if (images.size < 2) return

        val maxOffsetPx = (200 * resources.displayMetrics.density).toInt()
        val density = resources.displayMetrics.density

        for (i in 0 until images.size - 1) {
            val offset = seamOffsets.getOrElse(i) { 0 }
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, (8 * density).toInt())
            }

            val label = TextView(this).apply {
                text = "接缝 ${i + 1}（图 ${i + 1} ↔ 图 ${i + 2}）"
                textSize = 12f
                setTextColor(Color.parseColor("#49454F"))
                setPadding(0, 0, 0, (4 * density).toInt())
            }
            container.addView(label)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val valueLabel = TextView(this).apply {
                text = formatOffset(offset)
                textSize = 12f
                setTextColor(Color.parseColor("#1A73E8"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = (8 * density).toInt() }
            }

            val seekBar = SeekBar(this).apply {
                max = maxOffsetPx * 2
                progress = offset + maxOffsetPx
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        if (!fromUser) return
                        val actualOffset = progress - maxOffsetPx
                        if (i < seamOffsets.size) {
                            seamOffsets[i] = actualOffset
                        }
                        valueLabel.text = formatOffset(actualOffset)
                        scheduleRender()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            row.addView(seekBar)
            row.addView(valueLabel)

            container.addView(row)
            binding.seamSliders.addView(container)
        }
    }

    private fun formatOffset(px: Int): String {
        val dp = (px / resources.displayMetrics.density).toInt()
        return if (dp >= 0) "+${dp}dp" else "${dp}dp"
    }

    private fun scheduleRender() {
        handler.removeCallbacks(renderRunnable)
        handler.postDelayed(renderRunnable, debounceDelay)
    }

    private fun renderPreview() {
        if (images.isEmpty()) {
            previewBitmap = null
            binding.previewImage.setImageBitmap(null)
            return
        }

        val density = resources.displayMetrics.density
        // Account for root padding (16dp*2) + card padding (12dp*2)
        val previewMaxWidth = (resources.displayMetrics.widthPixels - 56 * density).toInt()

        val bitmaps = images.mapNotNull { it.bitmap }
        if (bitmaps.size != images.size) return

        val result: Bitmap
        if (isVertical) {
            // Scale all images to same width
            val scaledBitmaps = bitmaps.map { bmp ->
                val scale = previewMaxWidth.toFloat() / bmp.width.toFloat()
                val newH = (bmp.height * scale).toInt()
                Bitmap.createScaledBitmap(bmp, previewMaxWidth, newH, true)
            }

            var totalHeight = 0
            for (i in scaledBitmaps.indices) {
                totalHeight += scaledBitmaps[i].height
                if (i < seamOffsets.size) totalHeight += seamOffsets[i]
            }
            if (totalHeight <= 0) return

            result = Bitmap.createBitmap(previewMaxWidth, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            canvas.drawColor(Color.WHITE)

            var y = 0f
            for (i in scaledBitmaps.indices) {
                canvas.drawBitmap(scaledBitmaps[i], 0f, y, null)
                y += scaledBitmaps[i].height
                if (i < seamOffsets.size) {
                    y += seamOffsets[i]
                }
            }
        } else {
            // Scale all images to same height (use first image's aspect to determine height)
            val maxH = bitmaps.minOf { it.height }
            val scaledBitmaps = bitmaps.map { bmp ->
                val scale = maxH.toFloat() / bmp.height.toFloat()
                val newW = (bmp.width * scale).toInt()
                Bitmap.createScaledBitmap(bmp, newW, maxH, true)
            }

            var totalWidth = 0
            for (i in scaledBitmaps.indices) {
                totalWidth += scaledBitmaps[i].width
                if (i < seamOffsets.size) totalWidth += seamOffsets[i]
            }
            if (totalWidth <= 0) return

            result = Bitmap.createBitmap(totalWidth, maxH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            canvas.drawColor(Color.WHITE)

            var x = 0f
            for (i in scaledBitmaps.indices) {
                canvas.drawBitmap(scaledBitmaps[i], x, 0f, null)
                x += scaledBitmaps[i].width
                if (i < seamOffsets.size) {
                    x += seamOffsets[i]
                }
            }
        }

        previewBitmap = result
        binding.previewImage.setImageBitmap(result)
    }

    private fun showPreviewDialog() {
        val bitmap = previewBitmap
        if (bitmap == null) {
            Toast.makeText(this, R.string.stitch_no_images, Toast.LENGTH_SHORT).show()
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
        if (images.isEmpty()) {
            Toast.makeText(this, R.string.stitch_no_images, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val density = resources.displayMetrics.density
            val exportWidth = if (isVertical) {
                images.mapNotNull { it.originalWidth }.maxOrNull() ?: 1080
            } else {
                images.sumOf { it.originalWidth ?: 1080 } + seamOffsets.sum()
                    .coerceIn(1080, 10000)
            }

            // Reload full-resolution bitmaps for export
            val fullBitmaps = images.map { img ->
                val bmp = decodeSampledBitmap(img.uri, maxDim = 4096)
                img.originalWidth = bmp?.width
                img.originalHeight = bmp?.height
                bmp
            }
            if (fullBitmaps.any { it == null }) {
                Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show()
                fullBitmaps.forEach { it?.recycle() }
                return
            }
            val bitmaps = fullBitmaps.mapNotNull { it }

            val result: Bitmap
            if (isVertical) {
                val maxW = bitmaps.maxOf { it.width }
                val scaledBitmaps = bitmaps.map { bmp ->
                    val scale = maxW.toFloat() / bmp.width.toFloat()
                    val newH = (bmp.height * scale).toInt()
                    Bitmap.createScaledBitmap(bmp, maxW, newH, true)
                }

                var totalHeight = 0
                for (i in scaledBitmaps.indices) {
                    totalHeight += scaledBitmaps[i].height
                    if (i < seamOffsets.size) totalHeight += seamOffsets[i]
                }

                result = Bitmap.createBitmap(maxW, totalHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(result)
                canvas.drawColor(Color.WHITE)

                var y = 0f
                for (i in scaledBitmaps.indices) {
                    canvas.drawBitmap(scaledBitmaps[i], 0f, y, null)
                    y += scaledBitmaps[i].height
                    if (i < seamOffsets.size) y += seamOffsets[i]
                }
                scaledBitmaps.forEach { if (it !== result) it.recycle() }
            } else {
                val maxH = bitmaps.maxOf { it.height }
                val scaledBitmaps = bitmaps.map { bmp ->
                    val scale = maxH.toFloat() / bmp.height.toFloat()
                    val newW = (bmp.width * scale).toInt()
                    Bitmap.createScaledBitmap(bmp, newW, maxH, true)
                }

                var totalWidth = 0
                for (i in scaledBitmaps.indices) {
                    totalWidth += scaledBitmaps[i].width
                    if (i < seamOffsets.size) totalWidth += seamOffsets[i]
                }

                result = Bitmap.createBitmap(totalWidth, maxH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(result)
                canvas.drawColor(Color.WHITE)

                var x = 0f
                for (i in scaledBitmaps.indices) {
                    canvas.drawBitmap(scaledBitmaps[i], x, 0f, null)
                    x += scaledBitmaps[i].width
                    if (i < seamOffsets.size) x += seamOffsets[i]
                }
                scaledBitmaps.forEach { if (it !== result) it.recycle() }
            }

            val filename = "ankits_stitch_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/Ankits"
                    )
                }
                val uri =
                    contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { out ->
                        result.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val path = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES
                ).toString() + "/Ankits/$filename"
                java.io.File(path).parentFile?.mkdirs()
                java.io.FileOutputStream(path).use { out ->
                    result.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }

            result.recycle()
            fullBitmaps.forEach { it?.recycle() }
            Toast.makeText(this, R.string.export_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "${getString(R.string.export_failed)}: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun decodeSampledBitmap(uri: Uri, maxDim: Int): Bitmap? {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        val srcW = options.outWidth
        val srcH = options.outHeight
        if (srcW <= 0 || srcH <= 0) return null

        val maxSource = maxOf(srcW, srcH)
        var sampleSize = 1
        while (maxSource / sampleSize > maxDim) {
            sampleSize *= 2
        }

        options.inJustDecodeBounds = false
        options.inSampleSize = sampleSize

        return contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
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
}

data class StitchImage(
    val uri: Uri,
    var bitmap: Bitmap?,
    var originalWidth: Int? = null,
    var originalHeight: Int? = null
)

class StitchImageAdapter(
    private val onRemove: (Int) -> Unit,
    private val onDragStart: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<StitchImageAdapter.ViewHolder>() {

    private val images = mutableListOf<StitchImage>()

    fun setImages(newImages: List<StitchImage>) {
        images.clear()
        images.addAll(newImages)
        notifyDataSetChanged()
    }

    fun moveImage(from: Int, to: Int) {
        images.add(to, images.removeAt(from))
        notifyItemMoved(from, to)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stitch_thumbnail, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val image = images[position]
        holder.thumbnail.setImageBitmap(image.bitmap)
        holder.removeBtn.setOnClickListener { onRemove(position) }
        holder.itemView.setOnLongClickListener {
            onDragStart(holder)
            true
        }
    }

    override fun getItemCount() = images.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.thumbnail)
        val removeBtn: ImageView = view.findViewById(R.id.removeBtn)
    }
}
