package com.absenku.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.absenku.dashboard.DashboardActivity
import com.absenku.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)

        btnLogin.setOnClickListener { doLogin() }
    }

    override fun onStart() {
        super.onStart()

        // Hapus auto-login dan pastikan user logout agar harus login ulang
        auth.signOut()
    }

    private fun doLogin() {
        val email = etEmail.text?.toString()?.trim().orEmpty()
        val pass = etPassword.text?.toString()?.trim().orEmpty()

        if (email.isEmpty()) {
            etEmail.error = "Email wajib diisi"
            etEmail.requestFocus()
            return
        }
        if (pass.isEmpty()) {
            etPassword.error = "Password wajib diisi"
            etPassword.requestFocus()
            return
        }

        btnLogin.isEnabled = false

        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener { res ->
                val uid = res.user?.uid
                if (uid == null) {
                    btnLogin.isEnabled = true
                    Toast.makeText(this, "Login gagal (UID null)", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                fetchRoleAndGo(uid)
            }
            .addOnFailureListener { e ->
                btnLogin.isEnabled = true
                Toast.makeText(this, "Login gagal: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun fetchRoleAndGo(uid: String) {
        btnLogin.isEnabled = false
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                btnLogin.isEnabled = true
                if (!doc.exists()) {
                    Toast.makeText(this, "Profil belum dibuat. Hubungi admin.", Toast.LENGTH_LONG).show()
                    auth.signOut()
                    return@addOnSuccessListener
                }
                val role = (doc.getString("role") ?: "USER").uppercase()

                val targetActivity = if (role == "ADMIN") {
                    com.absenku.admin.AdminDashboardActivity::class.java
                } else {
                    DashboardActivity::class.java
                }

                val intent = Intent(this, targetActivity).apply {
                    putExtra("ROLE", role)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                btnLogin.isEnabled = true
                Toast.makeText(this, "Gagal cek role: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}