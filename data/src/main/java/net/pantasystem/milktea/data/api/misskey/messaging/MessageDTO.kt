package net.pantasystem.milktea.data.api.misskey.messaging

import jp.panta.misskeyandroidclient.mfm.MFMParser
import jp.panta.misskeyandroidclient.mfm.Root
import net.pantasystem.milktea.data.api.misskey.drive.FilePropertyDTO
import net.pantasystem.milktea.data.api.misskey.groups.GroupDTO
import net.pantasystem.milktea.data.api.misskey.users.UserDTO
import net.pantasystem.milktea.data.api.misskey.users.toUser
import kotlinx.datetime.Instant
import kotlinx.datetime.serializers.InstantIso8601Serializer
import kotlinx.serialization.Serializable
import net.pantasystem.milktea.data.model.account.Account
import net.pantasystem.milktea.data.model.emoji.Emoji
import net.pantasystem.milktea.data.model.messaging.Message
import net.pantasystem.milktea.data.model.users.User
import java.io.Serializable as JavaSerializable
import jp.panta.misskeyandroidclient.model.group.Group as GroupEntity

@Serializable
data class MessageDTO(
    val id: String,
    @Serializable(with = InstantIso8601Serializer::class) val createdAt: Instant,
    val text: String? = null,
    val userId: String,
    val user: UserDTO,
    val recipientId: String? = null,
    val recipient: UserDTO? = null,
    val groupId: String? = null,
    val group: GroupDTO? = null,
    val fileId: String? = null,
    val file: FilePropertyDTO? = null,
    val isRead: Boolean,
    val emojis: List<Emoji>? = null
): JavaSerializable{


    val textNode: Root?
        get() {
            return MFMParser.parse(text, emojis)
        }
}

fun MessageDTO.entities(account: Account): Pair<Message, List<User>> {
    val list = mutableListOf<User>()
    val id = Message.Id(account.accountId, id)
    list.add(user.toUser(account, false))
    val message = if(groupId == null) {
        require(recipientId != null)
        Message.Direct(
            id,
            createdAt,
            text,
            User.Id(account.accountId, userId),
            fileId,
            file?.toFileProperty(account),
            isRead,
            emojis?: emptyList(),
            recipientId = User.Id(account.accountId, recipientId)
        )
    }else{
        Message.Group(
            id,
            createdAt,
            text,
            User.Id(account.accountId, userId),
            fileId,
            file?.toFileProperty(account),
            isRead,
            emojis?: emptyList(),
            GroupEntity.Id(account.accountId, groupId),
        )
    }
    return message to list
}