package jp.panta.misskeyandroidclient.ui.notes.view

import android.view.View
import android.widget.TextView
import androidx.databinding.BindingAdapter
import dagger.hilt.android.EntryPointAccessors
import jp.panta.misskeyandroidclient.R
import jp.panta.misskeyandroidclient.ui.BindingProvider
import jp.panta.misskeyandroidclient.ui.notes.viewmodel.PlaneNoteViewData
import jp.panta.misskeyandroidclient.ui.text.CustomEmojiDecorator
import net.pantasystem.milktea.model.notes.NoteRelation


object StatusMessageHelper {

    @JvmStatic
    @BindingAdapter("statusMessageTargetViewNote")
    fun TextView.setStatusMessage(statusMessageTargetViewNote: PlaneNoteViewData){
        val settingStore = EntryPointAccessors.fromApplication(context.applicationContext, BindingProvider::class.java)
            .settingStore()
        val isUserNameDefault = settingStore.isUserNameDefault
        val note = statusMessageTargetViewNote.note
        val name = if(isUserNameDefault){
            note.user.displayUserName
        }else{
            note.user.displayName
        }
        val context = this.context
        val message = when{
            note.reply != null ->{
                context.getString(R.string.replied_by, name)
            }
            note.note.isRenote() && !note.note.hasContent() ->{
                context.getString(R.string.renoted_by, name)
            }
            note is NoteRelation.Featured -> {
                context.getString(R.string.featured)
            }
            note is NoteRelation.Promotion -> {
                context.getString(R.string.promotion)
            }

            else -> null
        }
        if(message == null) {
            this.visibility = View.GONE
            return
        }
        this.visibility = View.VISIBLE
        if(isUserNameDefault){
            this.text = message
        }else{
            this.text = CustomEmojiDecorator().decorate(note.user.emojis, message, this)
        }
    }
}