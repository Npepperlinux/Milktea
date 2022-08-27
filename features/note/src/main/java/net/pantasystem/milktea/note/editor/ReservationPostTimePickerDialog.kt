package net.pantasystem.milktea.note.editor

import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.TimePicker
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.datetime.Instant
import net.pantasystem.milktea.note.editor.viewmodel.NoteEditorViewModel
import java.util.*


@AndroidEntryPoint
class ReservationPostTimePickerDialog : AppCompatDialogFragment(), TimePickerDialog.OnTimeSetListener{

    private val mViewModel: NoteEditorViewModel by activityViewModels()
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {


        val viewModel = mViewModel

        val date = viewModel.reservationPostingAt.value?: Date()
        val c = Calendar.getInstance()
        c.time = date
        return TimePickerDialog(requireActivity(), this, c[Calendar.HOUR], c[Calendar.MINUTE], true)

    }

    override fun onTimeSet(p0: TimePicker?, p1: Int, p2: Int) {
        val date = mViewModel.reservationPostingAt.value ?: Date()
        val c = Calendar.getInstance()
        c.time = date
        c[Calendar.HOUR] = p1
        c[Calendar.MINUTE] = p2
        mViewModel.updateState(
            mViewModel.state.value.copy(
                reservationPostingAt = Instant.fromEpochMilliseconds(c.time.time)
            )
        )

    }
}