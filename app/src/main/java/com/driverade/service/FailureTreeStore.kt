package com.driverade.service

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Local recurring incident store; no raw OCR, screen text or rider information. */
internal class FailureTreeStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "failure_tree.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE incidents (
            signature TEXT PRIMARY KEY,
            stage TEXT NOT NULL,
            kind TEXT NOT NULL,
            cause TEXT NOT NULL,
            severity TEXT NOT NULL,
            first_ms INTEGER NOT NULL,
            last_ms INTEGER NOT NULL,
            occurrence_count INTEGER NOT NULL,
            last_capture_id TEXT,
            state TEXT NOT NULL
        )""")
        db.execSQL("CREATE INDEX idx_incidents_recent ON incidents(last_ms DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun record(now: Long, finding: FailureTreeRules.Finding, captureId: String?) {
        val key = listOf(finding.stage, finding.kind, finding.cause).joinToString("|")
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("""INSERT OR IGNORE INTO incidents
                (signature, stage, kind, cause, severity, first_ms, last_ms,
                 occurrence_count, last_capture_id, state)
                VALUES (?,?,?,?,?,?,?,?,?,?)""",
                arrayOf(key, finding.stage, finding.kind, finding.cause,
                    finding.severity, now, now, 0, captureId, "OBSERVED"))
            db.execSQL("""UPDATE incidents
                SET last_ms=?, occurrence_count=occurrence_count+1,
                    last_capture_id=?, severity=?, state='OBSERVED'
                WHERE signature=?""", arrayOf(now, captureId, finding.severity, key))
            db.execSQL("""DELETE FROM incidents WHERE signature NOT IN
                (SELECT signature FROM incidents ORDER BY last_ms DESC LIMIT 256)""")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun summary(): String {
        val lines = mutableListOf<String>()
        readableDatabase.rawQuery(
            """SELECT stage,kind,cause,severity,occurrence_count,last_ms
               FROM incidents ORDER BY last_ms DESC LIMIT 12""", null
        ).use { c ->
            while (c.moveToNext()) {
                lines.add("${c.getString(0)}/${c.getString(1)} ${c.getString(2)} " +
                    "severity=${c.getString(3)} seen=${c.getLong(4)} lastMs=${c.getLong(5)}")
            }
        }
        return if (lines.isEmpty()) "No classified incidents recorded" else lines.joinToString("\n")
    }
}
