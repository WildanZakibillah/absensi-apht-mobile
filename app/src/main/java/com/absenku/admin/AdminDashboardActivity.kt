package com.absenku.admin

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.absenku.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        bottomNav = findViewById(R.id.adminBottomNav)

        // Default fragment
        loadFragment(AdminHomeFragment())

        bottomNav.setOnItemSelectedListener { item: MenuItem ->
            when (item.itemId) {
                R.id.nav_admin_home     -> loadFragment(AdminHomeFragment())
                R.id.nav_admin_users    -> loadFragment(AdminUsersFragment())
                R.id.nav_admin_izin     -> loadFragment(AdminIzinFragment())
                R.id.nav_admin_rekap    -> loadFragment(AdminRekapFragment())
                R.id.nav_admin_settings -> loadFragment(AdminSettingsFragment())
            }
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.adminFragmentContainer, fragment)
            .commit()
    }

    override fun onBackPressed() {
        // Konfirmasi keluar
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Keluar Dashboard Admin")
            .setMessage("Yakin ingin keluar?")
            .setPositiveButton("Logout") { _, _ ->
                FirebaseAuth.getInstance().signOut()
                val intent = Intent(this, com.absenku.auth.LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}