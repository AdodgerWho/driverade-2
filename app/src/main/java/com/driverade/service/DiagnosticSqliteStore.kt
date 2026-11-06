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
    }
}
