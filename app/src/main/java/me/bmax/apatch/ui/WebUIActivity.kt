package me.bmax.apatch.ui

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.webkit.WebViewAssetLoader
import me.bmax.apatch.APApplication
import me.bmax.apatch.ui.webui.SuFilePathHandler
import me.bmax.apatch.ui.webui.WebViewInterface
import java.io.File

@SuppressLint("SetJavaScriptEnabled")
class WebUIActivity : ComponentActivity() {
    private lateinit var webViewInterface: WebViewInterface

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val moduleId = intent.getStringExtra("id")!!
        val name = intent.getStringExtra("name")!!
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            setTaskDescription(ActivityManager.TaskDescription("APatch - $name"))
        } else {
            val taskDescription =
                ActivityManager.TaskDescription.Builder().setLabel("APatch - $name").build()
            setTaskDescription(taskDescription)
        }

        val prefs = APApplication.sharedPreferences
        WebView.setWebContentsDebuggingEnabled(prefs.getBoolean("enable_web_debugging", false))

        val webRoot = File("/data/adb/modules/${moduleId}/webroot")
        val webViewAssetLoader = WebViewAssetLoader.Builder()
            .setDomain("mui.kernelsu.org")
            .addPathHandler(
                "/",
                SuFilePathHandler(this, webRoot)
            )
            .build()

        val webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return webViewAssetLoader.shouldInterceptRequest(request.url)
            }
        }

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            webViewInterface = WebViewInterface(this@WebUIActivity, this)
            addJavascriptInterface(webViewInterface, "ksu")
            setWebViewClient(webViewClient)
            loadUrl("https://mui.kernelsu.org/index.html")
        }

        setContentView(webView)
    }
}