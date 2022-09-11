package net.pantasystem.milktea.data.infrastructure.notification.db

import androidx.room.Entity
import androidx.room.ForeignKey
import net.pantasystem.milktea.model.account.Account

@Entity(
    primaryKeys = ["accountId", "notificationId"],
    foreignKeys = [ForeignKey(
        entity = Account::class,
        parentColumns = ["accountId"],
        childColumns = ["accountId"],
        onUpdate = ForeignKey.CASCADE,
        onDelete = ForeignKey.CASCADE
    )],
    tableName = "unread_notifications_table"
)
data class UnreadNotification(
    val accountId: Long,
    val notificationId: String
)