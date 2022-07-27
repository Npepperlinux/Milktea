package jp.panta.misskeyandroidclient.ui.notes.view

import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import jp.panta.misskeyandroidclient.NoteEditorActivity
import jp.panta.misskeyandroidclient.R
import jp.panta.misskeyandroidclient.ui.confirm.ConfirmDialog
import jp.panta.misskeyandroidclient.ui.notes.viewmodel.NotesViewModel
import jp.panta.misskeyandroidclient.ui.notes.viewmodel.PlaneNoteViewData
import jp.panta.misskeyandroidclient.ui.users.ReportDialog
import jp.panta.misskeyandroidclient.viewmodel.confirm.ConfirmViewModel
import net.pantasystem.milktea.data.infrastructure.confirm.ConfirmCommand
import net.pantasystem.milktea.data.infrastructure.confirm.ConfirmEvent
import net.pantasystem.milktea.data.infrastructure.confirm.ResultType
import net.pantasystem.milktea.data.infrastructure.settings.SettingStore
import net.pantasystem.milktea.model.notes.Note
import net.pantasystem.milktea.model.notes.NoteRelation
import net.pantasystem.milktea.model.notes.draft.DraftNote
import net.pantasystem.milktea.model.user.report.Report


class ActionNoteHandler(
    val activity: AppCompatActivity,
    private val mNotesViewModel: NotesViewModel,
    val confirmViewModel: ConfirmViewModel,
    val settingStore: SettingStore,

    ) {


    private val shareTargetObserver = Observer<PlaneNoteViewData> {
        Log.d("MainActivity", "share clicked :$it")
        ShareBottomSheetDialog().show(activity.supportFragmentManager, "MainActivity")
    }

    private val statusMessageObserver = Observer<String> {
        Toast.makeText(activity, it, Toast.LENGTH_LONG).show()
    }

    private val quoteRenoteTargetObserver = Observer<Note> {
        val intent = NoteEditorActivity.newBundle(activity, quoteTo = it.id)
        activity.startActivity(intent)
    }



    private val openNoteEditor = Observer<DraftNote?> { note ->
        activity.startActivity(
            NoteEditorActivity.newBundle(
                activity,
                draftNoteId = note.draftNoteId
            )
        )
    }

    private val confirmDeletionEventObserver = Observer<PlaneNoteViewData> {
        confirmViewModel.confirmEvent.event = ConfirmCommand(
            activity.getString(R.string.confirm_deletion),
            null,
            eventType = "delete_note",
            args = it.toShowNote.note
        )
    }

    private val confirmDeleteAndEditEventObserver = Observer<PlaneNoteViewData> {
        confirmViewModel.confirmEvent.event = ConfirmCommand(
            null,
            activity.getString(R.string.confirm_delete_and_edit_note_description),
            eventType = "delete_and_edit_note",
            args = it.toShowNote
        )
    }

    private val confirmCommandEventObserver = Observer<ConfirmCommand> {
        ConfirmDialog().show(activity.supportFragmentManager, "")
    }

    private val confirmedEventObserver = Observer<ConfirmEvent> {
        if (it.resultType == ResultType.NEGATIVE) {
            return@Observer
        }
        when (it.eventType) {
            "delete_note" -> {
                if (it.args is Note) {
                    mNotesViewModel.removeNote((it.args as Note).id)
                }
            }
            "delete_and_edit_note" -> {
                if (it.args is NoteRelation) {
                    mNotesViewModel.removeAndEditNote(it.args as NoteRelation)
                }
            }
        }
    }

    private val reportDialogObserver: (Report?) -> Unit = { report ->
        report?.let {
            ReportDialog.newInstance(report.userId, report.comment)
                .show(activity.supportFragmentManager, "")
        }
    }


    fun initViewModelListener() {


        mNotesViewModel.shareTarget.removeObserver(shareTargetObserver)
        mNotesViewModel.shareTarget.observe(activity, shareTargetObserver)


        mNotesViewModel.statusMessage.removeObserver(statusMessageObserver)
        mNotesViewModel.statusMessage.observe(activity, statusMessageObserver)

        mNotesViewModel.quoteRenoteTarget.removeObserver(quoteRenoteTargetObserver)
        mNotesViewModel.quoteRenoteTarget.observe(activity, quoteRenoteTargetObserver)

        mNotesViewModel.openNoteEditor.removeObserver(openNoteEditor)
        mNotesViewModel.openNoteEditor.observe(activity, openNoteEditor)


        mNotesViewModel.confirmDeletionEvent.removeObserver(confirmDeletionEventObserver)
        mNotesViewModel.confirmDeletionEvent.observe(activity, confirmDeletionEventObserver)

        mNotesViewModel.confirmDeleteAndEditEvent.removeObserver(confirmDeleteAndEditEventObserver)
        mNotesViewModel.confirmDeleteAndEditEvent.observe(
            activity,
            confirmDeleteAndEditEventObserver
        )

        confirmViewModel.confirmEvent.removeObserver(confirmCommandEventObserver)
        confirmViewModel.confirmEvent.observe(activity, confirmCommandEventObserver)

        confirmViewModel.confirmedEvent.removeObserver(confirmedEventObserver)
        confirmViewModel.confirmedEvent.observe(activity, confirmedEventObserver)

        mNotesViewModel.confirmReportEvent.removeObserver(reportDialogObserver)
        mNotesViewModel.confirmReportEvent.observe(activity, reportDialogObserver)

    }
}