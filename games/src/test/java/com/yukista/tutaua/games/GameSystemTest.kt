package com.yukista.tutaua.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameSystemTest {
    @Test fun detectsSupportedFormats() {
        assertEquals(GameSystem.NES, GameSystem.fromName("Mario.NES"))
        assertEquals(GameSystem.SNES, GameSystem.fromName("Zelda.sfc"))
        assertEquals(GameSystem.GAME_BOY, GameSystem.fromName("Tetris.gb"))
        assertEquals(GameSystem.GAME_BOY_ADVANCE, GameSystem.fromName("Advance.gba"))
        assertEquals(GameSystem.SEGA, GameSystem.fromName("Sonic.md"))
        assertEquals(GameSystem.PC_ENGINE, GameSystem.fromName("Game.pce"))
        assertNull(GameSystem.fromName("disc.iso"))
    }
}
