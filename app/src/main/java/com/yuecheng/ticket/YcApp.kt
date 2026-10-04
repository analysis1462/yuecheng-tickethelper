package com.yuecheng.ticket

import android.app.Application
import com.yuecheng.ticket.data.Api
import com.yuecheng.ticket.data.CrashLog
import com.yuecheng.ticket.data.FavLines
import com.yuecheng.ticket.data.SearchHistory
import com.yuecheng.ticket.data.SecureStore
import com.yuecheng.ticket.data.Session
import com.yuecheng.ticket.data.ThemePrefs

class YcApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        Api.init(this)
        Session.init(this)
        Session.restore()
        ThemePrefs.init(this)
        SearchHistory.init(this)
        FavLines.init(this)
        SecureStore.init(this)
    }
}
