package com.example.ankits

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ankits.databinding.ActivityOcrBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.io.File

class OcrActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOcrBinding
    private lateinit var recognizer: com.google.mlkit.vision.text.TextRecognizer
    private var cameraImageUri: Uri? = null
    private var rawOcrText: String = ""
    private var isMerged: Boolean = false

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { processImageUri(it) }
        }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = cameraImageUri
            if (success && uri != null) {
                processImageUri(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOcrBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleWindowInsets()

        recognizer = TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build()
        )

        binding.backBtn.setOnClickListener { finish() }
        binding.settingsBtn.setOnClickListener { /* TODO */ }
        binding.galleryBtn.setOnClickListener { openGallery() }
        binding.cameraBtn.setOnClickListener { takePhoto() }
        binding.mergeBtn.setOnClickListener { toggleMerge() }
        binding.copyBtn.setOnClickListener { copyResult() }
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizer.close()
    }

    // --- Window insets ---

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

    // --- Image source actions ---

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun takePhoto() {
        val photoFile = File(
            cacheDir,
            "ocr_capture_${System.currentTimeMillis()}.jpg"
        )
        cameraImageUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            photoFile
        )
        cameraLauncher.launch(cameraImageUri!!)
    }

    // --- Image processing ---

    private fun processImageUri(uri: Uri) {
        val bitmap = decodeSampledBitmap(uri, maxDim = 2048)
        if (bitmap == null) {
            Toast.makeText(this, R.string.ocr_decode_failed, Toast.LENGTH_SHORT).show()
            return
        }

        binding.imagePreview.setImageBitmap(bitmap)
        binding.emptyState.visibility = android.view.View.GONE
        binding.imagePreview.visibility = android.view.View.VISIBLE

        runOcr(bitmap)
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

    // --- OCR ---

    private fun runOcr(bitmap: Bitmap) {
        showProgress(true)
        isMerged = false
        binding.mergeBtn.setIconResource(R.drawable.ic_merge)
        binding.mergeBtn.contentDescription = getString(R.string.ocr_btn_merge)

        val inputImage = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { text ->
                showProgress(false)
                rawOcrText = extractText(text)
                if (rawOcrText.isEmpty()) {
                    binding.resultText.text = getString(R.string.ocr_no_text)
                } else {
                    binding.resultText.text = rawOcrText
                }
                binding.resultScroll.post {
                    binding.resultScroll.scrollTo(0, 0)
                }
            }
            .addOnFailureListener {
                showProgress(false)
                Toast.makeText(this, R.string.ocr_recognize_failed, Toast.LENGTH_SHORT).show()
            }
    }

    private fun extractText(result: Text): String {
        val sb = StringBuilder()
        for (block in result.textBlocks) {
            for (line in block.lines) {
                sb.appendLine(line.text)
            }
            sb.appendLine()
        }
        return sb.toString().trim()
    }

    private fun showProgress(visible: Boolean) {
        binding.progressOverlay.visibility =
            if (visible) android.view.View.VISIBLE else android.view.View.GONE
    }

    // --- Merge ---

    private fun toggleMerge() {
        val text = binding.resultText.text?.toString() ?: ""
        if (text.isEmpty() || text == getString(R.string.ocr_no_text)) return

        if (isMerged) {
            isMerged = false
            binding.resultText.text = rawOcrText
            binding.mergeBtn.setIconResource(R.drawable.ic_merge)
            binding.mergeBtn.contentDescription = getString(R.string.ocr_btn_merge)
        } else {
            isMerged = true
            val merged = rawOcrText.replace("\n", "")
            binding.resultText.text = merged
            binding.mergeBtn.setIconResource(R.drawable.ic_merge)
            binding.mergeBtn.contentDescription = getString(R.string.ocr_btn_unmerge)
        }
    }

    // --- Copy ---

    private fun copyResult() {
        val text = binding.resultText.text?.toString() ?: ""
        if (text.isEmpty() || text == getString(R.string.ocr_no_text)) {
            Toast.makeText(this, R.string.ocr_no_text, Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ocr_result", text))
        Toast.makeText(this, R.string.ocr_copy_success, Toast.LENGTH_SHORT).show()
    }
}
