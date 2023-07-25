package net.pantasystem.milktea.data.infrastructure.notes.impl

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import net.pantasystem.milktea.api.mastodon.status.CreateStatus
import net.pantasystem.milktea.api.mastodon.status.TootStatusDTO
import net.pantasystem.milktea.api.misskey.notes.DeleteNote
import net.pantasystem.milktea.api.misskey.notes.NoteDTO
import net.pantasystem.milktea.api.misskey.notes.NoteRequest
import net.pantasystem.milktea.api.misskey.notes.mute.ToggleThreadMuteRequest
import net.pantasystem.milktea.common.Logger
import net.pantasystem.milktea.common.mapCancellableCatching
import net.pantasystem.milktea.common.runCancellableCatching
import net.pantasystem.milktea.common.throwIfHasError
import net.pantasystem.milktea.data.api.mastodon.MastodonAPIProvider
import net.pantasystem.milktea.data.api.misskey.MisskeyAPIProvider
import net.pantasystem.milktea.data.infrastructure.drive.FileUploaderProvider
import net.pantasystem.milktea.data.infrastructure.drive.UploadSource
import net.pantasystem.milktea.model.account.Account
import net.pantasystem.milktea.model.account.AccountRepository
import net.pantasystem.milktea.model.drive.FilePropertyDataSource
import net.pantasystem.milktea.model.file.AppFile
import net.pantasystem.milktea.model.notes.CreateNote
import net.pantasystem.milktea.model.notes.Note
import net.pantasystem.milktea.model.notes.type4Mastodon
import javax.inject.Inject


interface NoteApiAdapter {

    interface Factory {
        suspend fun create(account: Account): NoteApiAdapter
    }

    suspend fun create(createNote: CreateNote): NoteCreatedResultType

    suspend fun showNote(noteId: Note.Id): ShowNoteResultType
    suspend fun delete(noteId: Note.Id): DeleteNoteResultType
    suspend fun createThreadMute(noteId: Note.Id): ToggleThreadMuteResultType
    suspend fun deleteThreadMute(noteId: Note.Id): ToggleThreadMuteResultType
    suspend fun renote(target: Note): RenoteResultType
}

internal class NoteApiAdapterFactoryImpl @Inject constructor(
    private val accountRepository: AccountRepository,
    private val misskeyAPIProvider: MisskeyAPIProvider,
    private val mastodonAPIProvider: MastodonAPIProvider,
    private val uploader: FileUploaderProvider,
    private val filePropertyDataSource: FilePropertyDataSource,
    private val loggerFactory: Logger.Factory,
): NoteApiAdapter.Factory {
    override suspend fun create(account: Account): NoteApiAdapter {
        return when(account.instanceType) {
            Account.InstanceType.MISSKEY, Account.InstanceType.FIREFISH -> NoteApiAdapterMisskeyPattern(
                accountRepository = accountRepository,
                misskeyAPIProvider = misskeyAPIProvider,
                filePropertyDataSource = filePropertyDataSource,
                uploader = uploader,
                loggerFactory = loggerFactory,
            )
            Account.InstanceType.MASTODON, Account.InstanceType.PLEROMA -> NoteApiAdapterMastodonPattern(
                uploader = uploader,
                mastodonAPIProvider = mastodonAPIProvider,
                accountRepository = accountRepository,
            )
        }
    }
}
internal class NoteApiAdapterMisskeyPattern(
    val accountRepository: AccountRepository,
    val misskeyAPIProvider: MisskeyAPIProvider,
    val filePropertyDataSource: FilePropertyDataSource,
    private val uploader: FileUploaderProvider,
    val loggerFactory: Logger.Factory
) : NoteApiAdapter {
    override suspend fun create(createNote: CreateNote): NoteResultType.Misskey {
        val task = PostNoteTask(
            createNote,
            createNote.author,
            loggerFactory,
            filePropertyDataSource
        )
        val result = runCancellableCatching {
            task.execute(
                uploader.get(createNote.author)
            ) ?: throw IllegalStateException("ファイルのアップロードに失敗しました")
        }.mapCancellableCatching {
            misskeyAPIProvider.get(createNote.author).create(it).throwIfHasError()
                .body()?.createdNote
        }.onFailure {
            loggerFactory.create("NoteApiAdapter").error("create note error", it)
        }

        val noteDTO = result.getOrThrow()
        return NoteResultType.Misskey(requireNotNull(noteDTO))
    }

    override suspend fun showNote(noteId: Note.Id): ShowNoteResultType {
        val account = accountRepository.get(noteId.accountId).getOrThrow()
        val body = misskeyAPIProvider.get(account).showNote(
            NoteRequest(
                i = account.token,
                noteId = noteId.noteId
            )
        ).throwIfHasError().body()
        return NoteResultType.Misskey(requireNotNull(body))
    }

    override suspend fun delete(noteId: Note.Id): DeleteNoteResultType {
        val account = accountRepository.get(noteId.accountId).getOrThrow()
        misskeyAPIProvider.get(account).delete(
            DeleteNote(
                i = account.token,
                noteId = noteId.noteId
            )
        ).throwIfHasError()
        return DeleteNoteResultType.Misskey
    }

    override suspend fun createThreadMute(noteId: Note.Id): ToggleThreadMuteResultType {
        val account = accountRepository.get(noteId.accountId).getOrThrow()
        misskeyAPIProvider.get(account).createThreadMute(
            ToggleThreadMuteRequest(
                i = account.token,
                noteId = noteId.noteId
            )
        ).throwIfHasError()
        return ToggleThreadMuteResultType.Misskey
    }

    override suspend fun deleteThreadMute(noteId: Note.Id): ToggleThreadMuteResultType {
        val account = accountRepository.get(noteId.accountId).getOrThrow()
        misskeyAPIProvider.get(account).deleteThreadMute(
            ToggleThreadMuteRequest(
                i = account.token,
                noteId = noteId.noteId,
            )
        ).throwIfHasError()
        return ToggleThreadMuteResultType.Misskey
    }

    override suspend fun renote(target: Note): RenoteResultType {
        val account = accountRepository.get(target.id.accountId).getOrThrow()
        return create(
            CreateNote(
                author = account, renoteId = target.id,
                text = null,
                visibility = target.visibility
            )
        )
    }

}

internal class NoteApiAdapterMastodonPattern(
    private val uploader: FileUploaderProvider,
    private val mastodonAPIProvider: MastodonAPIProvider,
    private val accountRepository: AccountRepository,
) : NoteApiAdapter {
    override suspend fun create(createNote: CreateNote): NoteCreatedResultType {
        val fileIds = coroutineScope {
            createNote.files?.map { appFile ->
                async {
                    when (appFile) {
                        is AppFile.Local -> {
                            uploader.get(createNote.author)
                                .upload(UploadSource.LocalFile(appFile), false).id
                        }
                        is AppFile.Remote -> appFile.id
                    }
                }
            }?.awaitAll()
        }
        val body = mastodonAPIProvider.get(createNote.author).createStatus(
            CreateStatus(
                status = createNote.text ?: "",
                mediaIds = fileIds?.map {
                    it.fileId
                } ?: emptyList(),
                inReplyToId = createNote.replyId?.noteId,
                spoilerText = createNote.cw,
                sensitive = createNote.isSensitive ?: false,
                visibility = createNote.visibility.type4Mastodon(),
                poll = createNote.poll?.let { poll ->
                    CreateStatus.CreatePoll(
                        options = poll.choices,
                        multiple = poll.multiple,
                        expiresIn = poll.expiresAt?.let {
                            (it - Clock.System.now().toEpochMilliseconds()) / 1000
                        }?.toInt() ?: (5 * 60),
                    )
                },
                quoteId = createNote.renoteId?.noteId,
            )
        ).throwIfHasError().body()
        return NoteResultType.Mastodon(requireNotNull(body))
    }

    override suspend fun showNote(noteId: Note.Id): ShowNoteResultType {
        val account = accountRepository.get(noteId.accountId).getOrThrow()
        val body = mastodonAPIProvider.get(account)
            .getStatus(noteId.noteId)
            .throwIfHasError().body()
        return NoteResultType.Mastodon(requireNotNull(body))
    }

    override suspend fun delete(noteId: Note.Id): DeleteNoteResultType {
        val account = accountRepository.get(noteId.accountId).getOrThrow()
        val body = mastodonAPIProvider.get(account).deleteStatus(noteId.noteId)
            .throwIfHasError()
            .body()
        return DeleteNoteResultType.Mastodon(requireNotNull(body))
    }

    override suspend fun createThreadMute(noteId: Note.Id): ToggleThreadMuteResultType {
        val account = accountRepository.get(noteId.accountId).getOrThrow()
        val body = mastodonAPIProvider.get(account)
            .muteConversation(noteId.noteId)
            .throwIfHasError()
            .body()
        return ToggleThreadMuteResultType.Mastodon(requireNotNull(body))
    }

    override suspend fun deleteThreadMute(noteId: Note.Id): ToggleThreadMuteResultType {
        val account = accountRepository.get(noteId.accountId).getOrThrow()
        val body = mastodonAPIProvider.get(account).unmuteConversation(noteId.noteId)
            .throwIfHasError()
            .body()
        return ToggleThreadMuteResultType.Mastodon(requireNotNull(body))
    }

    override suspend fun renote(target: Note): RenoteResultType {
        val account = accountRepository.get(target.id.accountId).getOrThrow()
        val toot = mastodonAPIProvider.get(account).reblog(target.id.noteId)
            .throwIfHasError()
            .body()
        return NoteResultType.Mastodon(requireNotNull(toot))
    }

}

sealed interface NoteResultType {
    data class Misskey(val note: NoteDTO) : NoteResultType
    data class Mastodon(val status: TootStatusDTO) : NoteResultType
}

sealed interface DeleteNoteResultType {
    data class Mastodon(val status: TootStatusDTO) : DeleteNoteResultType
    object Misskey : DeleteNoteResultType
}


typealias ShowNoteResultType = NoteResultType

typealias NoteCreatedResultType = NoteResultType


sealed interface ToggleThreadMuteResultType {
    object Misskey : ToggleThreadMuteResultType
    data class Mastodon(val status: TootStatusDTO) : ToggleThreadMuteResultType
}

typealias RenoteResultType = NoteResultType