package net.pantasystem.milktea.data.infrastructure.gallery

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import net.pantasystem.milktea.api.misskey.v12_75_0.*
import net.pantasystem.milktea.common.Encryption
import net.pantasystem.milktea.common.throwIfHasError
import net.pantasystem.milktea.data.api.misskey.MisskeyAPIProvider
import net.pantasystem.milktea.data.infrastructure.drive.FileUploaderProvider
import net.pantasystem.milktea.data.infrastructure.toEntity
import net.pantasystem.milktea.data.infrastructure.toFileProperty
import net.pantasystem.milktea.model.account.Account
import net.pantasystem.milktea.model.account.AccountRepository
import net.pantasystem.milktea.model.account.UnauthorizedException
import net.pantasystem.milktea.model.drive.FilePropertyDataSource
import net.pantasystem.milktea.model.file.AppFile
import net.pantasystem.milktea.model.gallery.*
import net.pantasystem.milktea.model.gallery.GalleryPost
import net.pantasystem.milktea.model.instance.IllegalVersionException
import net.pantasystem.milktea.model.user.UserDataSource
import javax.inject.Inject
import net.pantasystem.milktea.api.misskey.v12_75_0.CreateGallery as CreateGalleryDTO



class GalleryRepositoryImpl @Inject constructor(
    private val misskeyAPIProvider: MisskeyAPIProvider,
    private val galleryDataSource: GalleryDataSource,
    private val encryption: Encryption,
    private val fileUploaderProvider: FileUploaderProvider,
    private val userDataSource: UserDataSource,
    private val filePropertyDataSource: FilePropertyDataSource,
    private val accountRepository: AccountRepository
) : GalleryRepository {


    override suspend fun create(createGalleryPost: CreateGalleryPost): GalleryPost {
        val files = coroutineScope {
            createGalleryPost.files.map {
                async {
                    when(it) {
                        is AppFile.Remote -> it.id
                        is AppFile.Local -> {
                            fileUploaderProvider.get(createGalleryPost.author).upload(it, true).let {
                                it.toFileProperty(createGalleryPost.author).also { entity ->
                                    filePropertyDataSource.add(entity)
                                }
                            }.id
                        }
                    }
                }
            }.awaitAll()
        }

        val created = getMisskeyAPI(createGalleryPost.author).createGallery(
            CreateGalleryDTO(
                createGalleryPost.author.getI(encryption),
                createGalleryPost.title,
                createGalleryPost.description,
                fileIds = files.map {
                    it.fileId
                },
                isSensitive = createGalleryPost.isSensitive
            )
        ).throwIfHasError().body()
        requireNotNull(created)

        val gallery = created.toEntity(createGalleryPost.author, filePropertyDataSource, userDataSource)
        galleryDataSource.add(gallery)
        return gallery
    }

    override suspend fun delete(id: GalleryPost.Id) {
        val account = accountRepository.get(id.accountId).getOrThrow()
        val res = getMisskeyAPI(account).deleteGallery(Delete(i = account.getI(encryption), postId = id.galleryId)).throwIfHasError()
        require(res.isSuccessful)
    }

    override suspend fun find(id: GalleryPost.Id): GalleryPost {
        try {
            return galleryDataSource.find(id).getOrThrow()
        }catch (e: GalleryNotFoundException) {

        }
        val account = accountRepository.get(id.accountId).getOrThrow()
        val res = getMisskeyAPI(account).showGallery(Show(i = account.getI(encryption), postId = id.galleryId)).throwIfHasError()
        val body = res.body()
        requireNotNull(body)
        val gallery = body.toEntity(account, filePropertyDataSource, userDataSource)
        galleryDataSource.add(gallery)
        return gallery
    }

    override suspend fun like(id: GalleryPost.Id) {
        val gallery = find(id) as? GalleryPost.Authenticated
            ?: throw UnauthorizedException()
        val account = accountRepository.get(id.accountId).getOrThrow()
        getMisskeyAPI(account).likeGallery(Like(i = account.getI(encryption), postId = id.galleryId)).throwIfHasError()
        galleryDataSource.add(gallery.copy(isLiked = true))
    }

    override suspend fun unlike(id: GalleryPost.Id) {
        val gallery = find(id) as? GalleryPost.Authenticated
            ?: throw UnauthorizedException()
        val account = accountRepository.get(id.accountId).getOrThrow()
        getMisskeyAPI(account).unlikeGallery(UnLike(i = account.getI(encryption), postId = id.galleryId)).throwIfHasError()
        galleryDataSource.add(gallery.copy(isLiked = false))

    }

    override suspend fun update(updateGalleryPost: UpdateGalleryPost): GalleryPost {
        val account = accountRepository.get(updateGalleryPost.id.accountId).getOrThrow()
        val files = coroutineScope {
            updateGalleryPost.files.map {
                async {
                    when(it) {
                        is AppFile.Remote -> it.id.fileId
                        is AppFile.Local -> {
                            fileUploaderProvider.get(account).upload(it, true).also {
                                filePropertyDataSource.add(it.toFileProperty(account))
                            }.id
                        }
                    }
                }
            }.awaitAll()
        }
        val body = getMisskeyAPI(account).updateGallery(
            Update(
                i = account.getI(encryption),
                postId = updateGalleryPost.id.galleryId,
                description = updateGalleryPost.description,
                title = updateGalleryPost.title,
                fileIds = files,
                isSensitive = updateGalleryPost.isSensitive
            )
        ).throwIfHasError().body()
        requireNotNull(body)
        val gallery = body.toEntity(account, filePropertyDataSource, userDataSource)
        galleryDataSource.add(gallery)
        return gallery
    }

    private fun getMisskeyAPI(account: Account) : MisskeyAPIV1275 {
        return misskeyAPIProvider.get(account.instanceDomain) as? MisskeyAPIV1275
            ?: throw IllegalVersionException()
    }
}