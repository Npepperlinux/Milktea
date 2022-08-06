package jp.panta.misskeyandroidclient.ui

import android.view.View
import android.widget.TextView
import androidx.databinding.BindingAdapter
import dagger.hilt.android.EntryPointAccessors
import jp.panta.misskeyandroidclient.ui.text.CustomEmojiDecorator
import net.pantasystem.milktea.model.user.User


object UserTextHelper {

    @JvmStatic
    @BindingAdapter("mainNameView", "subNameView", "user")
    fun View.setUserInfo(mainNameView: TextView?, subNameView: TextView?, user: User?) {
        user ?: return
        val isUserNameDefault = EntryPointAccessors.fromApplication(
            this.context.applicationContext,
            BindingProvider::class.java
        )
            .settingStore()
            .isUserNameDefault
        val userName: TextView?
        val name: TextView?
        if (isUserNameDefault) {
            name = subNameView
            userName = mainNameView
        } else {
            name = mainNameView
            userName = subNameView
        }
        name?.let {
            name.text = CustomEmojiDecorator().decorate(user.emojis, user.displayName, name)
        }
        userName?.let {
            userName.text = user.displayUserName
        }
    }
}