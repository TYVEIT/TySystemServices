package com.android.ty.systemservices.compose.db

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * 贡献者云里的一条记录。
 *
 * 坐标 [x] / [y] / [fontSize] 都是「设计稿坐标系」下的值（`info.orig_size`，本项目为 2056），
 * 绘制时需要按目标位图边长等比换算。
 *
 * [rotation] 只有三种：`0` 横排、`-1` 逆时针 90°、`1` 顺时针 90°。
 */
data class ContributorEntry(
    val id: Int,
    val name: String,
    val username: String?,
    val commits: Int,
    val x: Float,
    val y: Float,
    val rotation: Int,
    val fontSize: Float,
) {
    /** `名字 <username>`，没有 username 时只有名字（与 LineageParts 的列表文案一致） */
    val displayName: String
        get() = if (username.isNullOrBlank()) name else "$name <$username>"
}

/** 云端统计：贡献者数、提交数、数据生成时间 */
data class ContributorsStats(
    val contributors: Int,
    val commits: Int,
    val updatedAt: Long,
)

/**
 * 贡献者数据库（`assets/contributors.db`）的只读访问层。
 *
 * 数据库随 APK 打包，首次使用时复制到 `databases/` 再以只读方式打开。
 * 复制用 [DB_VERSION] 做标记：升级 APK 时改这个值即可触发重新复制，
 * 避免旧版本残留导致字段缺失。
 */
class ContributorsDb(context: Context) {

    private val appContext = context.applicationContext

    private var database: SQLiteDatabase? = null
    private var cachedEntries: List<ContributorEntry>? = null
    private var cachedSourceSize: Int = DEFAULT_SOURCE_SIZE

    /** 打开数据库；失败返回 false，调用方按「云图不可用」处理 */
    fun open(): Boolean {
        database?.let { if (it.isOpen) return true }
        val file = ensureExtracted() ?: return false
        return try {
            val db = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
            database = db
            readSourceSize(db)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "open contributors.db failed", t)
            false
        }
    }

    /** 全部记录（含 id < 0 的分组标签，用于绘制；搜索/定位会过滤掉） */
    fun all(): List<ContributorEntry> {
        cachedEntries?.let { return it }
        val db = database ?: return emptyList()
        val result = ArrayList<ContributorEntry>(2_600)
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(
                "select id, name, username, commits, x, y, r, fs from metadata",
                null,
            )
            while (cursor.moveToNext()) {
                result.add(cursor.toEntry())
            }
        } catch (t: Throwable) {
            Log.e(TAG, "read metadata failed", t)
        } finally {
            cursor?.close()
        }
        cachedEntries = result
        return result
    }

    /**
     * 按关键字搜索（名字 / 用户名 / filter 字段）。
     *
     * `filter` 是数据生成时拼好的 `名字|用户名|别名`，LineageParts 的搜索就是查它；
     * 这里额外补上 name / username 两列，兼容 `filter` 为空的三百多条记录。
     */
    fun search(query: String, limit: Int = 100): List<ContributorEntry> {
        val db = database ?: return emptyList()
        val keyword = query.trim()
        if (keyword.isEmpty()) return emptyList()
        val like = "%" + keyword.replace("%", "").replace("_", "") + "%"
        val result = ArrayList<ContributorEntry>()
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(
                """
                select id, name, username, commits, x, y, r, fs from metadata
                where id > 0 and (
                    lower(ifnull(filter, '')) like lower(?) or
                    lower(ifnull(name, '')) like lower(?) or
                    lower(ifnull(username, '')) like lower(?)
                )
                order by commits desc
                limit $limit
                """.trimIndent(),
                arrayOf(like, like, like),
            )
            while (cursor.moveToNext()) {
                result.add(cursor.toEntry())
            }
        } catch (t: Throwable) {
            Log.e(TAG, "search failed", t)
        } finally {
            cursor?.close()
        }
        return result
    }

    /** 精确找一个人：先按名字、再按用户名、最后按模糊匹配兜底 */
    fun find(query: String): ContributorEntry? {
        val keyword = query.trim()
        if (keyword.isEmpty()) return null
        val db = database ?: return null
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(
                """
                select id, name, username, commits, x, y, r, fs from metadata
                where id > 0 and (
                    lower(name) = lower(?) or
                    lower(ifnull(username, '')) = lower(?)
                )
                order by commits desc
                limit 1
                """.trimIndent(),
                arrayOf(keyword, keyword),
            )
            if (cursor.moveToNext()) return cursor.toEntry()
        } catch (t: Throwable) {
            Log.w(TAG, "find by exact name failed", t)
        } finally {
            cursor?.close()
        }
        return search(keyword, limit = 1).firstOrNull()
    }

    fun stats(): ContributorsStats? {
        val db = database ?: return null
        var contributors = -1
        var commits = -1
        var updatedAt = -1L

        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery("select count(*) from metadata where id > 0;", null)
            if (cursor.moveToFirst()) contributors = cursor.getInt(0)
        } catch (t: Throwable) {
            Log.w(TAG, "count contributors failed", t)
        } finally {
            cursor?.close()
        }

        try {
            cursor = db.rawQuery("select sum(commits) from metadata where id > 0;", null)
            if (cursor.moveToFirst()) commits = cursor.getInt(0)
        } catch (t: Throwable) {
            Log.w(TAG, "sum commits failed", t)
        } finally {
            cursor?.close()
        }

        try {
            cursor = db.rawQuery("select value from info where key = ?;", arrayOf("date"))
            if (cursor.moveToFirst()) updatedAt = cursor.getLong(0)
        } catch (t: Throwable) {
            Log.w(TAG, "read date failed", t)
        } finally {
            cursor?.close()
        }

        if (contributors < 0 && commits < 0 && updatedAt < 0) return null
        return ContributorsStats(contributors, commits, updatedAt)
    }

    /** 设计稿边长，绘制时按它换算坐标 */
    fun sourceSize(): Int = cachedSourceSize

    fun close() {
        try {
            database?.close()
        } catch (t: Throwable) {
            Log.w(TAG, "close failed", t)
        }
        database = null
        cachedEntries = null
    }

    private fun readSourceSize(db: SQLiteDatabase) {
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery("select value from info where key = ?;", arrayOf("orig_size"))
            if (cursor.moveToFirst()) {
                val size = cursor.getString(0)?.toIntOrNull() ?: DEFAULT_SOURCE_SIZE
                if (size > 0) cachedSourceSize = size
            }
        } catch (t: Throwable) {
            Log.w(TAG, "read orig_size failed", t)
        } finally {
            cursor?.close()
        }
    }

    private fun Cursor.toEntry(): ContributorEntry = ContributorEntry(
        id = getInt(0),
        name = getString(1) ?: "",
        username = getString(2),
        commits = getInt(3),
        x = getFloat(4),
        y = getFloat(5),
        rotation = getInt(6),
        fontSize = getFloat(7),
    )

    /**
     * 把 assets 里的数据库复制到应用私有目录。
     *
     * 只在「文件不存在 / 版本号变了 / 长度对不上」时复制，正常启动只做一次 stat。
     */
    private fun ensureExtracted(): File? {
        val target = appContext.getDatabasePath(DB_NAME)
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val extractedVersion = prefs.getInt(KEY_DB_VERSION, 0)
        val assetLength = assetLength()

        if (target.exists() && extractedVersion == DB_VERSION &&
            (assetLength <= 0 || target.length() == assetLength)
        ) {
            return target
        }

        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "$DB_NAME.tmp")
        try {
            appContext.assets.open(DB_NAME).use { input ->
                FileOutputStream(temp).use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                }
            }
            if (target.exists() && !target.delete()) {
                Log.w(TAG, "cannot delete stale database")
            }
            if (!temp.renameTo(target)) {
                Log.e(TAG, "cannot move extracted database into place")
                temp.delete()
                return null
            }
            prefs.edit().putInt(KEY_DB_VERSION, DB_VERSION).apply()
            return target
        } catch (t: Throwable) {
            Log.e(TAG, "extract contributors.db failed", t)
            temp.delete()
            return null
        }
    }

    private fun assetLength(): Long = try {
        appContext.assets.openFd(DB_NAME).use { it.length }
    } catch (t: Throwable) {
        // assets 被压缩时拿不到 fd，退化成「不校验长度」
        -1L
    }

    companion object {
        private const val TAG = "ContributorsDb"
        private const val DB_NAME = "contributors.db"
        private const val PREFS_NAME = "contributors_db"
        private const val KEY_DB_VERSION = "db_version"

        /** APK 内数据库的版本号，替换 assets 里的 db 后 +1 */
        private const val DB_VERSION = 5

        /** db 里 `info.orig_size` 缺失时的兜底值 */
        private const val DEFAULT_SOURCE_SIZE = 2056
    }
}
