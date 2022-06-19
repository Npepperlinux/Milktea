package net.pantasystem.milktea.data.gettters

import net.pantasystem.milktea.common.Logger
import net.pantasystem.milktea.model.drive.FilePropertyDataSource
import net.pantasystem.milktea.model.notes.Note
import net.pantasystem.milktea.model.notes.NoteDeletedException
import net.pantasystem.milktea.model.notes.NoteRelation
import net.pantasystem.milktea.model.notes.NoteRepository
import net.pantasystem.milktea.model.user.User
import net.pantasystem.milktea.model.user.UserDataSource


class NoteRelationGetter(
    private val noteRepository: NoteRepository,
    private val userDataSource: UserDataSource,
    private val filePropertyDataSource: FilePropertyDataSource,
    private val logger: Logger
) {

    suspend fun get(
        noteId: Note.Id,
        deep: Boolean = true,
        featuredId: String? = null,
        promotionId: String? = null,
        usersMap: Map<User.Id, User> = emptyMap(),
        notesMap: Map<Note.Id, Note> = emptyMap(),
    ): NoteRelation? {
        return runCatching {
            notesMap.getOrElse(noteId) {
                noteRepository.find(noteId)
            }
        }.onFailure {
            if (it !is NoteDeletedException) {
                logger.error("ノートの取得に失敗しました", e = it)
            }
        }.getOrNull()?.let {
            get(
                it,
                deep,
                featuredId = featuredId,
                promotionId = promotionId,
                usersMap = usersMap,
                notesMap = notesMap
            )
        }
    }

    suspend fun getIn(noteIds: List<Note.Id>): List<NoteRelation> {
        val notes = noteRepository.findIn(noteIds)
        val users = userDataSource.getIn(notes.map { it.userId }).associateBy {
            it.id
        }
        val noteMap = notes.associateBy { it.id }
        return notes.map {
            get(
                it,
                true,
                featuredId = null,
                promotionId = null,
                usersMap = users,
                notesMap = noteMap
            )
        }
    }

    suspend fun get(
        accountId: Long,
        noteId: String,
        featuredId: String? = null,
        promotionId: String? = null
    ): NoteRelation? {
        return get(Note.Id(accountId, noteId), featuredId = featuredId, promotionId = promotionId)
    }


    suspend fun get(
        note: Note,
        deep: Boolean = true,
        featuredId: String? = null,
        promotionId: String? = null,
        usersMap: Map<User.Id, User> = emptyMap(),
        notesMap: Map<Note.Id, Note> = emptyMap(),
    ): NoteRelation {
        val user = usersMap.getOrElse(note.userId) {
            userDataSource.get(note.userId)
        }

        val renote = if (deep) {
            note.renoteId?.let {
                get(it, false)
            }
        } else null
        val reply = if (deep) {
            note.replyId?.let {
                get(it, false, notesMap = notesMap, usersMap = usersMap)
            }
        } else null

        if (featuredId != null) {
            return NoteRelation.Featured(
                note,
                user,
                renote,
                reply,
                note.fileIds?.let { filePropertyDataSource.findIn(it) },
                featuredId
            )
        }

        if (promotionId != null) {
            return NoteRelation.Promotion(
                note,
                user,
                renote,
                reply,
                note.fileIds?.let { filePropertyDataSource.findIn(it) },
                promotionId
            )
        }
        return NoteRelation.Normal(
            note = note,
            user = user,
            renote = renote,
            reply = reply,
            note.fileIds?.let { filePropertyDataSource.findIn(it) },
        )
    }
}