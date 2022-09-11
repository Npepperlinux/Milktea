package net.pantasystem.milktea.media

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView

class PlayerFragment : Fragment(R.layout.fragment_player){

    companion object{
        const val TAG = "PlayerFragment"
        private const val EXTRA_MEDIA_SOURCE_URI = "net.pantasystem.milktea.media.PlayerFragment.EXTRA_MEDIA_SOURCE_URI"
        private const val EXTRA_INDEX = "net.pantasystem.milktea.media.PlayerFragment.extra.INDEX"

        fun newInstance(index: Int, uri: String): PlayerFragment {
            return PlayerFragment().apply{
                arguments = Bundle().apply{
                    putString(EXTRA_MEDIA_SOURCE_URI, uri)
                    putInt(EXTRA_INDEX, index)
                }
            }
        }

        fun newInstance(index: Int, uri: Uri): PlayerFragment {
            return PlayerFragment().apply{
                arguments = Bundle().apply{
                    putString(EXTRA_MEDIA_SOURCE_URI, uri.toString())
                    putInt(EXTRA_INDEX, index)
                }
            }
        }
    }

    private var mExoPlayer: ExoPlayer? = null
    private var index: Int = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val extraUri = arguments?.getString(EXTRA_MEDIA_SOURCE_URI)
        Log.d(TAG, "extraUri: $extraUri")
        val uri = if(extraUri == null) null else Uri.parse(extraUri)

        index = arguments?.getInt(EXTRA_INDEX) ?: 0

        if(uri == null){
            Log.e(TAG, "uri must not null")
            return
        }

        val simpleExoPlayer = ExoPlayer.Builder(requireContext()).build()
        view.findViewById<PlayerView>(R.id.player_view).player = simpleExoPlayer

        simpleExoPlayer.setMediaItem(MediaItem.fromUri(uri))
        simpleExoPlayer.prepare()
        simpleExoPlayer.play()

        mExoPlayer = simpleExoPlayer

    }

    override fun onResume() {
        super.onResume()

        val activity = requireActivity()
        if(activity is MediaActivity){
            activity.setCurrentFileIndex(index)
        }
    }

    override fun onStop(){
        super.onStop()

        mExoPlayer?.playWhenReady = false
    }

    override fun onDestroyView() {
        super.onDestroyView()

        mExoPlayer?.release()
    }
}