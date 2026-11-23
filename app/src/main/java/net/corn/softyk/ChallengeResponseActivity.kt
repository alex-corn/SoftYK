package net.corn.softyk

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class ChallengeResponseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val challenge = intent.getByteArrayExtra("challenge")
        if (challenge == null || challenge.isEmpty()) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        if (!KeystoreHelper.hasKey()) {
            AlertDialog.Builder(this)
                .setTitle("SoftYK")
                .setMessage("No HMAC secret configured. Open SoftYK and configure your HMAC secret to continue.")
                .setPositiveButton("OK") { _, _ ->
                    setResult(RESULT_CANCELED)
                    finish()
                }
                .setOnCancelListener {
                    setResult(RESULT_CANCELED)
                    finish()
                }
                .show()
            return
        }

        val callingPkg = callingPackage

        // Skip dialog for previously approved apps
        if (callingPkg != null && ApprovedApps.isApproved(this, callingPkg)) {
            respond(challenge)
            return
        }

        val requesterLabel = callingPkg?.resolveLabel() ?: "An unknown app"

        showConfirmDialog(challenge, callingPkg, requesterLabel)
    }

    private fun showConfirmDialog(challenge: ByteArray, callingPkg: String?, requesterLabel: String) {
        val dp = resources.displayMetrics.density

        // Build a custom view: message text + "don't ask again" checkbox
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (20 * dp).toInt(), (24 * dp).toInt(), (8 * dp).toInt())
        }

        val messageView = TextView(this).apply {
            text = "Allow $requesterLabel to perform a challenge-response operation?"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 0, 0, (12 * dp).toInt())
        }

        val checkBox = CheckBox(this).apply {
            text = "Always allow this app"
        }

        container.addView(messageView)
        container.addView(checkBox)

        AlertDialog.Builder(this)
            .setTitle("SoftYK")
            .setView(container)
            .setPositiveButton("Allow") { _, _ ->
                if (checkBox.isChecked && callingPkg != null) {
                    ApprovedApps.approve(applicationContext, callingPkg)
                }
                respond(challenge)
            }
            .setNegativeButton("Deny") { _, _ ->
                setResult(RESULT_CANCELED)
                finish()
            }
            .setOnCancelListener {
                setResult(RESULT_CANCELED)
                finish()
            }
            .show()
    }

    private fun respond(challenge: ByteArray) {
        try {
            val response = KeystoreHelper.computeHmac(this, challenge)
            setResult(RESULT_OK, Intent().apply { putExtra("response", response) })
        } catch (e: Exception) {
            Log.e("SoftYK", "respond failed", e);
            setResult(RESULT_CANCELED)
        }
        finish()
    }

    private fun String.resolveLabel(): String = try {
        val info = packageManager.getApplicationInfo(this, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        this
    }
}
