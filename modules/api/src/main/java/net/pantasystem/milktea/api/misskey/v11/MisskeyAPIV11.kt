package net.pantasystem.milktea.api.misskey.v11

import net.pantasystem.milktea.api.misskey.I
import net.pantasystem.milktea.api.misskey.groups.*
import net.pantasystem.milktea.api.misskey.list.*
import net.pantasystem.milktea.api.misskey.notification.NotificationDTO
import net.pantasystem.milktea.api.misskey.MisskeyAPI
import net.pantasystem.milktea.api.misskey.app.CreateApp
import net.pantasystem.milktea.api.misskey.app.ShowApp
import net.pantasystem.milktea.api.misskey.auth.App
import net.pantasystem.milktea.api.misskey.drive.*
import net.pantasystem.milktea.api.misskey.favorite.Favorite
import net.pantasystem.milktea.api.misskey.hashtag.RequestHashTagList

import net.pantasystem.milktea.api.misskey.messaging.MessageDTO
import net.pantasystem.milktea.api.misskey.messaging.MessageAction
import net.pantasystem.milktea.api.misskey.messaging.RequestMessage
import net.pantasystem.milktea.api.misskey.notes.*
import net.pantasystem.milktea.api.misskey.notes.favorite.CreateFavorite
import net.pantasystem.milktea.api.misskey.notes.favorite.DeleteFavorite
import net.pantasystem.milktea.model.messaging.RequestMessageHistory
import net.pantasystem.milktea.model.notes.poll.Vote
import net.pantasystem.milktea.api.misskey.notification.NotificationRequest
import net.pantasystem.milktea.api.misskey.notes.reaction.ReactionHistoryDTO
import net.pantasystem.milktea.api.misskey.notes.reaction.RequestReactionHistoryDTO
import net.pantasystem.milktea.api.misskey.notes.translation.Translate
import net.pantasystem.milktea.api.misskey.notes.translation.TranslationResult
import net.pantasystem.milktea.api.misskey.register.Subscription
import net.pantasystem.milktea.api.misskey.register.SubscriptionState
import net.pantasystem.milktea.api.misskey.register.UnSubscription
import net.pantasystem.milktea.api.misskey.users.*
import net.pantasystem.milktea.api.misskey.users.report.ReportDTO
import net.pantasystem.milktea.model.drive.Directory
import net.pantasystem.milktea.model.hashtag.HashTag
import net.pantasystem.milktea.model.instance.Meta
import net.pantasystem.milktea.model.instance.RequestMeta
import retrofit2.Response

open class MisskeyAPIV11(private val misskeyAPI: MisskeyAPI, private val apiDiff: MisskeyAPIV11Diff): MisskeyAPI by misskeyAPI{

    override suspend fun blockUser(requestUser: RequestUser): Response<Unit> = misskeyAPI.blockUser(requestUser)
    override suspend fun children(noteRequest: NoteRequest): Response<List<NoteDTO>> = misskeyAPI.children(noteRequest)
    override suspend fun conversation(noteRequest: NoteRequest): Response<List<NoteDTO>> = misskeyAPI.conversation(noteRequest)
    override suspend fun create(createNote: CreateNote): Response<CreateNote.Response> = misskeyAPI.create(createNote)
    override suspend fun createApp(createApp: CreateApp): Response<App> = misskeyAPI.createApp(createApp)
    override suspend fun createFavorite(noteRequest: CreateFavorite): Response<Unit> = misskeyAPI.createFavorite(noteRequest)
    override suspend fun createFolder(createFolder: CreateFolder): Response<Directory> = misskeyAPI.createFolder(createFolder)
    override suspend fun createList(createList: CreateList): Response<UserListDTO> = misskeyAPI.createList(createList)
    override suspend fun createMessage(messageAction: MessageAction): Response<MessageDTO> = misskeyAPI.createMessage(messageAction)
    override suspend fun createReaction(reaction: CreateReactionDTO): Response<Unit> = misskeyAPI.createReaction(reaction)
    override suspend fun delete(deleteNote: DeleteNote): Response<Unit> = misskeyAPI.delete(deleteNote)
    override suspend fun deleteFavorite(noteRequest: DeleteFavorite): Response<Unit> = misskeyAPI.deleteFavorite(noteRequest)
    override suspend fun deleteList(listId: ListId): Response<Unit> = misskeyAPI.deleteList(listId)
    override suspend fun deleteMessage(messageAction: MessageAction): Response<Unit> = misskeyAPI.deleteMessage(messageAction)
    override suspend fun deleteReaction(deleteNote: DeleteNote): Response<Unit> = misskeyAPI.deleteReaction(deleteNote)
    override suspend fun favorites(noteRequest: NoteRequest): Response<List<Favorite>?> = misskeyAPI.favorites(noteRequest)
    override suspend fun featured(noteRequest: NoteRequest): Response<List<NoteDTO>?> = misskeyAPI.featured(noteRequest)
    open suspend fun followers(userRequest: RequestUser): Response<List<FollowFollowerUser>> = apiDiff.followers(userRequest)
    open suspend fun following(userRequest: RequestUser): Response<List<FollowFollowerUser>> = apiDiff.following(userRequest)
    override suspend fun getFiles(fileRequest: RequestFile): Response<List<FilePropertyDTO>> = misskeyAPI.getFiles(fileRequest)
    override suspend fun getFolders(folderRequest: RequestFolder): Response<List<Directory>> = misskeyAPI.getFolders(folderRequest)
    override suspend fun getMessageHistory(requestMessageHistory: RequestMessageHistory): Response<List<MessageDTO>> = misskeyAPI.getMessageHistory(requestMessageHistory)
    override suspend fun getMessages(requestMessage: RequestMessage): Response<List<MessageDTO>> = misskeyAPI.getMessages(requestMessage)
    override suspend fun getMeta(requestMeta: RequestMeta): Response<Meta> = misskeyAPI.getMeta(requestMeta)
    override suspend fun globalTimeline(noteRequest: NoteRequest): Response<List<NoteDTO>?> = misskeyAPI.globalTimeline(noteRequest)
    override suspend fun homeTimeline(noteRequest: NoteRequest): Response<List<NoteDTO>?> = misskeyAPI.homeTimeline(noteRequest)
    override suspend fun hybridTimeline(noteRequest: NoteRequest): Response<List<NoteDTO>?> = misskeyAPI.hybridTimeline(noteRequest)
    override suspend fun i(i: I): Response<UserDTO> = misskeyAPI.i(i)
    override suspend fun localTimeline(noteRequest: NoteRequest): Response<List<NoteDTO>?> = misskeyAPI.localTimeline(noteRequest)
    override suspend fun mentions(noteRequest: NoteRequest): Response<List<NoteDTO>?> = misskeyAPI.mentions(noteRequest)
    override suspend fun muteUser(requestUser: RequestUser): Response<Unit> = misskeyAPI.muteUser(requestUser)
    override suspend fun followUser(requestUser: RequestUser): Response<UserDTO> = misskeyAPI.followUser(requestUser)
    override suspend fun myApps(i: I): Response<List<App>> = misskeyAPI.myApps(i)
    override suspend fun noteState(noteRequest: NoteRequest): Response<NoteState> = misskeyAPI.noteState(noteRequest)
    override suspend fun notification(notificationRequest: NotificationRequest): Response<List<NotificationDTO>?> = misskeyAPI.notification(notificationRequest)
    override suspend fun pullUserFromList(listUserOperation: ListUserOperation): Response<Unit> = misskeyAPI.pullUserFromList(listUserOperation)
    override suspend fun pushUserToList(listUserOperation: ListUserOperation): Response<Unit> = misskeyAPI.pushUserToList(listUserOperation)
    override suspend fun readMessage(messageAction: MessageAction): Response<Unit> = misskeyAPI.readMessage(messageAction)
    override suspend fun searchByTag(noteRequest: NoteRequest): Response<List<NoteDTO>?> = misskeyAPI.searchByTag(noteRequest)
    override suspend fun searchNote(noteRequest: NoteRequest): Response<List<NoteDTO>?> = misskeyAPI.searchNote(noteRequest)
    override suspend fun searchUser(requestUser: RequestUser): Response<List<UserDTO>> = misskeyAPI.searchUser(requestUser)
    override suspend fun showApp(showApp: ShowApp): Response<App> = misskeyAPI.showApp(showApp)
    override suspend fun showList(listId: ListId): Response<UserListDTO> = misskeyAPI.showList(listId)
    override suspend fun showNote(requestNote: NoteRequest) = misskeyAPI.showNote(requestNote)
    override suspend fun showUser(requestUser: RequestUser): Response<UserDTO> = misskeyAPI.showUser(requestUser)
    override suspend fun unFollowUser(requestUser: RequestUser): Response<UserDTO> = misskeyAPI.unFollowUser(requestUser)
    override suspend fun unblockUser(requestUser: RequestUser): Response<Unit> = misskeyAPI.unblockUser(requestUser)
    override suspend fun unmuteUser(requestUser: RequestUser): Response<Unit> = misskeyAPI.unmuteUser(requestUser)
    override suspend fun updateList(createList: UpdateList): Response<Unit> = misskeyAPI.updateList(createList)
    override suspend fun userList(i: I): Response<List<UserListDTO>> = misskeyAPI.userList(i)
    override suspend fun userListTimeline(noteRequest: NoteRequest): Response<List<NoteDTO>?> = misskeyAPI.userListTimeline(noteRequest)
    override suspend fun userNotes(noteRequest: NoteRequest): Response<List<NoteDTO>?> = misskeyAPI.userNotes(noteRequest)
    override suspend fun vote(vote: Vote): Response<Unit> = misskeyAPI.vote(vote)
    override suspend fun unrenote(deleteNote: DeleteNote): Response<Unit> = misskeyAPI.unrenote(deleteNote)
    override suspend fun getHashTagList(requestHashTagList: RequestHashTagList): Response<List<HashTag>> = misskeyAPI.getHashTagList(requestHashTagList)
    override suspend fun getUsers(requestUser: RequestUser): Response<List<UserDTO>> = misskeyAPI.getUsers(requestUser)
    
    open suspend fun createGroup( body: CreateGroupDTO) : Response<GroupDTO> = apiDiff.createGroup(body)
    open suspend fun deleteGroup( body: DeleteGroupDTO) : Response<Unit> = apiDiff.deleteGroup(body)
    open suspend fun acceptInvitation( body: AcceptInvitationDTO) : Response<Unit> = apiDiff.acceptInvitation(body)
    open suspend fun rejectInvitation( body: RejectInvitationDTO) : Response<Unit> = apiDiff.rejectInvitation(body)
    open suspend fun invite( body: InviteUserDTO) : Response<Unit> = apiDiff.invite(body)
    open suspend fun joinedGroups( body: I) : Response<List<GroupDTO>> = apiDiff.joinedGroups(body)
    open suspend fun ownedGroups( body: I) : Response<List<GroupDTO>> = apiDiff.ownedGroups(body)
    open suspend fun pullUser( body: RemoveUserDTO) : Response<Unit> = apiDiff.pullUser(body)
    open suspend fun showGroup( body: ShowGroupDTO) : Response<GroupDTO> = apiDiff.showGroup(body)
    open suspend fun transferGroup( body: TransferGroupDTO) : Response<GroupDTO> = apiDiff.transferGroup(body)
    open suspend fun updateGroup( body: UpdateGroupDTO) : Response<GroupDTO> = apiDiff.updateGroup(body)
    override suspend fun reactions(body: RequestReactionHistoryDTO): Response<List<ReactionHistoryDTO>> = misskeyAPI.reactions(body)
    override suspend fun rejectFollowRequest(rejectFollowRequest: RejectFollowRequest): Response<Unit> = misskeyAPI.rejectFollowRequest(rejectFollowRequest)
    override suspend fun swRegister(subscription: Subscription): Response<SubscriptionState> = misskeyAPI.swRegister(subscription)
    override suspend fun translate(req: Translate): Response<TranslationResult> = misskeyAPI.translate(req)
    override suspend fun report(req: ReportDTO): Response<Unit> = misskeyAPI.report(req)
    override suspend fun acceptFollowRequest(followRequest: AcceptFollowRequest): Response<Unit> = misskeyAPI.acceptFollowRequest(followRequest)
    override suspend fun cancelFollowRequest(req: CancelFollow): Response<UserDTO> = misskeyAPI.cancelFollowRequest(req)
    override suspend fun renotes(req: FindRenotes): Response<List<NoteDTO>> = misskeyAPI.renotes(req)
    override suspend fun updateFile(updateFileRequest: UpdateFileDTO): Response<FilePropertyDTO> = misskeyAPI.updateFile(updateFileRequest)
    override suspend fun deleteFile(req: DeleteFileDTO): Response<Unit> = misskeyAPI.deleteFile(req)
    override suspend fun showFile(req: ShowFile): Response<FilePropertyDTO> = misskeyAPI.showFile(req)
    override suspend fun swUnRegister(unSub: UnSubscription): Response<Unit> = misskeyAPI.swUnRegister(unSub)
}