package net.pantasystem.milktea.data.model.streaming

import net.pantasystem.milktea.data.api.misskey.users.toUser
import net.pantasystem.milktea.data.model.account.Account
import net.pantasystem.milktea.data.model.users.UserDataSource
import net.pantasystem.milktea.data.streaming.ChannelBody

/**
 * StreamingAPIのMainイベントを各種DataSourceに適応します。
 */
class StreamingMainUserEventDispatcher(
    private val userDataSource: UserDataSource,
) : StreamingMainEventDispatcher{

    override suspend fun dispatch(account: Account, mainEvent: ChannelBody.Main): Boolean {
        return (mainEvent as? ChannelBody.Main.HavingUserBody)?.let {
            userDataSource.add(mainEvent.body.toUser(account, true))
        } != null
    }
}