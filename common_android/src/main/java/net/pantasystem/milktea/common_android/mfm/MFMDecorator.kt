package jp.panta.misskeyandroidclient.mfm

import android.app.SearchManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.*
import android.text.style.*
import android.util.Log
import android.view.View
import android.widget.TextView
import com.bumptech.glide.Glide
import jp.panta.misskeyandroidclient.*
import jp.panta.misskeyandroidclient.ui.text.DrawableEmojiSpan
import jp.panta.misskeyandroidclient.ui.text.EmojiAdapter
import jp.panta.misskeyandroidclient.ui.text.EmojiSpan
import net.pantasystem.milktea.common.glide.GlideApp
import java.lang.ref.WeakReference

object MFMDecorator {


    fun decorate(textView: TextView, node: Root): Spanned{
        val emojiAdapter = EmojiAdapter(textView)
        return Visitor(WeakReference(textView), node, node, emojiAdapter).decorate()
            ?: SpannedString(node.sourceText)
    }

    /**
     * VisitorパターンのVisitorとは少し違う
     */
    class Visitor(
        val textView: WeakReference<TextView>,
        val root: Root,
        val parent: Element,
        private val emojiAdapter: EmojiAdapter
    ){
        private var position = 0
        private val spannableStringBuilder = SpannableStringBuilder()


        /**
         * parent のchildElementsをここで処理する
         * @return 担当した要素（Element）のspannableStringBuilderの最終的なポジション
         */
        fun decorate(): Spanned?{
            if(parent is Node){
                parent.childElements.forEach{ ele ->
                    val spanned = Visitor(textView, root, ele, emojiAdapter).decorate()
                        ?: closeErrorElement(ele)
                    spannableStringBuilder.append(spanned)
                    position += spanned.length - 1
                }
            }

            // parentを装飾してしまう
            return try{
                // 自己がLeafであればparentの情報から装飾され、自己がNodeであればstartとpositionをもとに装飾される
                decorate(parent)
            }catch(e: Exception){
                // 子要素の処理がすべて無駄になることになってしまうがとりあえずはこうする
                null
            }

            // Leafの処理が終わったら自分(parent)のDecorateを開始する
        }


        private fun closeErrorElement(element: Element): Spanned{
            return SpannableString(root.sourceText.substring(element.start, element.end))
        }

        private fun decorate(element: Element): Spanned?{
            return when(element){
                is Leaf ->
                    when(element){
                        is Text-> decorateText(element)
                        is EmojiElement -> decorateEmoji(element)
                        is Search -> decorateSearch(element)
                        is Mention -> decorateMention(element)
                        is Link -> decorateLink(element)
                        is HashTag -> decorateHashTag(element)
                        else -> null
                    }
                is Node ->{
                    decorateNode()
                    spannableStringBuilder
                }
                else -> null
            }

        }

        private fun decorateEmoji(emojiElement: EmojiElement): Spanned{
            val spanned = SpannableString(emojiElement.text)
            textView.get()?.let{ textView ->
                //val emojiSpan = EmojiSpan(textView)
                val emojiSpan: EmojiSpan<*>
                if(emojiElement.emoji.isSvg()){
                    //emojiSpan = BitmapEmojiSpan(emojiAdapter)
                    emojiSpan = DrawableEmojiSpan(emojiAdapter)

                    GlideApp.with(textView.context)
                        .load(emojiElement.emoji.url?: emojiElement.emoji.url)
                        .into(emojiSpan.target)
                }else{
                    emojiSpan = DrawableEmojiSpan(emojiAdapter)
                    Glide.with(textView)
                        .asDrawable()
                        .load(emojiElement.emoji.url)
                        .override(textView.textSize.toInt())
                        .into(emojiSpan.target)
                }
                spanned.setSpan(emojiSpan, 0, emojiElement.text.length, 0)

            }

            return spanned
        }

        private fun decorateText(text: Text): Spanned{
            return SpannedString(text.text)
        }

        private fun decorateSearch(search: Search): Spanned{
            val intent = Intent(Intent.ACTION_SEARCH)
            //intent.setClassName("com.google.android.googlequicksearchbox",  "com.google.android.googlequicksearchbox.SearchActivity")
            intent.putExtra(SearchManager.QUERY, search.text)
            return makeClickableSpan("${search.text}  ${(textView.get()?.context?.getString(R.string.search)?: "Search")}", intent)
        }

        private fun decorateMention(mention: Mention): Spanned{
            return textView.get()?.let{ textView ->
                val intent = UserDetailActivity.newInstance(textView.context, userName = mention.text)
                intent.putActivity(Activities.ACTIVITY_IN_APP)


                makeClickableSpan(mention.text, intent)
            }?: closeErrorElement(mention)

        }

        private fun decorateLink(link: Link): Spanned{
            return makeClickableSpan(link.text, Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
        }


        private fun decorateHashTag(hashTag: HashTag): Spanned{
            return textView.get()?.let{ textView ->
                val intent = Intent(textView.context, SearchResultActivity::class.java)
                intent.putExtra(SearchResultActivity.EXTRA_SEARCH_WORLD, hashTag.text)
                makeClickableSpan(hashTag.text, intent)
            }?: closeErrorElement(hashTag)

        }

        private fun makeClickableSpan(text: String, intent: Intent): SpannableString{
            val spanned = SpannableString(text)
            spanned.setSpan(
                object : ClickableSpan(){
                    override fun onClick(p0: View) {
                        textView.get()?.context?.startActivity(intent)

                    }
                },0, text.length, 0
            )
            return spanned
        }

        /**
         * 最後に一度だけ実行すること
         */
        private fun decorateNode(){
            fun setSpan(any: Any){
                spannableStringBuilder.setSpan(any, 0, spannableStringBuilder.length, 0)
            }

            if(parent is Node){
                when(parent.elementType){
                    ElementType.QUOTE ->{
                        setSpan(QuoteSpan())
                    }
                    ElementType.BOLD ->{
                        setSpan(StyleSpan(Typeface.BOLD))
                    }
                    ElementType.ITALIC ->{
                        setSpan(StyleSpan(Typeface.ITALIC))
                    }
                    ElementType.CENTER ->{
                        setSpan(AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER))
                    }
                    ElementType.STRIKE ->{
                        setSpan(StrikethroughSpan())
                    }
                    ElementType.CODE ->{
                        setSpan(BackgroundColorSpan(Color.parseColor("#000000")))
                        setSpan(ForegroundColorSpan(Color.WHITE))
                    }
                    ElementType.TITLE ->{
                        spannableStringBuilder.append("\n")
                        setSpan(RelativeSizeSpan(1.5F))
                        setSpan(AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER))
                    }
                    ElementType.SMALL ->{
                        setSpan(RelativeSizeSpan(0.6F))
                    }
                    ElementType.ROOT ->{

                    }
                    else ->{
                        Log.d("MFMDecorator", "error:${parent.elementType}")
                    }
                }
            }

        }



    }
}