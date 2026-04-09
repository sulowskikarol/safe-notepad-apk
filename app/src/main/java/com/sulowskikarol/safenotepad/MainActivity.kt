package com.sulowskikarol.safenotepad

import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
        uri?.let { exportToFile(it) }
    }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { showPasswordDialog(isExport = false, uri = it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        loadButton.setOnClickListener {
            val file = File(filesDir, fileName)
            if (!file.exists()) {
                Toast.makeText(this, R.string.toast_no_note, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                val encryptedBytes = FileInputStream(file).use { it.readBytes() }
                val decryptedBytes = cryptoManager.decrypt(encryptedBytes)
                noteEditText.setText(String(decryptedBytes, Charsets.UTF_8))
                Toast.makeText(this, R.string.toast_load_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, R.string.toast_load_error, Toast.LENGTH_SHORT).show()
            }
        }

        clearButton.setOnClickListener {
            File(filesDir, fileName).takeIf { it.exists() }?.delete()
            cryptoManager.deleteKey()
            noteEditText.setText("")
            Toast.makeText(this, R.string.toast_clear_success, Toast.LENGTH_SHORT).show()
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

    private var pendingPassword = charArrayOf()

    private fun showPasswordDialog(isExport: Boolean, uri: Uri? = null) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = getString(R.string.dialog_password_hint)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_password_title)
            .setView(input)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                val password = input.text.toString().toCharArray()
                if (password.isEmpty()) return@setPositiveButton
                
                if (isExport) {
                    pendingPassword = password
                    createDocumentLauncher.launch("note_export.enc")
                } else {
                    uri?.let { importFromFile(it, password) }
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
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
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.toast_load_error, Toast.LENGTH_SHORT).show()
        } finally {
            password.fill('\u0000')
        }
    }
}