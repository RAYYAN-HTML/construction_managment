package com.siteledger.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class LedgerBackupManager(context: Context) {
    private val appContext = context.applicationContext

    fun export(uri: Uri) {
        val databaseFile = appContext.getDatabasePath(DATABASE_NAME)
        require(databaseFile.isFile) { "The SiteLedger database was not found." }
        AppDatabase.get(appContext).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
            if (cursor.moveToFirst() && cursor.getInt(0) != 0) error("The database is busy. Close other SiteLedger screens and try again.")
        }
        val photosDirectory = File(appContext.filesDir, PHOTO_DIRECTORY)
        val photos = photosDirectory.listFiles().orEmpty().filter { it.isFile && SAFE_FILE_NAME.matches(it.name) }
        val output = appContext.contentResolver.openOutputStream(uri, "w") ?: error("Could not create the selected backup file.")
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_FILE))
            zip.write(JSONObject().put("application", appContext.packageName).put("formatVersion", FORMAT_VERSION).put("databaseVersion", DATABASE_VERSION).toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(DATABASE_FILE))
            FileInputStream(databaseFile).use { it.copyTo(zip) }
            zip.closeEntry()
            photos.forEach { photo ->
                zip.putNextEntry(ZipEntry("$PHOTOS_FOLDER/${photo.name}"))
                FileInputStream(photo).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun restore(uri: Uri) {
        val staging = File(appContext.filesDir, "ledger_restore_${UUID.randomUUID()}").apply { check(mkdirs()) { "Could not prepare the restore." } }
        val stagedDatabase = File(staging, DATABASE_FILE)
        val stagedPhotos = File(staging, PHOTOS_FOLDER).apply { mkdirs() }
        try {
            extractBackup(uri, stagedDatabase, stagedPhotos)
            validateDatabase(stagedDatabase)
            rewritePhotoPaths(stagedDatabase, stagedPhotos)
            replaceLocalData(stagedDatabase, stagedPhotos)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun extractBackup(uri: Uri, database: File, photos: File) {
        val input = appContext.contentResolver.openInputStream(uri) ?: error("Could not open the selected backup file.")
        var manifest: JSONObject? = null
        var databaseFound = false
        var totalBytes = 0L
        val entryNames = mutableSetOf<String>()
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory) { "The selected file is not a valid SiteLedger backup." }
                require(entryNames.add(entry.name)) { "The backup contains duplicate entries." }
                when {
                    entry.name == MANIFEST_FILE -> {
                        val bytes = zip.readBounded(MAX_MANIFEST_BYTES)
                        manifest = JSONObject(bytes.toString(Charsets.UTF_8))
                    }
                    entry.name == DATABASE_FILE -> {
                        totalBytes += zip.copyBoundedTo(database, MAX_DATABASE_BYTES)
                        databaseFound = true
                    }
                    entry.name.startsWith("$PHOTOS_FOLDER/") -> {
                        val name = entry.name.removePrefix("$PHOTOS_FOLDER/")
                        require(SAFE_FILE_NAME.matches(name)) { "The backup contains an invalid photo path." }
                        val target = File(photos, name)
                        totalBytes += zip.copyBoundedTo(target, MAX_PHOTO_BYTES)
                    }
                    else -> error("The selected file is not a supported SiteLedger backup.")
                }
                require(totalBytes <= MAX_TOTAL_BYTES) { "The backup is too large to restore safely." }
                zip.closeEntry()
            }
        }
        val info = requireNotNull(manifest) { "The selected file is missing its backup information." }
        require(info.optString("application") == appContext.packageName && info.optInt("formatVersion") == FORMAT_VERSION) {
            "This file is not a supported SiteLedger backup."
        }
        require(info.optInt("databaseVersion") in MIN_DATABASE_VERSION..DATABASE_VERSION) { "This backup uses an unsupported database version." }
        require(databaseFound && database.isFile && database.length() > 0L) { "The backup does not contain a valid ledger database." }
    }

    private fun validateDatabase(databaseFile: File) {
        val database = SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        database.use {
            require(it.version in MIN_DATABASE_VERSION..DATABASE_VERSION) { "This backup uses an unsupported database version." }
            val requiredTables = setOf("workers", "attendance", "worker_ledger_entries", "counterparties", "counterparty_ledger_entries")
            val tables = mutableSetOf<String>()
            it.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { cursor ->
                while (cursor.moveToNext()) tables += cursor.getString(0)
            }
            require(tables.containsAll(requiredTables)) { "The selected backup database is incomplete." }
        }
    }

    private fun rewritePhotoPaths(databaseFile: File, photos: File) {
        val database = SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        database.use { db ->
            db.beginTransaction()
            try {
                listOf("worker_ledger_entries", "counterparty_ledger_entries").forEach { table ->
                    val references = mutableListOf<Pair<String, String>>()
                    db.rawQuery("SELECT id, photoPath FROM `$table` WHERE photoPath IS NOT NULL", null).use { cursor ->
                        while (cursor.moveToNext()) references += cursor.getString(0) to cursor.getString(1)
                    }
                    references.forEach { (id, oldPath) ->
                        val photoName = File(oldPath).name
                        require(SAFE_FILE_NAME.matches(photoName)) { "The backup contains an invalid photo reference." }
                        val photo = File(photos, photoName)
                        val newPath = photo.takeIf { it.isFile }?.let { File(appContext.filesDir, "$PHOTO_DIRECTORY/$photoName").absolutePath }
                        db.execSQL("UPDATE `$table` SET photoPath = ? WHERE id = ?", arrayOf(newPath, id))
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun replaceLocalData(stagedDatabase: File, stagedPhotos: File) {
        val currentDatabase = appContext.getDatabasePath(DATABASE_NAME)
        val currentPhotos = File(appContext.filesDir, PHOTO_DIRECTORY)
        val rollback = File(appContext.filesDir, "ledger_previous_${UUID.randomUUID()}").apply { check(mkdirs()) }
        val oldDatabase = File(rollback, DATABASE_FILE)
        val oldPhotos = File(rollback, PHOTOS_FOLDER)
        var databaseMoved = false
        var photosMoved = false
        var newDatabaseInstalled = false
        var newPhotosInstalled = false
        AppDatabase.closeAndReset()
        try {
            currentDatabase.parentFile?.mkdirs()
            File(currentDatabase.absolutePath + "-wal").delete()
            File(currentDatabase.absolutePath + "-shm").delete()
            if (currentDatabase.exists()) { move(currentDatabase, oldDatabase); databaseMoved = true }
            if (currentPhotos.exists()) { move(currentPhotos, oldPhotos); photosMoved = true }
            move(stagedDatabase, currentDatabase); newDatabaseInstalled = true
            if (stagedPhotos.exists()) { move(stagedPhotos, currentPhotos); newPhotosInstalled = true }
            AppDatabase.get(appContext).openHelper.writableDatabase
        } catch (failure: Exception) {
            if (newDatabaseInstalled) currentDatabase.delete()
            if (newPhotosInstalled) currentPhotos.deleteRecursively()
            AppDatabase.closeAndReset()
            if (databaseMoved && oldDatabase.exists()) move(oldDatabase, currentDatabase)
            if (photosMoved && oldPhotos.exists()) move(oldPhotos, currentPhotos)
            AppDatabase.get(appContext)
            throw failure
        } finally {
            rollback.deleteRecursively()
        }
    }

    private fun move(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        if (!source.renameTo(destination)) {
            if (source.isDirectory) source.copyRecursively(destination, overwrite = true) else source.copyTo(destination, overwrite = true)
            check(destination.exists()) { "Could not install the backup." }
            if (source.isDirectory) source.deleteRecursively() else source.delete()
        }
    }

    private fun java.io.InputStream.readBounded(maximum: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        copyBoundedTo(output, maximum)
        return output.toByteArray()
    }

    private fun java.io.InputStream.copyBoundedTo(file: File, maximum: Long): Long = FileOutputStream(file).use { copyBoundedTo(it, maximum) }

    private fun java.io.InputStream.copyBoundedTo(output: java.io.OutputStream, maximum: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var count = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            count += read
            require(count <= maximum) { "A backup file entry is too large." }
            output.write(buffer, 0, read)
        }
        return count
    }

    companion object {
        private const val DATABASE_NAME = "siteledger.db"
        private const val DATABASE_FILE = "siteledger.db"
        private const val MANIFEST_FILE = "manifest.json"
        private const val PHOTO_DIRECTORY = "ledger_photos"
        private const val PHOTOS_FOLDER = "photos"
        private const val FORMAT_VERSION = 1
        private const val MIN_DATABASE_VERSION = 3
        private const val DATABASE_VERSION = 4
        private const val MAX_MANIFEST_BYTES = 16 * 1024L
        private const val MAX_DATABASE_BYTES = 256 * 1024 * 1024L
        private const val MAX_PHOTO_BYTES = 64 * 1024 * 1024L
        private const val MAX_TOTAL_BYTES = 1024 * 1024 * 1024L
        private val SAFE_FILE_NAME = Regex("[A-Za-z0-9._-]{1,200}")
    }
}
