package com.lancefer.lancedrop.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import com.lancefer.lancedrop.model.TransferHistoryItem

class HistoryDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "lancefer_history.db"
        const val DATABASE_VERSION = 1

        const val TABLE_TRANSFERS = "transfers_history"
        const val COLUMN_ID = "id"
        const val COLUMN_FILE_NAME = "file_name"
        const val COLUMN_FILE_SIZE = "file_size"
        const val COLUMN_PEER_NAME = "peer_name"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_IS_INCOMING = "is_incoming"
        const val COLUMN_SHA256 = "sha256"
        const val COLUMN_FILE_PATH = "file_path"
        const val COLUMN_FILE_URI = "file_uri"
        const val COLUMN_DURATION_MS = "duration_ms"
        const val COLUMN_AVG_SPEED = "avg_speed"
        const val COLUMN_PEAK_SPEED = "peak_speed"
        const val COLUMN_STATUS = "status"
        const val COLUMN_IS_DIRECTORY = "is_directory"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableSql = """
            CREATE TABLE $TABLE_TRANSFERS (
                $COLUMN_ID TEXT PRIMARY KEY,
                $COLUMN_FILE_NAME TEXT NOT NULL,
                $COLUMN_FILE_SIZE INTEGER NOT NULL,
                $COLUMN_PEER_NAME TEXT NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_IS_INCOMING INTEGER NOT NULL,
                $COLUMN_SHA256 TEXT,
                $COLUMN_FILE_PATH TEXT,
                $COLUMN_FILE_URI TEXT,
                $COLUMN_DURATION_MS INTEGER NOT NULL DEFAULT 0,
                $COLUMN_AVG_SPEED REAL NOT NULL DEFAULT 0.0,
                $COLUMN_PEAK_SPEED REAL NOT NULL DEFAULT 0.0,
                $COLUMN_STATUS TEXT NOT NULL DEFAULT 'COMPLETED',
                $COLUMN_IS_DIRECTORY INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(createTableSql)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TRANSFERS")
        onCreate(db)
    }

    @Synchronized
    fun insertOrUpdate(item: TransferHistoryItem): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_ID, item.id)
            put(COLUMN_FILE_NAME, item.fileName)
            put(COLUMN_FILE_SIZE, item.fileSize)
            put(COLUMN_PEER_NAME, item.peerName)
            put(COLUMN_TIMESTAMP, item.timestamp)
            put(COLUMN_IS_INCOMING, if (item.isIncoming) 1 else 0)
            put(COLUMN_SHA256, item.sha256Checksum)
            put(COLUMN_FILE_PATH, item.filePath)
            put(COLUMN_FILE_URI, item.fileUri?.toString())
            put(COLUMN_DURATION_MS, item.durationMs)
            put(COLUMN_AVG_SPEED, item.averageSpeedBytesPerSec)
            put(COLUMN_PEAK_SPEED, item.peakSpeedBytesPerSec)
            put(COLUMN_STATUS, item.status)
            put(COLUMN_IS_DIRECTORY, if (item.isDirectory) 1 else 0)
        }
        return db.insertWithOnConflict(TABLE_TRANSFERS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun getAllHistory(): List<TransferHistoryItem> {
        val list = mutableListOf<TransferHistoryItem>()
        val db = readableDatabase
        val cursor: Cursor = db.query(
            TABLE_TRANSFERS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_TIMESTAMP DESC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(cursorToItem(c))
            }
        }
        return list
    }

    @Synchronized
    fun getInterruptedTransfers(): List<TransferHistoryItem> {
        val list = mutableListOf<TransferHistoryItem>()
        val db = readableDatabase
        val cursor: Cursor = db.query(
            TABLE_TRANSFERS,
            null,
            "$COLUMN_STATUS = ?",
            arrayOf("INTERRUPTED"),
            null,
            null,
            "$COLUMN_TIMESTAMP DESC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(cursorToItem(c))
            }
        }
        return list
    }

    @Synchronized
    fun updateTransferStatus(id: String, status: String): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_STATUS, status)
        }
        return db.update(TABLE_TRANSFERS, values, "$COLUMN_ID = ?", arrayOf(id))
    }

    @Synchronized
    fun deleteHistoryItem(id: String): Int {
        val db = writableDatabase
        return db.delete(TABLE_TRANSFERS, "$COLUMN_ID = ?", arrayOf(id))
    }

    @Synchronized
    fun clearHistory(): Int {
        val db = writableDatabase
        return db.delete(TABLE_TRANSFERS, null, null)
    }

    private fun cursorToItem(c: Cursor): TransferHistoryItem {
        val id = c.getString(c.getColumnIndexOrThrow(COLUMN_ID))
        val fileName = c.getString(c.getColumnIndexOrThrow(COLUMN_FILE_NAME))
        val fileSize = c.getLong(c.getColumnIndexOrThrow(COLUMN_FILE_SIZE))
        val peerName = c.getString(c.getColumnIndexOrThrow(COLUMN_PEER_NAME))
        val timestamp = c.getLong(c.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
        val isIncoming = c.getInt(c.getColumnIndexOrThrow(COLUMN_IS_INCOMING)) == 1
        val sha256 = c.getString(c.getColumnIndexOrThrow(COLUMN_SHA256))
        val filePath = c.getString(c.getColumnIndexOrThrow(COLUMN_FILE_PATH))
        val uriStr = c.getString(c.getColumnIndexOrThrow(COLUMN_FILE_URI))
        val durationMs = c.getLong(c.getColumnIndexOrThrow(COLUMN_DURATION_MS))
        val avgSpeed = c.getDouble(c.getColumnIndexOrThrow(COLUMN_AVG_SPEED))
        val peakSpeed = c.getDouble(c.getColumnIndexOrThrow(COLUMN_PEAK_SPEED))
        val status = c.getString(c.getColumnIndexOrThrow(COLUMN_STATUS))
        val isDirectory = c.getInt(c.getColumnIndexOrThrow(COLUMN_IS_DIRECTORY)) == 1

        return TransferHistoryItem(
            id = id,
            fileName = fileName,
            fileSize = fileSize,
            peerName = peerName,
            timestamp = timestamp,
            isIncoming = isIncoming,
            sha256Checksum = sha256,
            fileUri = if (!uriStr.isNullOrEmpty()) Uri.parse(uriStr) else null,
            filePath = filePath,
            durationMs = durationMs,
            averageSpeedBytesPerSec = avgSpeed,
            peakSpeedBytesPerSec = peakSpeed,
            status = status,
            isDirectory = isDirectory
        )
    }
}
