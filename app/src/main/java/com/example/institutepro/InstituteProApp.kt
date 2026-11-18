package com.example.institutepro

import android.app.Application
import com.example.institutepro.data.db.InstituteDatabase
import com.example.institutepro.data.repo.InstituteRepository

class InstituteProApp : Application() {
    val database: InstituteDatabase by lazy { InstituteDatabase.getInstance(this) }
    val repository: InstituteRepository by lazy { InstituteRepository(database) }
}
