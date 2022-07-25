package jp.panta.misskeyandroidclient.ui.notes.view

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import jp.panta.misskeyandroidclient.NoteDetailActivity
import jp.panta.misskeyandroidclient.R
import jp.panta.misskeyandroidclient.databinding.FragmentShareBottomSheetBinding
import jp.panta.misskeyandroidclient.ui.notes.viewmodel.NotesViewModel
import net.pantasystem.milktea.model.user.report.toReport

class ShareBottomSheetDialog : BottomSheetDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        val view = View.inflate(context, R.layout.fragment_share_bottom_sheet, null)
        dialog.setContentView(view)
        val dataBinding = DataBindingUtil.bind<FragmentShareBottomSheetBinding>(view)
        dialog.setContentView(view)
        if (dataBinding == null) {
            Log.e("ShareBottomSheetDialog", "Bindingを取得するのに失敗しました")
            dismiss()
            return dialog
        }
        val viewModel = ViewModelProvider(requireActivity()).get(NotesViewModel::class.java)
        val note = viewModel.shareTarget.event

        dataBinding.notesViewModel = viewModel
        dataBinding.executePendingBindings()
        dataBinding.lifecycleOwner = this

        dataBinding.showDetail.setOnClickListener {
            requireActivity().startActivity(
                NoteDetailActivity.newIntent(
                    requireActivity(),
                    noteId = note?.toShowNote?.note?.id!!
                )
            )
            dismiss()
        }

        dataBinding.removeFavorite.setOnClickListener {
            viewModel.deleteFavorite()
            dismiss()
        }

        dataBinding.addFavorite.setOnClickListener {
            viewModel.addFavorite()
            dismiss()
        }

        dataBinding.shareNote.setOnClickListener {
            val baseUrl = viewModel.getAccount()?.instanceDomain
            val url = "$baseUrl/notes/${note?.id?.noteId}"
            val intent = Intent().apply {
                action = ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share)))
            dismiss()
        }

        dataBinding.translateText.setOnClickListener {
            viewModel.translate(note?.toShowNote?.note?.id!!)
            dismiss()
        }

        val clipboardManager =
            context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?

        dataBinding.copyContent.setOnClickListener {
            if (clipboardManager == null || note == null) {
                dismiss()
                return@setOnClickListener
            }
            clipboardManager.setPrimaryClip(ClipData.newPlainText("", note.toShowNote.note.text))
            dismiss()
        }

        dataBinding.copyUrl.setOnClickListener {
            if (clipboardManager == null || note == null) {
                dismiss()
                return@setOnClickListener
            }
            val baseUrl = viewModel.getAccount()?.instanceDomain
            clipboardManager.setPrimaryClip(
                ClipData.newPlainText(
                    "",
                    "$baseUrl/notes/${note.id.noteId}"
                )
            )
            dismiss()

        }

        dataBinding.removeMyNote.setOnClickListener {
            viewModel.confirmDeletionEvent.event = viewModel.shareTarget.event
            dismiss()
        }

        dataBinding.deleteAndEditNoteButton.setOnClickListener {
            note?.let { n ->
                viewModel.confirmDeleteAndEditEvent.event = n
            }
            dismiss()
        }

        dataBinding.reportButton.setOnClickListener {
            val baseUrl = viewModel.getAccount()?.instanceDomain

            note?.let {
                val report = it.toShowNote.toReport(baseUrl!!)
                viewModel.confirmReportEvent.event = report
            }
            dismiss()
        }
        return dialog
    }

}