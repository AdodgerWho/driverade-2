package com.driverade.service

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class DiagnosticSqliteStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "diagnostics.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, time_ms INTEGER, category TEXT, detail TEXT)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun add(time: Long, category: String, detail: String) {
        writableDatabase.execSQL("INSERT INTO events(time_ms,category,detail) VALUES(?,?,?)", arrayOf(time, category, detail))
        if (time % 97L == 0L) prune(time)
    }
    fun prune(now: Long) {
        writableDatabase.delete("events", "time_ms < ?", arrayOf((now - 2592000000L).toString()))
        writableDatabase.execSQL("DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY id DESC LIMIT 50000)")
    }
}
