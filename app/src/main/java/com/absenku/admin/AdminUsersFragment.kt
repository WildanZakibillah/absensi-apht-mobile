package com.absenku.admin

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.absenku.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AdminUsersFragment : Fragment() {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private lateinit var rvUsers: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var tvCount: TextView

    private var allUsers = listOf<UserItem>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_admin_users, container, false)
        rvUsers = view.findViewById(R.id.rvAdminUsers)
        etSearch = view.findViewById(R.id.etSearchUser)
        tvCount = view.findViewById(R.id.tvUserCount)

        rvUsers.layoutManager = LinearLayoutManager(requireContext())
        loadUsers()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterUsers(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        view.findViewById<MaterialButton>(R.id.btnAddUser).setOnClickListener {
            showAddUserDialog()
        }

        return view
    }

    private fun loadUsers() {
        db.collection("users").get().addOnSuccessListener { snap ->
            allUsers = snap.documents.mapNotNull { doc ->
                UserItem(
                    uid = doc.id,
                    name = doc.getString("name") ?: doc.getString("nama") ?: "-",
                    email = doc.getString("email") ?: "-",
                    role = doc.getString("role") ?: "user",
                    nik_ktp = doc.getString("nik_ktp") ?: "",
                    nik_perusahaan = doc.getString("nik_perusahaan") ?: doc.getString("nip") ?: "-",
                    pob = doc.getString("pob") ?: "",
                    dob = doc.getString("dob") ?: "",
                    gender = doc.getString("gender") ?: "Laki-laki",
                    religion = doc.getString("religion") ?: "",
                    marital_status = doc.getString("marital_status") ?: "Lajang",
                    last_education = doc.getString("last_education") ?: "",
                    phone = doc.getString("phone") ?: "",
                    address_ktp = doc.getString("address_ktp") ?: "",
                    address_domicile = doc.getString("address_domicile") ?: "",
                    position = doc.getString("position") ?: "",
                    department = doc.getString("department") ?: "",
                    join_date = doc.getString("join_date") ?: "",
                    status_karyawan = doc.getString("status_karyawan") ?: "Kontrak (PKWT)",
                    shift = doc.getString("shift") ?: "Regular"
                )
            }
            tvCount.text = "${allUsers.size} pengguna"
            rvUsers.adapter = AdminUsersAdapter(allUsers) { user -> showUserDetailDialog(user) }
        }
    }

    private fun filterUsers(query: String) {
        val filtered = if (query.isEmpty()) allUsers
        else allUsers.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.email.contains(query, ignoreCase = true) ||
                    it.nik_perusahaan.contains(query, ignoreCase = true)
        }
        tvCount.text = "${filtered.size} pengguna"
        rvUsers.adapter = AdminUsersAdapter(filtered) { user -> showUserDetailDialog(user) }
    }

    // ── Ekstraksi Data dari Form ─────────────────────────────────────────────
    private fun extractDataFromForm(view: View): Map<String, Any> {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return mapOf(
            "name" to view.findViewById<EditText>(R.id.etName).text.toString().trim(),
            "email" to view.findViewById<EditText>(R.id.etEmail).text.toString().trim(),
            "nik_ktp" to view.findViewById<EditText>(R.id.etNikKtp).text.toString().trim(),
            "nik_perusahaan" to view.findViewById<EditText>(R.id.etNikPerusahaan).text.toString().trim(),
            "pob" to view.findViewById<EditText>(R.id.etPob).text.toString().trim(),
            "dob" to view.findViewById<EditText>(R.id.etDob).text.toString().trim(),
            "gender" to view.findViewById<EditText>(R.id.etGender).text.toString().trim().ifEmpty { "Laki-laki" },
            "religion" to view.findViewById<EditText>(R.id.etReligion).text.toString().trim(),
            "marital_status" to view.findViewById<EditText>(R.id.etMaritalStatus).text.toString().trim().ifEmpty { "Lajang" },
            "last_education" to view.findViewById<EditText>(R.id.etLastEducation).text.toString().trim(),
            "phone" to view.findViewById<EditText>(R.id.etPhone).text.toString().trim(),
            "address_ktp" to view.findViewById<EditText>(R.id.etAddressKtp).text.toString().trim(),
            "address_domicile" to view.findViewById<EditText>(R.id.etAddressDomicile).text.toString().trim(),
            "position" to view.findViewById<EditText>(R.id.etPosition).text.toString().trim(),
            "department" to view.findViewById<EditText>(R.id.etDepartment).text.toString().trim(),
            "join_date" to view.findViewById<EditText>(R.id.etJoinDate).text.toString().trim().ifEmpty { todayStr },
            "status_karyawan" to view.findViewById<EditText>(R.id.etStatusKaryawan).text.toString().trim().ifEmpty { "Kontrak (PKWT)" },
            "shift" to view.findViewById<EditText>(R.id.etShift).text.toString().trim().ifEmpty { "Regular" }
        )
    }

    // ── Dialog Detail ────────────────────────────────────────────────────────
    private fun showUserDetailDialog(user: UserItem) {
        val options = arrayOf(
            "✏️  Edit Profil Lengkap",
            "📅  Edit Absen Hari Ini",
            "📋  Lihat Rekap Bulan Ini",
            "🔄  Ubah Role (${user.role})",
            "🗑️  Hapus User"
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(user.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditProfileDialog(user)
                    1 -> showEditAbsenDialog(user)
                    2 -> showUserRekapDialog(user)
                    3 -> showToggleRoleDialog(user)
                    4 -> showDeleteUserDialog(user)
                }
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    // ── 1. TAMBAH USER (BUAT AUTH & FIRESTORE) ──────────────────────────────
    private fun showAddUserDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_user_form, null)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("➕ Tambah User & Auth")
            .setView(dialogView)
            .setPositiveButton("Simpan", null) // Akan di-override di bawah
            .setNegativeButton("Batal", null)
            .create().apply {
                show()
                getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val email = dialogView.findViewById<EditText>(R.id.etEmail).text.toString().trim()
                    val password = dialogView.findViewById<EditText>(R.id.etPassword).text.toString().trim()
                    val name = dialogView.findViewById<EditText>(R.id.etName).text.toString().trim()

                    if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                        Toast.makeText(requireContext(), "Nama, Email, dan Password wajib diisi!", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    if (password.length < 6) {
                        Toast.makeText(requireContext(), "Password minimal 6 karakter!", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    Toast.makeText(requireContext(), "Membuat akun, mohon tunggu...", Toast.LENGTH_LONG).show()

                    // BUAT AKUN FIREBASE AUTH
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener { authResult ->
                            val uid = authResult.user?.uid ?: return@addOnSuccessListener

                            val userData = extractDataFromForm(dialogView).toMutableMap()
                            userData["role"] = "user"
                            userData["password"] = "" // Keamanan: Jangan simpan password di database

                            // SIMPAN KE FIRESTORE
                            db.collection("users").document(uid)
                                .set(userData, SetOptions.merge())
                                .addOnSuccessListener {
                                    dismiss() // Tutup dialog

                                    // Peringatan Logout
                                    MaterialAlertDialogBuilder(requireContext())
                                        .setTitle("Sukses!")
                                        .setMessage("Akun berhasil dibuat.\n\nSistem Firebase otomatis memindahkan sesi login ke user baru. Anda harus Login Ulang sebagai Admin.")
                                        .setPositiveButton("Login Ulang") { _, _ ->
                                            auth.signOut()
                                            // Redirect ke halaman login. Pastikan Class LoginActivity Anda benar!
                                            val intent = Intent(requireContext(), com.absenku.auth.LoginActivity::class.java)
                                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                            startActivity(intent)
                                        }
                                        .setCancelable(false)
                                        .show()
                                }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(requireContext(), "Gagal buat akun Auth: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }
    }

    // ── 2. EDIT PROFIL (HANYA FIRESTORE) ───────────────────────────────────
    private fun showEditProfileDialog(user: UserItem) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_user_form, null)

        // Sembunyikan kolom password saat Edit Profile
        dialogView.findViewById<TextInputLayout>(R.id.layoutPassword).visibility = View.GONE

        // Isi form dengan data lama
        dialogView.findViewById<EditText>(R.id.etName).setText(user.name)
        dialogView.findViewById<EditText>(R.id.etEmail).setText(user.email)
        dialogView.findViewById<EditText>(R.id.etNikKtp).setText(user.nik_ktp)
        dialogView.findViewById<EditText>(R.id.etNikPerusahaan).setText(user.nik_perusahaan)
        dialogView.findViewById<EditText>(R.id.etPob).setText(user.pob)
        dialogView.findViewById<EditText>(R.id.etDob).setText(user.dob)
        dialogView.findViewById<EditText>(R.id.etGender).setText(user.gender)
        dialogView.findViewById<EditText>(R.id.etReligion).setText(user.religion)
        dialogView.findViewById<EditText>(R.id.etMaritalStatus).setText(user.marital_status)
        dialogView.findViewById<EditText>(R.id.etLastEducation).setText(user.last_education)
        dialogView.findViewById<EditText>(R.id.etPhone).setText(user.phone)
        dialogView.findViewById<EditText>(R.id.etAddressKtp).setText(user.address_ktp)
        dialogView.findViewById<EditText>(R.id.etAddressDomicile).setText(user.address_domicile)
        dialogView.findViewById<EditText>(R.id.etPosition).setText(user.position)
        dialogView.findViewById<EditText>(R.id.etDepartment).setText(user.department)
        dialogView.findViewById<EditText>(R.id.etJoinDate).setText(user.join_date)
        dialogView.findViewById<EditText>(R.id.etStatusKaryawan).setText(user.status_karyawan)
        dialogView.findViewById<EditText>(R.id.etShift).setText(user.shift)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Profil — ${user.name}")
            .setView(dialogView)
            .setPositiveButton("Simpan") { _, _ ->
                val updates = extractDataFromForm(dialogView)

                db.collection("users").document(user.uid)
                    .update(updates)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Profil diperbarui", Toast.LENGTH_SHORT).show()
                        loadUsers()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ── Sisa fungsi lainnya tetap sama (Edit Absen, Rekap, Toggle Role, Delete) ──
    private fun showEditAbsenDialog(user: UserItem) {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, y, m, d ->
            val dateStr = "%04d%02d%02d".format(y, m + 1, d)
            val docId   = "${user.uid}_$dateStr"
            val display = "%04d-%02d-%02d".format(y, m + 1, d)

            db.collection("attendance").document(docId).get()
                .addOnSuccessListener { doc -> showAbsenEditForm(user, docId, display, doc) }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showAbsenEditForm(user: UserItem, docId: String, displayDate: String, existingDoc: com.google.firebase.firestore.DocumentSnapshot) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_absen, null)
        val tvUserNama   = dialogView.findViewById<TextView>(R.id.tvEditAbsenUser)
        val tvDate       = dialogView.findViewById<TextView>(R.id.tvEditAbsenDate)
        val etCheckIn    = dialogView.findViewById<EditText>(R.id.etEditCheckIn)
        val etCheckOut   = dialogView.findViewById<EditText>(R.id.etEditCheckOut)
        val tvStatusInfo = dialogView.findViewById<TextView>(R.id.tvEditAbsenStatus)

        tvUserNama.text = user.name
        tvDate.text     = displayDate

        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val existIn  = existingDoc.getLong("checkInTime")
        val existOut = existingDoc.getLong("checkOutTime")

        etCheckIn.setText(existIn?.let { fmt.format(Date(it)) } ?: "")
        etCheckOut.setText(existOut?.let { fmt.format(Date(it)) } ?: "")
        tvStatusInfo.text = "Status: ${existingDoc.getString("status") ?: "belum absen"}"

        etCheckIn.setOnClickListener { showTimePicker { h, m -> etCheckIn.setText("%02d:%02d".format(h, m)) } }
        etCheckOut.setOnClickListener { showTimePicker { h, m -> etCheckOut.setText("%02d:%02d".format(h, m)) } }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Absen — $displayDate")
            .setView(dialogView)
            .setPositiveButton("Simpan") { _, _ ->
                val dateKey  = docId.substringAfter("_")
                val monthKey = dateKey.take(6)
                val data     = mutableMapOf<String, Any>("uid" to user.uid, "status" to "hadir", "date" to dateKey, "monthKey" to monthKey)

                val inText  = etCheckIn.text.toString().trim()
                val outText = etCheckOut.text.toString().trim()

                if (inText.isNotEmpty()) parseTimeToEpoch(inText, dateKey)?.let { data["checkInTime"] = it }
                if (outText.isNotEmpty()) parseTimeToEpoch(outText, dateKey)?.let { data["checkOutTime"] = it }

                db.collection("attendance").document(docId).set(data, SetOptions.merge())
                    .addOnSuccessListener { Toast.makeText(requireContext(), "Absen diperbarui", Toast.LENGTH_SHORT).show() }
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun showTimePicker(onPick: (Int, Int) -> Unit) {
        val cal = Calendar.getInstance()
        TimePickerDialog(requireContext(), { _, h, m -> onPick(h, m) }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    private fun parseTimeToEpoch(timeStr: String, dateStr: String): Long? {
        return try { SimpleDateFormat("yyyyMMdd HH:mm", Locale.getDefault()).parse("$dateStr $timeStr")?.time } catch (_: Exception) { null }
    }

    private fun showUserRekapDialog(user: UserItem) {
        val monthKey = SimpleDateFormat("yyyyMM", Locale.getDefault()).format(Date())
        db.collection("attendance").whereEqualTo("uid", user.uid).whereEqualTo("monthKey", monthKey).get()
            .addOnSuccessListener { snap ->
                var hadir = 0; var izin = 0; var terlambat = 0
                snap.documents.forEach { doc ->
                    when (doc.getString("status")) {
                        "hadir" -> { hadir++; if (doc.getBoolean("isLate") == true) terlambat++ }
                        "izin"  -> izin++
                    }
                }
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Rekap ${user.name}")
                    .setMessage("Bulan ini:\n\n✅  Hadir: $hadir hari\n📋  Izin: $izin hari\n⚠️  Terlambat: $terlambat kali")
                    .setPositiveButton("OK", null).show()
            }
    }

    private fun showToggleRoleDialog(user: UserItem) {
        val newRole = if (user.role.uppercase() == "ADMIN") "USER" else "ADMIN"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Ubah Role")
            .setMessage("Ubah role ${user.name} menjadi $newRole?")
            .setPositiveButton("Ya") { _, _ ->
                db.collection("users").document(user.uid).update("role", newRole).addOnSuccessListener { loadUsers() }
            }.setNegativeButton("Batal", null).show()
    }

    private fun showDeleteUserDialog(user: UserItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("⚠️ Hapus User")
            .setMessage("Yakin ingin menghapus ${user.name}?")
            .setPositiveButton("Hapus") { _, _ ->
                db.collection("users").document(user.uid).delete().addOnSuccessListener { loadUsers() }
            }.setNegativeButton("Batal", null).show()
    }
}

// Data Class Lengkap
data class UserItem(
    val uid: String, val name: String, val email: String, val role: String,
    val nik_ktp: String, val nik_perusahaan: String, val pob: String, val dob: String,
    val gender: String, val religion: String, val marital_status: String,
    val last_education: String, val phone: String, val address_ktp: String,
    val address_domicile: String, val position: String, val department: String,
    val join_date: String, val status_karyawan: String, val shift: String
)