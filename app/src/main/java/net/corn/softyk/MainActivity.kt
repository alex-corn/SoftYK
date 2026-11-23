package net.corn.softyk

import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class MainActivity : AppCompatActivity() {

    private lateinit var tilHexSecret: TextInputLayout
    private lateinit var etHexSecret: TextInputEditText
    private lateinit var cbLt64: android.widget.CheckBox
    private lateinit var tvStatus: TextView
    private lateinit var tvBackend: TextView
    private lateinit var btnSave: Button
    private lateinit var btnClear: Button
    private lateinit var tvApprovedEmpty: TextView
    private lateinit var containerApproved: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById(R.id.toolbar))
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.scrollView)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        tilHexSecret      = findViewById(R.id.tilHexSecret)
        etHexSecret       = findViewById(R.id.etHexSecret)
        cbLt64            = findViewById(R.id.cbLt64)
        tvStatus          = findViewById(R.id.tvStatus)
        tvBackend         = findViewById(R.id.tvBackend)
        btnSave           = findViewById(R.id.btnSave)
        btnClear          = findViewById(R.id.btnClear)
        tvApprovedEmpty   = findViewById(R.id.tvApprovedEmpty)
        containerApproved = findViewById(R.id.containerApproved)

        etHexSecret.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = validateInput(s?.toString() ?: "")
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnSave.setOnClickListener { saveKey() }
        btnClear.setOnClickListener { confirmClear() }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // ── Input validation ──────────────────────────────────────────────────────

    private fun validateInput(raw: String) {
        val hex = raw.trim().replace(":", "").replace(" ", "")
        tilHexSecret.error = when {
            hex.isEmpty() -> null
            !hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' } -> "Invalid hex characters"
            else -> null
        }
        btnSave.isEnabled = hex.isNotEmpty() && hex.length == 40 &&
            hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
    }

    // ── Keystore operations ───────────────────────────────────────────────────

    private fun saveKey() {
        val hex = etHexSecret.text?.toString() ?: return
        try {
            val usedStrongBox = KeystoreHelper.importSecret(this, hex, cbLt64.isChecked)
            etHexSecret.setText("")
            tilHexSecret.error = null
            android.widget.Toast.makeText(this, "Key saved into KeyStore", android.widget.Toast.LENGTH_LONG).show()
            refresh()
        } catch (e: IllegalArgumentException) {
            tilHexSecret.error = e.message
        } catch (e: Exception) {
            tilHexSecret.error = "Failed to save: ${e.message}"
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Remove Key")
            .setMessage(
                "This will delete the stored HMAC secret. " +
                "You will need to import the secret again to use it with apps that need to access it."
            )
            .setPositiveButton("Remove") { _, _ ->
                KeystoreHelper.deleteKey()
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Approved apps ─────────────────────────────────────────────────────────

    private fun refreshApprovedApps() {
        containerApproved.removeAllViews()
        val approved = ApprovedApps.getAll(this)

        tvApprovedEmpty.visibility = if (approved.isEmpty()) View.VISIBLE else View.GONE

        val dp = resources.displayMetrics.density

        approved.sorted().forEach { pkg ->
            val label = pkg.resolveLabel()

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
            }

            val nameView = TextView(this).apply {
                text = label
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val revokeBtn = MaterialButton(
                this, null,
                com.google.android.material.R.attr.borderlessButtonStyle
            ).apply {
                text = "Revoke"
                setOnClickListener {
                    ApprovedApps.revoke(this@MainActivity, pkg)
                    refreshApprovedApps()
                }
            }

            row.addView(nameView)
            row.addView(revokeBtn)
            containerApproved.addView(row)
        }
    }

    // ── UI state ──────────────────────────────────────────────────────────────

    private fun refresh() {
        refreshStatus()
        refreshApprovedApps()
    }

    private fun refreshStatus() {
        val hasKey = KeystoreHelper.hasKey()
        if (hasKey) {
            tvStatus.setText(R.string.status_loaded)
            tvStatus.setTextColor(getColor(R.color.status_ok))
            tvBackend.setText(R.string.backend_hint)
            tvBackend.visibility = View.VISIBLE
        } else {
            tvStatus.setText(R.string.status_none)
            tvStatus.setTextColor(getColor(R.color.status_none))
            tvBackend.visibility = View.GONE
        }
        btnClear.isEnabled = hasKey
        btnSave.isEnabled = false

        findViewById<LinearLayout>(R.id.containerImport).visibility = if (hasKey) View.GONE else View.VISIBLE
        findViewById<LinearLayout>(R.id.containerDelete).visibility = if (hasKey) View.VISIBLE else View.GONE
    }

    private fun String.resolveLabel(): String = try {
        val info = packageManager.getApplicationInfo(this, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        this
    }
}
