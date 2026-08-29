package com.yukista.tutaua.games

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

object RomLibrary {
    fun scanAvailableStorage(context: Context): List<GameEntry> {
        val roots = linkedSetOf<File>()
        roots += Environment.getExternalStorageDirectory()
        val storage = context.getSystemService(StorageManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 30) storage.storageVolumes.mapNotNullTo(roots) { it.directory }
        else File("/storage").listFiles()?.filterTo(roots) { it.isDirectory && it.name != "emulated" && it.name != "self" }
        val output = mutableListOf<GameEntry>()
        roots.filter { it.canRead() }.forEach { scanFile(it, output, 0) }
        return output.distinctBy { it.uri.toString() + (it.zipEntry ?: "") }
            .sortedWith(compareBy({ it.system.ordinal }, { it.name.lowercase() }))
    }

    private fun scanFile(file: File, output: MutableList<GameEntry>, depth: Int) {
        if (depth > 8 || file.name.startsWith('.') || file.name == "Android") return
        if (file.isDirectory) {
            file.listFiles()?.forEach { scanFile(it, output, depth + 1) }
            return
        }
        val filename = file.name
        GameSystem.fromName(filename)?.let {
            output += GameEntry(cleanName(filename), Uri.fromFile(file), it)
            return
        }
        if (!filename.endsWith(".zip", ignoreCase = true)) return
        runCatching {
            ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) GameSystem.fromName(entry.name)?.let {
                        output += GameEntry(cleanName(entry.name.substringAfterLast('/')), Uri.fromFile(file), it, entry.name)
                        return@use
                    }
                    entry = zip.nextEntry
                }
            }
        }
    }

    fun readGame(context: Context, uri: Uri, zipEntry: String?): ByteArray {
        val input = if (uri.scheme == "file") FileInputStream(requireNotNull(uri.path))
            else context.contentResolver.openInputStream(uri) ?: error("ROM unavailable")
        if (zipEntry == null) return input.use { it.readBytes() }
        return input.use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == zipEntry) return@use zip.readBytes()
                    entry = zip.nextEntry
                }
                error("ROM not found in ZIP")
            }
        }
    }

    private fun cleanName(value: String): String = value.substringBeforeLast('.')
        .replace('_', ' ').replace(Regex("\\s+"), " ").trim()
}
