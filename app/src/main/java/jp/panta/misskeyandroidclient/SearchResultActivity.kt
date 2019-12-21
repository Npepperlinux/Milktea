package jp.panta.misskeyandroidclient

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import jp.panta.misskeyandroidclient.model.notes.NoteRequest
import jp.panta.misskeyandroidclient.model.notes.NoteType
import jp.panta.misskeyandroidclient.view.notes.TimelineFragment
import kotlinx.android.synthetic.main.activity_search_result.*

class SearchResultActivity : AppCompatActivity() {
    companion object{
        const val EXTRA_SEARCH_WORLD = "jp.panta.misskeyandroidclient.SearchResultActivity.EXTRA_SEARCH_WORLD"
    }

    var mSearchWord: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme()
        setContentView(R.layout.activity_search_result)
        setSupportActionBar(search_result_toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val keyword: String? = intent.getStringExtra(EXTRA_SEARCH_WORLD)
            ?: intent.data?.getQueryParameter("keyword")

        mSearchWord = keyword

        if(keyword == null){
            finish()
            return
        }
        supportActionBar?.title = keyword

        val isTag = keyword.startsWith("#")
        val request = if(isTag){
            val tag = keyword.substring(1, keyword.length)
            NoteRequest.Setting(
                type = NoteType.SEARCH_HASH,
                tag = tag
            )
        }else{
            NoteRequest.Setting(
                type = NoteType.SEARCH,
                query = keyword
            )
        }

        val timelineFragment = TimelineFragment.newInstance(request)
        val ft = supportFragmentManager.beginTransaction()
        ft.replace(R.id.search_result_base, timelineFragment)
        ft.commit()

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.search_top_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when(item?.itemId){
            android.R.id.home -> finish()
            R.id.search -> {
                val intent = Intent(this, SearchActivity::class.java)
                intent.putExtra(SearchActivity.EXTRA_SEARCH_WORD, mSearchWord)
                startActivity(intent)
            }
        }
        return super.onOptionsItemSelected(item)
    }
}