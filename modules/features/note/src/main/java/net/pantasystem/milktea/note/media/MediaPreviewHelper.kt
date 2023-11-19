
package net.pantasystem.milktea.note.media

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.databinding.BindingAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import jp.wasabeef.glide.transformations.BlurTransformation
import net.pantasystem.milktea.common.glide.GlideApp
import net.pantasystem.milktea.common.glide.blurhash.BlurHashSource
import net.pantasystem.milktea.common_android.platform.isWifiConnected
import net.pantasystem.milktea.common_android.ui.MediaLayout
import net.pantasystem.milktea.common_android.ui.VisibilityHelper.setMemoVisibility
import net.pantasystem.milktea.model.setting.Config
import net.pantasystem.milktea.model.setting.MediaDisplayMode
import net.pantasystem.milktea.note.R
import net.pantasystem.milktea.note.databinding.ItemMediaPreviewBinding
import net.pantasystem.milktea.note.media.viewmodel.MediaViewData
import net.pantasystem.milktea.note.media.viewmodel.PreviewAbleFile
import net.pantasystem.milktea.note.view.NoteCardActionListenerAdapter

object MediaPreviewHelper {


    @JvmStatic
    fun FrameLayout.setClickWhenShowMediaActivityListener(
        thumbnailView: ImageView,
        playButton: ImageButton,
        previewAbleFile: PreviewAbleFile?,
        previewAbleFileList: List<PreviewAbleFile>?,
        noteCardActionListenerAdapter: NoteCardActionListenerAdapter?,
    ) {

        if (previewAbleFileList.isNullOrEmpty()) {
            return
        }
        val listener = View.OnClickListener {
            noteCardActionListenerAdapter?.onMediaPreviewClicked(
                previewAbleFile = previewAbleFile,
                files = previewAbleFileList,
                index = previewAbleFileList.indexOfFirst { f ->
                    f === previewAbleFile
                },
                thumbnailView = thumbnailView,
            )
        }
        thumbnailView.setOnClickListener(listener)
        playButton.setOnClickListener(listener)

        val holdListener = View.OnLongClickListener {
            noteCardActionListenerAdapter?.onMediaPreviewLongClicked(previewAbleFile)
            true
        }
        thumbnailView.setOnLongClickListener(holdListener)

        // NOTE: 実装の仕様上、サムネイル非表示時には親レイアウトにクリックイベントを伝播する必要がある
        if (previewAbleFile?.visibleType == PreviewAbleFile.VisibleType.SensitiveHide
            || previewAbleFile?.visibleType == PreviewAbleFile.VisibleType.HideWhenMobileNetwork
        ) {
            thumbnailView.setOnClickListener {
                this.performClick()
            }
        }
    }

    @SuppressLint("MissingPermission")
    @BindingAdapter("thumbnailView", "config")
    @JvmStatic
    fun ImageView.setPreview(file: PreviewAbleFile?, config: Config?) {
        file ?: return
        config ?: return
        val isHiding = when(file.visibleType) {
            PreviewAbleFile.VisibleType.Visible -> false
            PreviewAbleFile.VisibleType.HideWhenMobileNetwork -> {
                if (config.mediaDisplayMode == MediaDisplayMode.ALWAYS_HIDE_WHEN_MOBILE_NETWORK) {
                    !context.isWifiConnected()
                } else config.mediaDisplayMode == MediaDisplayMode.ALWAYS_HIDE
            }
            PreviewAbleFile.VisibleType.SensitiveHide -> true
        }
        if (isHiding) {
            Glide.with(this)
                .let {
                    when (val blurhash = file.source.blurhash) {
                        null -> it.load(file.source.thumbnailUrl)
                            .transform(BlurTransformation(32, 4), CenterCrop())
                        else -> it.load(
                            BlurHashSource(blurhash)
                        )
                    }
                }
                .into(this)
        } else {
            Glide.with(this)
                .load(file.source.thumbnailUrl)
                .thumbnail(GlideApp.with(this).load(
                    file.source.blurhash?.let {
                        BlurHashSource(it)
                    }
                ))
                .centerCrop()
                .into(this)
        }

    }

    @JvmStatic
    @BindingAdapter("hideImageMessageImageSource", "config")
    fun TextView.setHideImageMessage(src: PreviewAbleFile?, config: Config?) {
        src ?: return
        config ?: return
        when(src.visibleType) {
            PreviewAbleFile.VisibleType.Visible -> {
                this.setMemoVisibility(View.GONE)
                return
            }
            PreviewAbleFile.VisibleType.HideWhenMobileNetwork -> {
                this.text = context.getString(R.string.notes_media_click_to_load_image)
                if (context.isWifiConnected()) {
                    if (config.mediaDisplayMode == MediaDisplayMode.ALWAYS_HIDE) {
                        this.setMemoVisibility(View.VISIBLE)
                    } else {
                        this.setMemoVisibility(View.GONE)
                    }
                } else {
                    this.setMemoVisibility(View.VISIBLE)
                }
            }
            PreviewAbleFile.VisibleType.SensitiveHide -> {
                this.setMemoVisibility(View.VISIBLE)
                this.text = context.getString(R.string.sensitive_content)
            }
        }
    }


    @JvmStatic
    @BindingAdapter("previewAbleList", "mediaViewData", "noteCardActionListenerAdapter")
    fun MediaLayout.setPreviewAbleList(
        previewAbleList: List<PreviewAbleFile>?,
        mediaViewData: MediaViewData?,
        noteCardActionListenerAdapter: NoteCardActionListenerAdapter?,
    ) {
        if (previewAbleList == null || mediaViewData == null) {
            this.visibility = View.GONE
            return
        }

        if (previewAbleList.isEmpty()) {
            this.visibility = View.GONE
            return
        }

        withSuspendLayout {
            var count = this.childCount
            while (count > previewAbleList.size) {
                if (this.childCount > 4) {
                    this.removeViewAt(this.childCount - 1)
                } else {
                    this.getChildAt(count - 1).setMemoVisibility(View.GONE)
                }
                count --
            }

            val inflater = LayoutInflater.from(this.context)
            previewAbleList.forEachIndexed { index, previewAbleFile ->
                val existsView: View? = this.getChildAt(index)
                val binding = if (existsView == null) {
                    ItemMediaPreviewBinding.inflate(inflater, this, false)
                } else {
                    ItemMediaPreviewBinding.bind(existsView)
                }
                binding.root.setMemoVisibility(View.VISIBLE)

                binding.baseFrame.setClickWhenShowMediaActivityListener(
                    binding.thumbnail,
                    binding.actionButton,
                    previewAbleFile,
                    previewAbleList,
                    noteCardActionListenerAdapter,
                )

                binding.thumbnail.setPreview(previewAbleFile, mediaViewData.config)

                binding.actionButton.isVisible = previewAbleFile.isVisiblePlayButton
                binding.nsfwMessage.isVisible = previewAbleFile.isHiding
                binding.nsfwMessage.setHideImageMessage(previewAbleFile, mediaViewData.config)
                binding.toggleVisibilityButton.setImageResource(if (previewAbleFile.isHiding) R.drawable.ic_baseline_image_24 else R.drawable.ic_baseline_hide_image_24)
                binding.toggleVisibilityButton.setOnClickListener {
                    mediaViewData.toggleVisibility(index)
                }
                binding.baseFrame.setOnClickListener {
                    noteCardActionListenerAdapter?.onSensitiveMediaPreviewClicked(
                        mediaViewData,
                        index
                    )
                }

                if (existsView == null) {
                    this.addView(binding.root)
                }
            }
        }

        this.visibility = View.VISIBLE

    }


}


