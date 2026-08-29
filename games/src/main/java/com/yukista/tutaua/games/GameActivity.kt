package com.yukista.tutaua.games

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ShaderConfig
import java.io.File
import java.security.MessageDigest

class GameActivity : ComponentActivity() {
    private lateinit var retroView: GLRetroView
    private lateinit var saveFile: File
    private var loaded = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state); immersive()
        val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse) ?: return finish()
        val system = intent.getStringExtra(EXTRA_SYSTEM)?.let { runCatching { GameSystem.valueOf(it) }.getOrNull() } ?: return finish()
        val bytes = runCatching { RomLibrary.readGame(this, uri, intent.getStringExtra(EXTRA_ZIP_ENTRY)) }.getOrElse {
            Toast.makeText(this, R.string.game_error, Toast.LENGTH_LONG).show(); return finish()
        }
        val id = MessageDigest.getInstance("SHA-256").digest(bytes).take(12).joinToString("") { "%02x".format(it) }
        val romFile = File(cacheDir, "roms/$id.${system.fileExtension}").apply {
            parentFile?.mkdirs()
            if (!isFile || length() != bytes.size.toLong()) writeBytes(bytes)
        }
        val saves = File(filesDir, "saves").apply { mkdirs() }
        saveFile = File(saves, "$id.srm")
        val data = GLRetroViewData(this).apply {
            coreFilePath = system.coreLibrary
            gameFilePath = romFile.absolutePath
            systemDirectory = File(filesDir, "system").apply { mkdirs() }.absolutePath
            savesDirectory = saves.absolutePath
            saveRAMState = saveFile.takeIf { it.isFile }?.readBytes()
            shader = ShaderConfig.Sharp
            preferLowLatencyAudio = true
            rumbleEventsEnabled = true
            skipDuplicateFrames = true
        }
        retroView = GLRetroView(this, data)
        lifecycle.addObserver(retroView)
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(retroView, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        })
        loaded = true
        Toast.makeText(this, R.string.tx5_controls, Toast.LENGTH_LONG).show()
    }

    override fun onPause() {
        if (loaded) runCatching { retroView.serializeSRAM(false) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { runCatching { saveFile.writeBytes(it) } }
        super.onPause()
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return true
        if (loaded) mappedGameKey(keyCode)?.let { retroView.sendKeyEvent(event.action, it) }
        return true
    }
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true }
        if (loaded) mappedGameKey(keyCode)?.let { retroView.sendKeyEvent(event.action, it) }
        return true
    }
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!loaded) return super.onGenericMotionEvent(event)
        retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y), 0)
        retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y), 0)
        return true
    }
    private fun immersive() { window.decorView.systemUiVisibility = 5894 }
    private fun mappedGameKey(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> KeyEvent.KEYCODE_BUTTON_A
        KeyEvent.KEYCODE_MENU -> KeyEvent.KEYCODE_BUTTON_B
        KeyEvent.KEYCODE_F2 -> KeyEvent.KEYCODE_BUTTON_SELECT
        KeyEvent.KEYCODE_F4 -> KeyEvent.KEYCODE_BUTTON_START
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT -> keyCode
        else -> null
    }

    companion object {
        const val EXTRA_NAME = "name"
        const val EXTRA_URI = "uri"
        const val EXTRA_SYSTEM = "system"
        const val EXTRA_ZIP_ENTRY = "zip_entry"
    }
}
