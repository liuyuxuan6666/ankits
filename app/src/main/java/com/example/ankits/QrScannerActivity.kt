package com.example.ankits

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ankits.databinding.ActivityQrScannerBinding
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.net.URI

class QrScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrScannerBinding
    private var decodedText: String = ""

    private val scanLauncher =
        registerForActivityResult(ScanContract()) { result ->
            if (result.contents != null) {
                decodedText = result.contents
                showResult(decodedText)
            } else {
                Toast.makeText(this, R.string.qr_scan_failed, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleWindowInsets()

        binding.backBtn.setOnClickListener { finish() }
        binding.copyBtn.setOnClickListener { copyResult() }
        binding.scanBtn.setOnClickListener { startScan() }
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

    private fun startScan() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("将二维码置于取景框内")
            setBeepEnabled(false)
            setOrientationLocked(true)
            setCaptureActivity(CaptureActivityPortrait::class.java)
        }
        scanLauncher.launch(options)
    }

    private fun showResult(text: String) {
        binding.emptyState.visibility = android.view.View.GONE
        binding.resultCard.visibility = android.view.View.VISIBLE
        binding.resultText.text = text

        val urlInfo = parseUrl(text)
        if (urlInfo != null) {
            binding.urlWarningSection.visibility = android.view.View.VISIBLE
            binding.urlBreakdownSection.visibility = android.view.View.VISIBLE
            binding.urlSchemeValue.text = urlInfo.scheme
            binding.urlHostValue.text = urlInfo.host
            binding.urlPathValue.text = urlInfo.path
            binding.urlQueryValue.text = urlInfo.query
        } else {
            binding.urlWarningSection.visibility = android.view.View.GONE
            binding.urlBreakdownSection.visibility = android.view.View.GONE
        }

        binding.scanBtn.text = getString(R.string.qr_btn_scan_again)
    }

    private data class UrlInfo(
        val scheme: String,
        val host: String,
        val path: String,
        val query: String
    )

    private fun parseUrl(text: String): UrlInfo? {
        val lower = text.trim().lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return null
        }
        return try {
            val uri = URI(text.trim())
            if (uri.host == null) return null
            UrlInfo(
                scheme = uri.scheme ?: "",
                host = uri.host ?: "",
                path = if (uri.path.isNullOrEmpty()) "/" else uri.path,
                query = uri.query ?: "(无)"
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun copyResult() {
        if (decodedText.isEmpty()) {
            Toast.makeText(this, R.string.qr_scan_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("qr_result", decodedText))
        Toast.makeText(this, R.string.qr_copy_success, Toast.LENGTH_SHORT).show()
    }
}
