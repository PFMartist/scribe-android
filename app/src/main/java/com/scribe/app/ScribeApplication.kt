package com.scribe.app

import android.app.Application
import com.scribe.app.data.local.AppDatabase

class ScribeApplication : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
}
