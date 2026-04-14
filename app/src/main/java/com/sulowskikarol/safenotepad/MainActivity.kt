package com.sulowskikarol.safenotepad

import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputEditText
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.text.Charsets

class MainActivity : AppCompatActivity() {

    private lateinit var noteEditText: TextInputEditText
    private lateinit var saveButton: Button
    private lateinit var loadButton: Button
    private lateinit var clearButton: Button
    private lateinit var exportButton: Button
    private lateinit var importButton: Button

    private val cryptoManager = CryptoManager()
    private val fileName = "secret_note.enc"

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            exportToFile(uri)
        } else {
            pendingPassword.fill('\u0000')
        }
    }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { showPasswordDialog(isExport = false, uri = it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        noteEditText = findViewById(R.id.noteEditText)
        saveButton = findViewById(R.id.saveButton)
        loadButton = findViewById(R.id.loadButton)
        clearButton = findViewById(R.id.clearButton)
        exportButton = findViewById(R.id.exportButton)
        importButton = findViewById(R.id.importButton)

        saveButton.setOnClickListener {
            val text = noteEditText.text.toString()
            if (text.isBlank()) {
                Toast.makeText(this, R.string.toast_empty_note, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showBiometricPrompt {
                try {
                    val bytes = text.toByteArray(Charsets.UTF_8)
                    val encryptedBytes = cryptoManager.encrypt(bytes)
                    val file = File(filesDir, fileName)
                    FileOutputStream(file).use { it.write(encryptedBytes) }
                    Toast.makeText(this, R.string.toast_save_success, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, R.string.toast_save_error, Toast.LENGTH_SHORT).show()
                }
            }
        }

        loadButton.setOnClickListener {
            val file = File(filesDir, fileName)
            if (!file.exists()) {
                Toast.makeText(this, R.string.toast_no_note, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showBiometricPrompt {
                try {
                    val encryptedBytes = FileInputStream(file).use { it.readBytes() }
                    val decryptedBytes = cryptoManager.decrypt(encryptedBytes)
                    noteEditText.setText(String(decryptedBytes, Charsets.UTF_8))
                    Toast.makeText(this, R.string.toast_load_success, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, R.string.toast_load_error, Toast.LENGTH_SHORT).show()
                }
            }
        }

        clearButton.setOnClickListener {
            showBiometricPrompt {
                File(filesDir, fileName).takeIf { it.exists() }?.delete()
                cryptoManager.deleteKey()
                noteEditText.setText("")
                Toast.makeText(this, R.string.toast_clear_success, Toast.LENGTH_SHORT).show()
            }
        }

        exportButton.setOnClickListener {
            if (noteEditText.text.toString().isBlank()) {
                Toast.makeText(this, R.string.toast_empty_note, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showPasswordDialog(isExport = true)
        }

        importButton.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("application/octet-stream", "*/*"))
        }
    }

    private fun showBiometricPrompt(onSuccess: () -> Unit) {
        val authenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        val biometricManager = BiometricManager.from(this)
        
        when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                val executor = ContextCompat.getMainExecutor(this)
                val biometricPrompt = BiometricPrompt(this, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            onSuccess()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            Toast.makeText(this@MainActivity, R.string.toast_auth_error, Toast.LENGTH_SHORT).show()
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            Toast.makeText(this@MainActivity, R.string.toast_auth_error, Toast.LENGTH_SHORT).show()
                        }
                    })

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(getString(R.string.biometric_title))
                    .setSubtitle(getString(R.string.biometric_subtitle))
                    .setAllowedAuthenticators(authenticators)
                    .build()

                biometricPrompt.authenticate(promptInfo)
            }
            else -> {
                // Device cannot authenticate (no lock screen or biometric hardware)
                Toast.makeText(this, R.string.toast_auth_unavailable, Toast.LENGTH_LONG).show()
            }
        }
    }

    private var pendingPassword = charArrayOf()

    private fun showPasswordDialog(isExport: Boolean, uri: Uri? = null) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = getString(R.string.dialog_password_hint)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_password_title)
            .setView(input)
            .setPositiveButton(R.string.dialog_ok, null)
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val editable = input.text
                if (editable.isNullOrEmpty()) {
                    Toast.makeText(this, R.string.toast_empty_password, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                val password = CharArray(editable.length)
                for (i in 0 until editable.length) {
                    password[i] = editable[i]
                }
                
                if (isExport) {
                    pendingPassword = password
                    createDocumentLauncher.launch("note_export.enc")
                } else {
                    uri?.let { importFromFile(it, password) }
                }
                editable.clear()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun exportToFile(uri: Uri) {
        try {
            val text = noteEditText.text.toString()
            val encryptedBytes = cryptoManager.encryptWithPassword(
                text.toByteArray(Charsets.UTF_8),
                pendingPassword
            )
            contentResolver.openOutputStream(uri)?.use { it.write(encryptedBytes) }
            Toast.makeText(this, R.string.toast_export_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.toast_save_error, Toast.LENGTH_SHORT).show()
        } finally {
            pendingPassword.fill('\u0000')
        }
    }

    private fun importFromFile(uri: Uri, password: CharArray) {
        try {
            val encryptedBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (encryptedBytes != null) {
                val decryptedBytes = cryptoManager.decryptWithPassword(encryptedBytes, password)
                noteEditText.setText(String(decryptedBytes, Charsets.UTF_8))
                Toast.makeText(this, R.string.toast_import_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.toast_load_error, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.toast_load_error, Toast.LENGTH_SHORT).show()
        } finally {
            password.fill('\u0000')
        }
    }
}