package net.pantasystem.milktea.model.notes

import kotlinx.datetime.Instant
import net.pantasystem.milktea.model.Entity
import net.pantasystem.milktea.model.EntityId
import net.pantasystem.milktea.model.app.AppType
import net.pantasystem.milktea.model.channel.Channel
import net.pantasystem.milktea.model.drive.FileProperty
import net.pantasystem.milktea.model.emoji.Emoji
import net.pantasystem.milktea.model.notes.poll.Poll
import net.pantasystem.milktea.model.notes.reaction.Reaction
import net.pantasystem.milktea.model.notes.reaction.ReactionCount
import net.pantasystem.milktea.model.user.User
import javax.annotation.concurrent.Immutable
import java.io.Serializable as JSerializable

data class Note(
    val id: Id,
    val createdAt: Instant,
    val text: String?,
    val cw: String?,
    val userId: User.Id,

    val replyId: Id?,

    val renoteId: Id?,

    val viaMobile: Boolean?,
    val visibility: Visibility,
    val localOnly: Boolean?,

    val visibleUserIds: List<User.Id>?,

    val url: String?,
    val uri: String?,
    val renoteCount: Int,
    val reactionCounts: List<ReactionCount>,
    val emojis: List<Emoji>?,
    val repliesCount: Int,
    val fileIds: List<FileProperty.Id>?,
    val poll: Poll?,
    val myReaction: String?,


    val app: AppType.Misskey?,
    val channelId: Channel.Id?,
) : Entity {
    data class Id(
        val accountId: Long,
        val noteId: String
    ) : EntityId



    /**
     * 引用リノートであるか
     */
    fun isQuote(): Boolean {
        return isRenote() && hasContent()
    }

    /**
     * リノートであるか
     */
    fun isRenote(): Boolean {
        return renoteId != null
    }

    /**
     * ファイル、投票、テキストなどのコンテンツを持っているか
     */
    fun hasContent(): Boolean {
        return !(text == null && fileIds.isNullOrEmpty() && poll == null)
    }

    fun isOwnReaction(reaction: Reaction): Boolean {
        return myReaction != null && myReaction == reaction.getName()
    }

    /**
     * この投稿がRenote可能であるかをチェックしている。
     * 既に取得できた投稿なので少なくともHome, Followers, Specifiedの公開範囲に
     * 入っていることになるので厳密なチェックは行わない。
     */
    fun canRenote(userId: User.Id): Boolean {
        return id.accountId == userId.accountId
                && (visibility is Visibility.Public
                || visibility is Visibility.Home
                || ((visibility is Visibility.Specified || visibility is Visibility.Followers) && this.userId == userId)
                )
    }
}

sealed class NoteRelation : JSerializable {
    abstract val note: Note
    abstract val user: User
    abstract val reply: NoteRelation?
    abstract val renote: NoteRelation?
    abstract val files: List<FileProperty>?

    data class Normal(
        override val note: Note,
        override val user: User,
        override val renote: NoteRelation?,
        override val reply: NoteRelation?,
        override val files: List<FileProperty>?
    ) : NoteRelation()

    data class Featured(
        override val note: Note,
        override val user: User,
        override val renote: NoteRelation?,
        override val reply: NoteRelation?,
        override val files: List<FileProperty>?,
        val featuredId: String
    ) : NoteRelation()

    data class Promotion(
        override val note: Note,
        override val user: User,
        override val renote: NoteRelation?,
        override val reply: NoteRelation?,
        override val files: List<FileProperty>?,
        val promotionId: String
    ) : NoteRelation()
}
