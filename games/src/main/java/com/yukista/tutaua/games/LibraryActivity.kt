package com.yukista.tutaua.games

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import java.util.concurrent.Executors

class LibraryActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var content: LinearLayout
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadLibrary() else renderPermissionRequired()
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        immersive()
        renderShell()
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) loadLibrary()
        else permissionRequest.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    override fun onResume() { super.onResume(); immersive() }
    override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }

    private fun renderShell() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(64), dp(36), dp(64), dp(28))
            setBackgroundColor(Color.rgb(7, 10, 18))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text(getString(R.string.title), 32, Color.WHITE, true))
        header.addView(text("   ${getString(R.string.subtitle)}", 15, Color.rgb(165, 150, 235), true))
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(button(getString(R.string.rescan)) { loadLibrary() }, LinearLayout.LayoutParams(dp(230), dp(50)))
        page.addView(header, LinearLayout.LayoutParams(-1, dp(58)))
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(content) }
        val params = LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(22) }
        page.addView(scroll, params)
        setContentView(page)
    }

    private fun renderPermissionRequired() {
        content.removeAllViews()
        content.gravity = Gravity.CENTER
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(44), dp(36), dp(44), dp(36)); background = background(Color.rgb(19, 25, 39), Color.rgb(55, 64, 87), 18, 1)
        }
        val message = text(getString(R.string.permission_explanation), 18, Color.rgb(190, 198, 216), false).apply { gravity = Gravity.CENTER }
        panel.addView(message, LinearLayout.LayoutParams(dp(650), -2))
        val choose = button(getString(R.string.grant_access)) { permissionRequest.launch(Manifest.permission.READ_EXTERNAL_STORAGE) }
        panel.addView(choose, LinearLayout.LayoutParams(dp(330), dp(56)).apply { topMargin = dp(28) })
        content.addView(panel)
        choose.requestFocus()
    }

    private fun loadLibrary() {
        content.removeAllViews(); content.gravity = Gravity.CENTER
        content.addView(ProgressBar(this)); content.addView(text(getString(R.string.loading), 16, Color.LTGRAY, false))
        worker.execute {
            val games = runCatching { RomLibrary.scanAvailableStorage(this) }.getOrDefault(emptyList())
            runOnUiThread { renderGames(games) }
        }
    }

    private fun renderGames(games: List<GameEntry>) {
        content.removeAllViews(); content.gravity = Gravity.NO_GRAVITY
        if (games.isEmpty()) {
            content.gravity = Gravity.CENTER
            content.addView(text(getString(R.string.empty_library), 19, Color.rgb(175, 184, 205), false).apply { gravity = Gravity.CENTER })
            return
        }
        var first: View? = null
        games.groupBy { it.system }.forEach { (system, systemGames) ->
            val heading = text(system.title.uppercase(), 14, Color.rgb(176, 157, 255), true).apply { letterSpacing = .12f }
            content.addView(heading, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22); bottomMargin = dp(10) })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false }
            systemGames.forEach { game ->
                val tile = gameTile(game)
                if (first == null) first = tile
                row.addView(tile, LinearLayout.LayoutParams(dp(255), dp(110)).apply { rightMargin = dp(14) })
            }
            content.addView(row)
        }
        first?.requestFocus()
    }

    private fun gameTile(game: GameEntry): View = TextView(this).apply {
        text = game.name; textSize = 17f; setTextColor(Color.WHITE); setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(12)); isFocusable = true; isClickable = true
        background = background(Color.rgb(22, 28, 42), Color.rgb(54, 64, 87), 14, 1)
        setOnFocusChangeListener { _, focused ->
            background = background(if (focused) Color.rgb(38, 44, 65) else Color.rgb(22, 28, 42), if (focused) Color.WHITE else Color.rgb(54, 64, 87), 14, if (focused) 3 else 1)
        }
        setOnClickListener {
            startActivity(Intent(this@LibraryActivity, GameActivity::class.java)
                .putExtra(GameActivity.EXTRA_NAME, game.name).putExtra(GameActivity.EXTRA_URI, game.uri.toString())
                .putExtra(GameActivity.EXTRA_SYSTEM, game.system.name).putExtra(GameActivity.EXTRA_ZIP_ENTRY, game.zipEntry))
        }
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 13f; setTextColor(Color.WHITE); setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        background = background(Color.rgb(104, 76, 228), Color.rgb(145, 120, 255), 10, 1); setOnClickListener { action() }
    }
    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }
    private fun background(fill: Int, stroke: Int, radius: Int, width: Int) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat(); setStroke(dp(width), stroke)
    }
    private fun immersive() { window.decorView.systemUiVisibility = 5894 }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
