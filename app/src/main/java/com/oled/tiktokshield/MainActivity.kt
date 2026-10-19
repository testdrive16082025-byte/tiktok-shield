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
            text = "1. Дать доступ: Поверх других окон"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }

        val btnAccess = Button(this).apply {
            text = "2. Дать доступ: Спец. возможности"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                Toast.makeText(this@MainActivity, "Найдите 'TikTok OLED Shield' и включите", Toast.LENGTH_LONG).show()
            }
        }

        val btnEdit = Button(this).apply {
            text = "3. Настроить положение (Вкл/Выкл режим перетаскивания)"
            setOnClickListener {
                val intent = Intent(this@MainActivity, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_TOGGLE_EDIT
                }
                startService(intent)
            }
        }

        layout.addView(btnOverlay)
        layout.addView(btnAccess)
        layout.addView(btnEdit)
        setContentView(layout)
    }
}
