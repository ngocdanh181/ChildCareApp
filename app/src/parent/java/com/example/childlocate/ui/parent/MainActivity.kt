package com.example.childlocate.ui.parent

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.childlocate.R
import com.example.childlocate.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup NavController
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController


        //setupActionBarWithNavController(navController, appBarConfiguration)

        binding.bottomNavigationView.setupWithNavController(navController)

        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.chat -> {
                    navController.navigate(R.id.detailChatFragment)
                    true
                }

                R.id.home -> {
                    navController.navigate(R.id.homeFragment)
                    true
                }

                /*R.id.history -> {
                    navController.navigate(R.id.historyFragment)
                    //startActivity(Intent(this, MapActivity::class.java))
                    true
                }*/

                R.id.userinfo -> {
                    navController.navigate(R.id.userInfoFragment)
                    true
                }

                else -> false
            }
        }

    }

    /*override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }*/
}







