package com.example.hawkwind.core
import android.content.Context
object AppCtx { lateinit var ctx: Context; fun init(c: Context){ ctx = c.applicationContext } }
