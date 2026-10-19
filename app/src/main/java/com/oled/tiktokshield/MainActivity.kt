package com.oled.tiktokshield

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 120, 60, 60)
        }

        val btnOverlay = Button(this).apply {
            text = "1. Поверх других окон"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }

        val btnAccess = Button(this).apply {
            text = "2. Включить спец. возможности"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val btnEdit = Button(this).apply {
            text = "3. Настроить размер и место (Вкл/Выкл)"
            setOnClickListener {
                val intent = Intent(this@MainActivity, TikTokDetectService::class.java).apply {
                    action = "TOGGLE_EDIT"
                }
                startService(intent)
                Toast.makeText(this@MainActivity, "Тяните плашку для сдвига, а желтый угол ⇲ для размера!", Toast.LENGTH_LONG).show()
            }
        }

        layout.addView(btnOverlay)
        layout.addView(btnAccess)
        layout.addView(btnEdit)
        setContentView(layout)
    }
}
