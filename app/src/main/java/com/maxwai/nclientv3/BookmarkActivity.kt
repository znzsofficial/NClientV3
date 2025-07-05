package com.maxwai.nclientv3

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import com.maxwai.nclientv3.adapters.BookmarkAdapter
import com.maxwai.nclientv3.components.activities.GeneralActivity
import com.maxwai.nclientv3.components.widgets.CustomLinearLayoutManager

class BookmarkActivity : GeneralActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //Global.initActivity(this);
        setContentView(R.layout.activity_bookmark)
        val toolbar = findViewById<Toolbar?>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(true)
            setTitle(R.string.manage_bookmarks)
        }

        findViewById<RecyclerView>(R.id.recycler).let {
            it.setLayoutManager(CustomLinearLayoutManager(this))
            it.setAdapter(BookmarkAdapter(this))
        }

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
