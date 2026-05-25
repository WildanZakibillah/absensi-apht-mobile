package com.absenku.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.absenku.R
import com.absenku.auth.LoginActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileFragment : Fragment() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db   by lazy { FirebaseFirestore.getInstance() }

    // Header Views
    private lateinit var tvInitial    : TextView
    private lateinit var imgProfile   : ImageView
    private lateinit var tvNama       : TextView
    private lateinit var tvEmail      : TextView
    private lateinit var tvRoleBadge  : TextView

    // Card 1: Informasi Pekerjaan
    private lateinit var tvNikPerusahaan : TextView
    private lateinit var tvNikKtp        : TextView
    private lateinit var tvJabatanDivisi : TextView
    private lateinit var tvStatusJoin    : TextView
    private lateinit var tvShift         : TextView

    // Card 2: Informasi Pribadi
    private lateinit var tvTtl           : TextView
    private lateinit var tvGenderAgama   : TextView
    private lateinit var tvPendidikan    : TextView
    private lateinit var tvNikah         : TextView
    private lateinit var tvTelepon       : TextView

    // Card 3: Alamat
    private lateinit var tvDomisili      : TextView
    private lateinit var tvAlamatKtp     : TextView

    // Menu Actions
    private lateinit var menuPassword : LinearLayout
    private lateinit var menuNotif    : LinearLayout
    private lateinit var menuTentang  : LinearLayout
    private lateinit var btnLogout    : MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)
        bindViews(view)
        setupClickListeners()
        loadUserProfile()
        return view
    }

    private fun bindViews(v: View) {
        // Header
        tvInitial    = v.findViewById(R.id.tvInitial)
        imgProfile   = v.findViewById(R.id.imgProfile)
        tvNama       = v.findViewById(R.id.tvNama)
        tvEmail      = v.findViewById(R.id.tvEmail)
        tvRoleBadge  = v.findViewById(R.id.tvRoleBadge)

        // Info Pekerjaan
        tvNikPerusahaan = v.findViewById(R.id.tvNikPerusahaan)
        tvNikKtp        = v.findViewById(R.id.tvNikKtp)
        tvJabatanDivisi = v.findViewById(R.id.tvJabatanDivisi)
        tvStatusJoin    = v.findViewById(R.id.tvStatusJoin)
        tvShift         = v.findViewById(R.id.tvShift)

        // Info Pribadi
        tvTtl           = v.findViewById(R.id.tvTtl)
        tvGenderAgama   = v.findViewById(R.id.tvGenderAgama)
        tvPendidikan    = v.findViewById(R.id.tvPendidikan)
        tvNikah         = v.findViewById(R.id.tvNikah)
        tvTelepon       = v.findViewById(R.id.tvTelepon)

        // Alamat
        tvDomisili      = v.findViewById(R.id.tvDomisili)
        tvAlamatKtp     = v.findViewById(R.id.tvAlamatKtp)

        // Menu
        menuPassword = v.findViewById(R.id.menuUbahPassword)
        menuNotif    = v.findViewById(R.id.menuNotifikasi)
        menuTentang  = v.findViewById(R.id.menuTentang)
        btnLogout    = v.findViewById(R.id.btnLogout)
    }

    private fun setupClickListeners() {
        menuPassword.setOnClickListener {
            Toast.makeText(requireContext(), "Fitur ubah password akan segera hadir.", Toast.LENGTH_SHORT).show()
        }
        menuNotif.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, NotificationFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }
        menuTentang.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Tentang AbsenKu")
                .setMessage("AbsenKu v1.0.0\nSistem Absensi Digital Karyawan Terpadu untuk Perusahaan Daerah Sumekar.\n\nDikembangkan untuk mendukung operasional APHT.")
                .setPositiveButton("Tutup", null)
                .show()
        }
        btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Keluar Akun")
                .setMessage("Apakah kamu yakin ingin keluar dari aplikasi?")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Keluar") { _, _ ->
                    auth.signOut()
                    val intent = Intent(requireContext(), LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                .show()
        }
    }

    private fun loadUserProfile() {
        val user = auth.currentUser
        if (user == null) return

        // Isi Email dari Firebase Auth
        tvEmail.text = user.email ?: "-"

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    // Ekstraksi data
                    val name       = doc.getString("name")?.ifEmpty { "-" } ?: "-"
                    val role       = doc.getString("role")?.lowercase() ?: "user"

                    val nikPeru    = doc.getString("nik_perusahaan")?.ifEmpty { "-" } ?: "-"
                    val nikKtp     = doc.getString("nik_ktp")?.ifEmpty { "-" } ?: "-"
                    val dept       = doc.getString("department")?.ifEmpty { "-" } ?: "-"
                    val pos        = doc.getString("position")?.ifEmpty { "-" } ?: "-"
                    val statusK    = doc.getString("status_karyawan")?.ifEmpty { "-" } ?: "-"
                    val joinDate   = doc.getString("join_date")?.ifEmpty { "-" } ?: "-"
                    val shift      = doc.getString("shift")?.ifEmpty { "Regular" } ?: "Regular"

                    val phone      = doc.getString("phone")?.ifEmpty { "-" } ?: "-"
                    val pob        = doc.getString("pob")?.ifEmpty { "-" } ?: "-"
                    val dob        = doc.getString("dob")?.ifEmpty { "-" } ?: "-"
                    val gender     = doc.getString("gender")?.ifEmpty { "-" } ?: "-"
                    val religion   = doc.getString("religion")?.ifEmpty { "-" } ?: "-"

                    val education  = doc.getString("last_education")?.ifEmpty { "-" } ?: "-"
                    val marital    = doc.getString("marital_status")?.ifEmpty { "-" } ?: "-"

                    val domisili   = doc.getString("address_domicile")?.ifEmpty { "-" } ?: "-"
                    val alamatKtp  = doc.getString("address_ktp")?.ifEmpty { "-" } ?: "-"

                    // Mapping Data ke UI: Header
                    tvNama.text = name
                    tvInitial.text = name.take(1).uppercase()
                    tvRoleBadge.text = if (role == "admin") "ROLE: ADMIN" else "ROLE: USER"

                    // Mapping Data ke UI: Informasi Pekerjaan
                    tvNikPerusahaan.text = nikPeru
                    tvNikKtp.text        = nikKtp
                    tvJabatanDivisi.text = "$pos di $dept"
                    tvStatusJoin.text    = "$statusK • $joinDate"
                    tvShift.text         = shift

                    // Mapping Data ke UI: Informasi Pribadi
                    tvTtl.text           = "$pob, $dob"
                    tvGenderAgama.text   = "$gender • $religion"
                    tvPendidikan.text    = education
                    tvNikah.text         = marital
                    tvTelepon.text       = phone

                    // Mapping Data ke UI: Alamat
                    tvDomisili.text      = domisili
                    tvAlamatKtp.text     = alamatKtp

                } else {
                    Toast.makeText(requireContext(), "Data profil tidak ditemukan.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Gagal memuat profil: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}