package jp.panta.misskeyandroidclient.model.gallery

import jp.panta.misskeyandroidclient.model.account.Account
import jp.panta.misskeyandroidclient.model.file.File

class UpdateGalleryPost (
    val id: GalleryPost.Id,
    val title: String,
    val files: List<File>,
    val description: String?,
    val isSensitive: Boolean,
    val tags: List<String>
)