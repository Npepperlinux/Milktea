package jp.panta.misskeyandroidclient.ui.main

import android.content.Intent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import jp.panta.misskeyandroidclient.*
import jp.panta.misskeyandroidclient.ui.settings.activities.PageSettingActivity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import net.pantasystem.milktea.data.infrastructure.settings.SettingStore
import net.pantasystem.milktea.messaging.MessagingListActivity

internal class MainActivityMenuProvider(
    val activity: MainActivity,
    val settingStore: SettingStore
) : MenuProvider {
    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        activity.setMenuTint(menu)
        menuInflater.inflate(R.menu.main, menu)

        listOf(
            menu.findItem(R.id.action_messaging),
            menu.findItem(R.id.action_notification),
            menu.findItem(R.id.action_search)
        ).forEach {
            it.isVisible = settingStore.isClassicUI
        }

    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        val idAndActivityMap = mapOf(
            R.id.action_settings to SettingsActivity::class.java,
            R.id.action_tab_setting to PageSettingActivity::class.java,
            R.id.action_notification to NotificationsActivity::class.java,
            R.id.action_messaging to MessagingListActivity::class.java,
            R.id.action_search to SearchActivity::class.java
        )

        val targetActivity = idAndActivityMap[menuItem.itemId]
            ?: return false
        activity.startActivity(Intent(activity, targetActivity))
        return true
    }
}