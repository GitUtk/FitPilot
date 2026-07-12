package com.gitutk.fitpilot

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    lateinit var apiService: ApiService
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        apiService = ApiService(this)
        bottomNav = findViewById(R.id.bottom_navigation)

        setupNavigation()

        if (apiService.isLoggedIn()) {
            showMainApp()
        } else {
            showLogin()
        }
    }

    private fun setupNavigation() {
        bottomNav.setOnNavigationItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_dashboard -> DashboardFragment()
                R.id.nav_nutrition -> NutritionFragment()
                R.id.nav_workouts -> WorkoutLogFragment()
                R.id.nav_food -> MealLoggerFragment()
                else -> DashboardFragment()
            }
            loadFragment(fragment, false)
            true
        }
    }

    fun showMainApp() {
        bottomNav.visibility = View.VISIBLE
        bottomNav.selectedItemId = R.id.nav_dashboard
        loadFragment(DashboardFragment(), false)
    }

    fun showLogin() {
        bottomNav.visibility = View.GONE
        loadFragment(LoginFragment(), false)
    }

    fun loadFragment(fragment: Fragment, addToBackStack: Boolean) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragment)
        if (addToBackStack) {
            transaction.addToBackStack(null)
        }
        transaction.commit()
    }
}
