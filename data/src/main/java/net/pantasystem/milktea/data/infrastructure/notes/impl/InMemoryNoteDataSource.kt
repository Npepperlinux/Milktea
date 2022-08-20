package net.pantasystem.milktea.data.infrastructure.notes.impl

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.pantasystem.milktea.common.Logger
import net.pantasystem.milktea.model.AddResult
import net.pantasystem.milktea.model.notes.*
import net.pantasystem.milktea.model.user.User
import javax.inject.Inject

class InMemoryNoteDataSource @Inject constructor(
    loggerFactory: Logger.Factory
): NoteDataSource {

    val logger = loggerFactory.create("InMemoryNoteRepository")

    private var notes = mapOf<Note.Id, Note>()

    private val mutex = Mutex()
    private val listenersLock = Mutex()

    private var listeners = setOf<NoteDataSource.Listener>()

    private val _state = MutableStateFlow(NoteDataSourceState(emptyMap()))

    private var deleteNoteIds = mutableSetOf<Note.Id>()

    override val state: StateFlow<NoteDataSourceState>
        get() = _state

    init {
        addEventListener {
            _state.update {
                it.copy(notes)
            }
        }
    }

    override fun addEventListener(listener: NoteDataSource.Listener): Unit = runBlocking {
        listeners = listeners.toMutableSet().apply {
            add(listener)
        }
    }


    override suspend fun get(noteId: Note.Id): Result<Note> = runCatching {
        mutex.withLock{
            if (deleteNoteIds.contains(noteId)) {
                throw NoteDeletedException(noteId)
            }
            notes[noteId]
                ?: throw NoteNotFoundException(noteId)
        }
    }

    override suspend fun getIn(noteIds: List<Note.Id>): Result<List<Note>> = runCatching {
        noteIds.mapNotNull { noteId ->
            notes[noteId]
        }
    }

    /**
     * @param note 追加するノート
     * @return ノートが新たに追加されるとtrue、上書きされた場合はfalseが返されます。
     */
    override suspend fun add(note: Note): Result<AddResult> = runCatching {
       createOrUpdate(note).also {
           if(it == AddResult.Created) {
               publish(NoteDataSource.Event.Created(note.id, note))
           }else if(it == AddResult.Updated) {
               publish(NoteDataSource.Event.Updated(note.id, note))
           }
       }
    }

    override suspend fun addAll(notes: List<Note>): Result<List<AddResult>> = runCatching {
        notes.map{
            this.add(it).getOrElse {
                AddResult.Canceled
            }
        }
    }

    /**
     * @param noteId 削除するNoteのid
     * @return 実際に削除されるとtrue、そもそも存在していなかった場合にはfalseが返されます
     */
    override suspend fun remove(noteId: Note.Id): Result<Boolean> = runCatching {
        suspend fun delete(noteId: Note.Id): Boolean {
            mutex.withLock{
                val n = this.notes[noteId]
                notes = notes - noteId
                deleteNoteIds.add(noteId)
                return n != null
            }
        }

        delete(noteId).also {
            if(it) {
                publish(NoteDataSource.Event.Deleted(noteId))
            }
        }

    }

    override suspend fun removeByUserId(userId: User.Id): Result<Int> = runCatching {
        val result = mutex.withLock {
            notes.values.filter {
                it.userId == userId
            }.mapNotNull {
                if(this.remove(it.id).getOrThrow()) {
                    it
                }else null
            }
        }
        result.mapNotNull {
            remove(it.id).getOrNull()
        }.count ()
    }

    private suspend fun createOrUpdate(note: Note): AddResult {
        mutex.withLock{
            val n = this.notes[note.id]
            notes = notes + (note.id to note)

            deleteNoteIds.remove(note.id)
            return if(n == null){
                AddResult.Created
            } else {
                AddResult.Updated
            }
        }
    }

    private fun publish(ev: NoteDataSource.Event) = runBlocking {
        listenersLock.withLock {
            listeners.forEach {
                it.on(ev)
            }
        }
    }

}