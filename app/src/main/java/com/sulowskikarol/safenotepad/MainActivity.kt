package com.sulowskikarol.safenotepad

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputEditText
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var noteEditText: TextInputEditText
    private lateinit var saveButton: Button
    private lateinit var loadButton: Button
    private lateinit var clearButton: Button

    private val cryptoManager = CryptoManager()
    private val fileName = "secret_note.enc"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Inicjalizacja widoków
        noteEditText = findViewById(R.id.noteEditText)
        saveButton = findViewById(R.id.saveButton)
        loadButton = findViewById(R.id.loadButton)
        clearButton = findViewById(R.id.clearButton)

        // Obsługa kliknięć
        saveButton.setOnClickListener {
            val text = noteEditText.text.toString()
            if (text.isBlank()) {
                Toast.makeText(this, "Wprowadź tekst notatki", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                val bytes = text.toByteArray()
                val encryptedBytes = cryptoManager.encrypt(bytes)

                val file = File(filesDir, fileName)
                FileOutputStream(file).use { fos ->
                    fos.write(encryptedBytes)
                }
                Toast.makeText(this, "Notatka zapisana pomyślnie", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Błąd zapisu: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        loadButton.setOnClickListener {
            val file = File(filesDir, fileName)
            if (!file.exists()) {
                Toast.makeText(this, "Brak zapisanej notatki", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                val encryptedBytes = FileInputStream(file).use { fis ->
                    fis.readBytes()
                }
                val decryptedBytes = cryptoManager.decrypt(encryptedBytes)
                val text = String(decryptedBytes)
                noteEditText.setText(text)
                Toast.makeText(this, "Notatka wczytana", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Błąd odczytu/deszyfrowania: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        clearButton.setOnClickListener {
            val file = File(filesDir, fileName)
            if (file.exists()) {
                file.delete()
            }
            cryptoManager.deleteKey()
            noteEditText.setText("")
            Toast.makeText(this, "Dane zostały wyczyszczone", Toast.LENGTH_SHORT).show()
        }
    }
}