package com.maxwai.nclientv3.components.activities

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import com.maxwai.nclientv3.R
import com.maxwai.nclientv3.components.views.CFTokenView
import com.maxwai.nclientv3.settings.Global
import java.lang.ref.WeakReference

abstract class GeneralActivity : AppCompatActivity() {
    private var isFastScrollerApplied = false
    private var tokenView: CFTokenView? = null

    private fun inflateWebView() {
        if (tokenView == null) {
            Toast.makeText(this, R.string.fetching_cloudflare_token, Toast.LENGTH_SHORT).show()
            val rootView = findViewById<View>(android.R.id.content).getRootView() as ViewGroup?
            val v = LayoutInflater.from(this)
                .inflate(R.layout.cftoken_layout, rootView, false) as ViewGroup
            tokenView = CFTokenView(v)
            val params = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            tokenView!!.setVisibility(View.GONE)
            this.addContentView(v, params)
        }
    }

    override fun onPause() {
        if (Global.hideMultitask()) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onPause()
    }

    protected open var handleInsetsInBaseActivity = true
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        if (handleInsetsInBaseActivity) applyInsets()
        Global.initActivity(this)
    }

    protected open fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, windowInsets ->
            // 1. 获取系统栏（状态栏 + 导航栏）的 Insets
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            // 2. 将 Insets 应用为根视图的 padding
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)

            windowInsets
        }
    }

    override fun onResume() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onResume()
        lastActivity = WeakReference<GeneralActivity?>(this)
        if (!isFastScrollerApplied) {
            isFastScrollerApplied = true
            Global.applyFastScroller(findViewById<RecyclerView?>(R.id.recycler))
        }
    }

    companion object {
        private var lastActivity: WeakReference<GeneralActivity?>? = null

        @JvmStatic
        val lastCFView: CFTokenView?
            get() {
                if (lastActivity == null) return null
                val activity: GeneralActivity? = lastActivity!!.get()
                if (activity != null) {
                    activity.runOnUiThread(Runnable { activity.inflateWebView() })
                    return activity.tokenView
                }
                return null
            }
    }
}
