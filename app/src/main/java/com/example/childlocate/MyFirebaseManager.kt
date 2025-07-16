package com.example.childlocate

import android.content.Context
import com.google.firebase.FirebaseApp

object MyFirebaseManager {
    private var firebaseApp: FirebaseApp? = null

    fun initFirebase(context: Context) {
        if (firebaseApp == null) {
            firebaseApp = FirebaseApp.initializeApp(context)
        }
    }

    fun getFirebaseApp(): FirebaseApp? {
        return firebaseApp
    }
}





