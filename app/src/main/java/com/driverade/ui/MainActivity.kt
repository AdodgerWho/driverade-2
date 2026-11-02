package com.driverade.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.driverade.R

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_dashboard)
    }
}
