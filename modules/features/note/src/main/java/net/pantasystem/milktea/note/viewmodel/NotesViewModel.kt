package net.pantasystem.milktea.note.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.pantasystem.milktea.app_store.account.AccountStore
import net.pantasystem.milktea.app_store.notes.NoteTranslationStore
import net.pantasystem.milktea.common.Logger
import net.pantasystem.milktea.common.mapCancellableCatching
import net.pantasystem.milktea.common.runCancellableCatching
import net.pantasystem.milktea.model.notes.Note
import net.pantasystem.milktea.model.notes.NoteRelation
import net.pantasystem.milktea.model.notes.NoteRepository
import net.pantasystem.milktea.model.notes.bookmark.BookmarkRepository
import net.pantasystem.milktea.model.notes.draft.DraftNote
import net.pantasystem.milktea.model.notes.draft.DraftNoteRepository
import net.pantasystem.milktea.model.notes.draft.toDraftNote
import net.pantasystem.milktea.model.notes.favorite.FavoriteRepository
import net.pantasystem.milktea.model.notes.favorite.ToggleFavoriteUseCase
import net.pantasystem.milktea.model.notes.poll.Poll
import net.pantasystem.milktea.model.notes.reaction.ToggleReactionUseCase
import net.pantasystem.milktea.model.user.report.Report
import javax.inject.Inject


@HiltViewModel
class NotesViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val toggleReactionUseCase: ToggleReactionUseCase,
    private val favoriteRepository: FavoriteRepository,
    val accountStore: AccountStore,
    val draftNoteRepository: DraftNoteRepository,
    private val translationStore: NoteTranslationStore,
    private val bookmarkRepository: BookmarkRepository,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    loggerFactory: Logger.Factory
) : ViewModel() {
    private val logger by lazy {
        loggerFactory.create("NotesViewModel")
    }

    private val _statusMessage = MutableSharedFlow<String>(onBufferOverflow = BufferOverflow.DROP_OLDEST, extraBufferCapacity = 10)
    val statusMessage = _statusMessage.asSharedFlow()

    val quoteRenoteTarget = MutableSharedFlow<Note>(onBufferOverflow = BufferOverflow.DROP_OLDEST, extraBufferCapacity = 10)

    val confirmDeletionEvent = MutableSharedFlow<NoteRelation?>(onBufferOverflow = BufferOverflow.DROP_OLDEST, extraBufferCapacity = 10)

    val confirmDeleteAndEditEvent = MutableSharedFlow<NoteRelation?>(onBufferOverflow = BufferOverflow.DROP_OLDEST, extraBufferCapacity = 10)

    val confirmReportEvent = MutableSharedFlow<Report?>(onBufferOverflow = BufferOverflow.DROP_OLDEST, extraBufferCapacity = 10)

    private val _openNoteEditorEvent = MutableSharedFlow<DraftNote?>(onBufferOverflow = BufferOverflow.DROP_OLDEST, extraBufferCapacity = 10)
    val openNoteEditorEvent = _openNoteEditorEvent.asSharedFlow()

    fun showQuoteNoteEditor(noteId: Note.Id) {
        viewModelScope.launch {
            recursiveSearchHasContentNote(noteId).onSuccess { note ->
                withContext(Dispatchers.Main) {
                    quoteRenoteTarget.tryEmit(note)
                }
            }

        }
    }

    private suspend fun recursiveSearchHasContentNote(noteId: Note.Id): Result<Note> =
        noteRepository.find(noteId).mapCancellableCatching { note ->
            if (note.hasContent()) {
                note
            } else {
                recursiveSearchHasContentNote(requireNotNull(note.renoteId)).getOrThrow()
            }
        }

    /**
     * リアクションを送信する
     * @param reaction 既存のリアクションと値が同様の場合は解除のみする
     * 既に含まれているmyReactionと一致しない場合は一度解除し再送する
     */
    fun postReaction(planeNoteViewData: PlaneNoteViewData, reaction: String) {

        val id = planeNoteViewData.toShowNote.note.id
        toggleReaction(id, reaction)
    }

    fun toggleReaction(noteId: Note.Id, reaction: String) {
        viewModelScope.launch {
            toggleReactionUseCase(noteId, reaction).onFailure {
                logger.error("リアクション失敗", it)
            }
        }
    }

    fun addFavorite(noteId: Note.Id) {
        viewModelScope.launch {
            favoriteRepository.create(noteId).onSuccess {
                _statusMessage.tryEmit("お気に入りに追加しました")
            }.onFailure {
                _statusMessage.tryEmit("お気に入りにへの追加に失敗しました")
            }
        }
    }

    fun deleteFavorite(noteId: Note.Id) {
        viewModelScope.launch {
            val result = favoriteRepository.delete(noteId).getOrNull() != null
            _statusMessage.tryEmit(if (result) {
                "お気に入りから削除しました"
            } else {
                "お気に入りの削除に失敗しました"
            })
        }
    }


    fun addBookmark(noteId: Note.Id) {
        viewModelScope.launch {
            bookmarkRepository.create(noteId).onFailure {
                logger.error("add book mark error", it)
            }
        }
    }

    fun removeBookmark(noteId: Note.Id) {
        viewModelScope.launch {
            bookmarkRepository.delete(noteId).onFailure {
                logger.error("remove book mark error", it)
            }
        }
    }

    fun removeNote(noteId: Note.Id) {
        viewModelScope.launch {
            noteRepository.delete(noteId).onSuccess {
                _statusMessage.tryEmit("削除に成功しました")
            }.onFailure {
                logger.error("ノート削除に失敗", it)
            }
        }

    }

    fun removeAndEditNote(note: NoteRelation) {
        viewModelScope.launch {
            runCancellableCatching {
                val dn = draftNoteRepository.save(note.toDraftNote()).getOrThrow()
                noteRepository.delete(note.note.id).getOrThrow()
                dn
            }.onSuccess {
                _openNoteEditorEvent.tryEmit(it)
            }.onFailure { t ->
                logger.error("削除に失敗しました", t)
            }
        }

    }

    fun vote(noteId: Note.Id?, poll: Poll?, choice: Poll.Choice?) {
        if (noteId == null || poll == null || choice == null) {
            return
        }
        if (poll.canVote) {
            viewModelScope.launch {
                noteRepository.vote(noteId, choice).onSuccess {
                    logger.debug("投票に成功しました")
                }.onFailure {
                    logger.error("投票に失敗しました", it)
                }
            }
        }
    }

    fun toggleReblog(noteId: Note.Id) {
        viewModelScope.launch {
            noteRepository.find(noteId).mapCancellableCatching {
                when(val type = it.type) {
                    is Note.Type.Mastodon -> {
                        if (type.reblogged == true) {
                            noteRepository.unrenote(noteId)
                        } else {
                            noteRepository.renote(noteId)
                        }
                    }
                    is Note.Type.Misskey -> {
                        return@launch
                    }
                }
            }
        }
    }

    fun onToggleFavoriteUseCase(note: Note) {
        viewModelScope.launch {
            toggleFavoriteUseCase(note.id).onFailure {
                logger.error("favoriteに失敗", it)
            }
        }
    }

    fun translate(noteId: Note.Id) {
        viewModelScope.launch {
            translationStore.translate(noteId)
        }
    }

}