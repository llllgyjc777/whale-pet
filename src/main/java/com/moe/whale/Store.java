package com.moe.whale;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** 对话记忆 + 操作日志 */
public class Store extends SQLiteOpenHelper {
    private static final String DB = "whale.db";
    private static final int VER = 1;
    private static Store inst;

    public static synchronized Store get(Context c) {
        if (inst == null) inst = new Store(c.getApplicationContext());
        return inst;
    }

    private Store(Context c) { super(c, DB, null, VER); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE msg(id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "role TEXT, content TEXT, ts INTEGER)");
        db.execSQL("CREATE TABLE log(id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "action TEXT, detail TEXT, ts INTEGER)");
        db.execSQL("CREATE TABLE fact(id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "k TEXT UNIQUE, v TEXT, ts INTEGER)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int o, int n) { }

    // ---- 消息 ----
    public void addMsg(String role, String content) {
        ContentValues v = new ContentValues();
        v.put("role", role);
        v.put("content", content);
        v.put("ts", System.currentTimeMillis());
        getWritableDatabase().insert("msg", null, v);
    }

    /** 取最近 n 轮对话，正序返回 */
    public List<String[]> recentMsgs(int n) {
        List<String[]> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT role,content FROM msg ORDER BY id DESC LIMIT ?", new String[]{String.valueOf(n)});
        while (c.moveToNext()) out.add(0, new String[]{c.getString(0), c.getString(1)});
        c.close();
        return out;
    }

    public void clearMsgs() { getWritableDatabase().delete("msg", null, null); }

    // ---- 操作日志 ----
    public void log(String action, String detail) {
        ContentValues v = new ContentValues();
        v.put("action", action);
        v.put("detail", detail);
        v.put("ts", System.currentTimeMillis());
        getWritableDatabase().insert("log", null, v);
        // 只保留最近 500 条
        getWritableDatabase().execSQL(
                "DELETE FROM log WHERE id NOT IN (SELECT id FROM log ORDER BY id DESC LIMIT 500)");
    }

    public List<String> recentLogs(int n) {
        List<String> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT ts,action,detail FROM log ORDER BY id DESC LIMIT ?", new String[]{String.valueOf(n)});
        while (c.moveToNext()) {
            long ts = c.getLong(0);
            out.add(String.format("%tH:%tM  %s  %s", ts, ts, c.getString(1), c.getString(2)));
        }
        c.close();
        return out;
    }

    // ---- 长期事实 ----
    public void putFact(String k, String v) {
        ContentValues cv = new ContentValues();
        cv.put("k", k);
        cv.put("v", v);
        cv.put("ts", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("fact", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String factsText(int max) {
        StringBuilder sb = new StringBuilder();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT k,v FROM fact ORDER BY ts DESC LIMIT ?", new String[]{String.valueOf(max)});
        while (c.moveToNext()) sb.append("- ").append(c.getString(0)).append(": ").append(c.getString(1)).append('\n');
        c.close();
        return sb.toString();
    }
}