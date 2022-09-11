package net.pantasystem.milktea.model.file

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.flow.Flow
import net.pantasystem.milktea.common.ResultState
import net.pantasystem.milktea.model.drive.FileProperty
import net.pantasystem.milktea.model.notes.draft.DraftNoteFile
import java.io.Serializable as JSerializable

sealed interface AppFile : JSerializable {

    companion object;


    data class Local(
        val name: String,
        val path: String,
        val type: String,
        val thumbnailUrl: String?,
        val isSensitive: Boolean,
        val folderId: String?,
        val id: Long = 0,
    ) : AppFile {
        fun isAttributeSame(file: Local): Boolean {
            return file.name == name
                    && file.path == path
                    && file.type == type
        }
    }

    data class Remote(
        val id: FileProperty.Id,
    ) : AppFile

}


sealed interface FileState {
    val appFile: AppFile

    data class Local(override val appFile: AppFile.Local) : FileState
    data class Remote(
        override val appFile: AppFile,
        val state: Flow<ResultState<FileProperty>>,
        val source: FileProperty?
    ) : FileState
}


data class File(
    val name: String,
    val path: String?,
    val type: String?,
    val remoteFileId: FileProperty.Id?,
    val localFileId: Long?,
    val thumbnailUrl: String?,
    val isSensitive: Boolean?,
    val folderId: String? = null,
    val comment: String? = null,
) : JSerializable {
    enum class AboutMediaType {
        VIDEO, IMAGE, SOUND, OTHER

    }

    val isRemoteFile: Boolean
        get() = remoteFileId != null

    val aboutMediaType = when {
        this.type == null -> AboutMediaType.OTHER
        this.type.startsWith("image") -> AboutMediaType.IMAGE
        this.type.startsWith("video") -> AboutMediaType.VIDEO
        this.type.startsWith("audio") -> AboutMediaType.SOUND
        else -> AboutMediaType.OTHER
    }
}


fun AppFile.toFile(): File {
    return when (this) {
        is AppFile.Remote -> {
            File(
                name = "remote file",
                path = null,
                type = null,
                remoteFileId = id,
                thumbnailUrl = null,
                isSensitive = null,
                folderId = null,
                localFileId = null
            )
        }
        is AppFile.Local -> {
            File(
                name = name,
                path = path,
                type = type,
                remoteFileId = null,
                thumbnailUrl = thumbnailUrl,
                isSensitive = isSensitive,
                folderId = folderId,
                localFileId = id
            )
        }
    }
}

fun AppFile.Companion.from(file: File): AppFile {
    return if (file.isRemoteFile) {
        AppFile.Remote(
            file.remoteFileId!!
        )
    } else {
        AppFile.Local(
            folderId = file.folderId,
            isSensitive = file.isSensitive ?: false,
            id = file.localFileId!!,
            name = file.name,
            path = file.path!!,
            thumbnailUrl = file.thumbnailUrl,
            type = file.type!!
        )
    }
}

fun AppFile.Companion.from(file: DraftNoteFile): AppFile {
    return when(file) {
        is DraftNoteFile.Local -> AppFile.Local(
            name = file.name,
            path = file.filePath,
            thumbnailUrl = file.thumbnailUrl,
            type = file.type,
            isSensitive = file.isSensitive ?: false,
            folderId = file.folderId,
        )
        is DraftNoteFile.Remote -> AppFile.Remote(file.fileProperty.id)
    }
}


fun Uri.toAppFile(context: Context): AppFile.Local {
    val fileName = try{
        context.getFileName(this)
    }catch(e: Exception){
        Log.d("FileUtils", "ファイル名の取得に失敗しました", e)
        null
    }

    val mimeType = context.contentResolver.getType(this)

    val isMedia = mimeType?.startsWith("image")?: false || mimeType?.startsWith("video")?: false
    val thumbnail = if(isMedia) this.toString() else null
    return AppFile.Local(
        fileName?: "name none",
        path = this.toString(),
        type  = mimeType ?: "",
        thumbnailUrl = thumbnail,
        isSensitive = false,
        folderId = null
    )
}
fun Uri.toFile(context: Context): File {
    val fileName = try{
        context.getFileName(this)
    }catch(e: Exception){
        Log.d("FileUtils", "ファイル名の取得に失敗しました", e)
        null
    }

    val mimeType = context.contentResolver.getType(this)

    val isMedia = mimeType?.startsWith("image")?: false || mimeType?.startsWith("video")?: false
    val thumbnail = if(isMedia) this.toString() else null
    return File(
        fileName ?: "name none",
        this.toString(),
        type = mimeType,
        remoteFileId = null,
        localFileId = null,
        thumbnailUrl = thumbnail,
        isSensitive = null
    )
}

private fun Context.getFileName(uri: Uri) : String{
    return when(uri.scheme){
        "content" ->{
            this.contentResolver
                .query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use{
                    if(it.moveToFirst()){
                        val index = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        if(index != -1) {
                            it.getString(index)
                        }else{
                            null
                        }
                    }else{
                        null
                    }
                }?: throw IllegalArgumentException("ファイル名の取得に失敗しました")
        }
        "file" ->{
            java.io.File(uri.path!!).name
        }
        else -> throw IllegalArgumentException("scheme不明")
    }
}