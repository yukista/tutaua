package com.yukista.tutaua.games

import android.net.Uri
import java.util.Locale

enum class GameSystem(val title: String, val coreLibrary: String, val fileExtension: String, val extensions: Set<String>) {
    NES("Nintendo NES", "libfceumm_libretro_android.so", "nes", setOf("nes", "fds", "unf", "unif")),
    SNES("Super Nintendo", "libsnes9x_libretro_android.so", "sfc", setOf("sfc", "smc", "fig", "swc")),
    GAME_BOY("Game Boy", "libgambatte_libretro_android.so", "gb", setOf("gb", "gbc")),
    GAME_BOY_ADVANCE("Game Boy Advance", "libmgba_libretro_android.so", "gba", setOf("gba")),
    SEGA("Sega 8/16 bits", "libgenesis_plus_gx_libretro_android.so", "md", setOf("sms", "gg", "sg", "gen", "md", "smd")),
    PC_ENGINE("PC Engine", "libmednafen_pce_fast_libretro_android.so", "pce", setOf("pce"));

    companion object {
        fun fromName(name: String): GameSystem? {
            val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            return entries.firstOrNull { extension in it.extensions }
        }
    }
}

data class GameEntry(val name: String, val uri: Uri, val system: GameSystem, val zipEntry: String? = null)
