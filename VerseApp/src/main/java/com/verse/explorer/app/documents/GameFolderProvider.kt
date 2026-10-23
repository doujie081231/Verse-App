package com.verse.explorer.app.documents

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import com.verse.explorer.core.config.GameConfig
import java.io.File
import java.io.FileNotFoundException

/**
 * 游戏目录文档提供器：把应用内部游戏目录以文档 URI 形式暴露给系统文件管理界面，
 * 便于「用其他应用打开」时由文件管理器浏览目录内容（只读）。
 */
class GameFolderProvider : DocumentsProvider() {

    companion object {
        const val ROOT_ID = "verse_game"

        fun authority(context: Context): String = "${context.packageName}.documents"

        /** 目录对应的文档 URI（文档 ID 使用绝对路径，查询时再校验是否位于游戏目录内） */
        fun documentUri(context: Context, folder: File): Uri =
            DocumentsContract.buildDocumentUri(authority(context), folder.absolutePath)
    }

    private val rootProjection = arrayOf(
        Root.COLUMN_ROOT_ID,
        Root.COLUMN_DOCUMENT_ID,
        Root.COLUMN_TITLE,
        Root.COLUMN_SUMMARY,
        Root.COLUMN_FLAGS,
        Root.COLUMN_MIME_TYPES,
        Root.COLUMN_AVAILABLE_BYTES
    )

    private val documentProjection = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_LAST_MODIFIED,
        Document.COLUMN_SIZE,
        Document.COLUMN_FLAGS
    )

    override fun onCreate(): Boolean = true

    private fun rootDir(): File = GameConfig.getGameDir(requireNotNull(context))

    /** 解析文档 ID：只允许游戏目录及其子目录，防止越权访问其他私有数据 */
    private fun resolve(documentId: String): File? {
        return runCatching {
            val root = rootDir().canonicalFile
            val target = File(documentId).canonicalFile
            if (target == root || target.path.startsWith(root.path + File.separator)) target else null
        }.getOrNull()
    }

    private fun mimeType(file: File): String =
        if (file.isDirectory) Document.MIME_TYPE_DIR
        else MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"

    private fun newDocumentCursor(projection: Array<out String>?): MatrixCursor =
        MatrixCursor(projection ?: documentProjection)

    private fun addDocumentRow(cursor: MatrixCursor, file: File) {
        val row = cursor.newRow()
        for (column in cursor.columnNames) {
            when (column) {
                Document.COLUMN_DOCUMENT_ID -> row.add(column, file.absolutePath)
                Document.COLUMN_MIME_TYPE -> row.add(column, mimeType(file))
                Document.COLUMN_DISPLAY_NAME -> row.add(column, file.name)
                Document.COLUMN_LAST_MODIFIED -> row.add(column, file.lastModified())
                Document.COLUMN_SIZE -> row.add(column, if (file.isDirectory) null else file.length())
                Document.COLUMN_FLAGS -> row.add(column, 0)
                else -> row.add(column, null)
            }
        }
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: rootProjection)
        val root = rootDir()
        val row = cursor.newRow()
        for (column in cursor.columnNames) {
            when (column) {
                Root.COLUMN_ROOT_ID -> row.add(column, ROOT_ID)
                Root.COLUMN_DOCUMENT_ID -> row.add(column, root.absolutePath)
                Root.COLUMN_TITLE -> row.add(column, "Verse 游戏目录")
                Root.COLUMN_SUMMARY -> row.add(column, root.absolutePath)
                Root.COLUMN_FLAGS -> row.add(
                    column,
                    Root.FLAG_LOCAL_ONLY or Root.FLAG_SUPPORTS_IS_CHILD
                )
                Root.COLUMN_MIME_TYPES -> row.add(column, "*/*")
                Root.COLUMN_AVAILABLE_BYTES -> row.add(column, root.usableSpace)
                else -> row.add(column, null)
            }
        }
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val file = resolve(documentId) ?: throw FileNotFoundException(documentId)
        val cursor = newDocumentCursor(projection)
        if (file.exists()) addDocumentRow(cursor, file)
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val parent = resolve(parentDocumentId) ?: throw FileNotFoundException(parentDocumentId)
        val cursor = newDocumentCursor(projection)
        parent.listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?.forEach { addDocumentRow(cursor, it) }
        return cursor
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = resolve(parentDocumentId) ?: return false
        val child = resolve(documentId) ?: return false
        return child.path.startsWith(parent.path + File.separator)
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val file = resolve(documentId) ?: throw FileNotFoundException(documentId)
        if (!file.isFile) throw FileNotFoundException(documentId)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
}
