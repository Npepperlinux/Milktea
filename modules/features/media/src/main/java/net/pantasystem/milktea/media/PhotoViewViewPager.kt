package net.pantasystem.milktea.media

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager


/**
 * PhotoViewでViewPagerを利用するとエラーが発生するのでその対処をする。
 */
class PhotoViewViewPager : ViewPager {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet): super(context, attrs)

    override fun onInterceptHoverEvent(event: MotionEvent?): Boolean {
        return try{
            super.onInterceptHoverEvent(event)
        }catch(e: IllegalArgumentException){
            false
        }
    }
}