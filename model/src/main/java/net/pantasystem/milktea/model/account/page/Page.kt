package net.pantasystem.milktea.model.account.page

import android.os.Parcelable
import androidx.room.*
import kotlinx.parcelize.Parcelize
import java.io.Serializable

@Entity(
    tableName = "page_table",
    indices = [Index("weight"), Index("accountId")]
)
@Parcelize
data class Page(
    var accountId: Long,
    val title: String,
    var weight: Int,
    @Embedded val pageParams: PageParams,
    @PrimaryKey(autoGenerate = true) var pageId: Long
) : Serializable, Parcelable {

    constructor(accountId: Long, title: String, weight: Int, pageable: Pageable, pageId: Long = 0)
            : this(accountId, title, weight, pageable.toParams(), pageId)


    @Ignore
    fun pageable(): Pageable {
        return pageParams.toPageable()
    }

    fun isEqualEntity(page: Page): Boolean {
        if (page.accountId != this.accountId) {
            return false
        }
        if (page.pageId == this.pageId) {
            return true
        }
        return page.pageable() == pageable()
    }
}