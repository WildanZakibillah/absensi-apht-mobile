package com.absenku.admin

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.absenku.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class AdminSettingsFragment : Fragment() {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_admin_settings, container, false)

        // Tombol Edit Lokasi
        view.findViewById<MaterialButton>(R.id.btnEditOfficeLocation).setOnClickListener {
            showEditOfficeLocationDialog()
        }

        // Tombol Edit Radius
        view.findViewById<MaterialButton>(R.id.btnEditRadius).setOnClickListener {
            showEditRadiusDialog()
        }

        // Tombol Logout
        view.findViewById<MaterialButton>(R.id.btnAdminLogout).setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Logout Admin")
                .setMessage("Yakin ingin logout?")
                .setPositiveButton("Logout") { _, _ ->
                    auth.signOut()
                    val intent = Intent(requireContext(), com.absenku.auth.LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        loadCurrentSettings(view)
        return view
    }

    private fun loadCurrentSettings(view: View) {
        db.collection("config").document("app_settings").get()
            .addOnSuccessListener { doc ->
                if (doc.exists() && isAdded) {
                    view.findViewById<TextView>(R.id.tvCurrentLat).text =
                        "Lat: ${doc.getDouble("officeLat") ?: "-"}"
                    view.findViewById<TextView>(R.id.tvCurrentLng).text =
                        "Lng: ${doc.getDouble("officeLng") ?: "-"}"
                    view.findViewById<TextView>(R.id.tvCurrentRadius).text =
                        "Radius: ${doc.getLong("radiusMeter") ?: 100}m"
                }
            }
    }

    // ── Helper untuk layout dialog ──────────────────────────────────────────
    private fun createDialogLayout(): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
    }

    // ── Edit Lokasi Kantor ─────────────────────────────────────────────────
    private fun showEditOfficeLocationDialog() {
        val layout = createDialogLayout()

        val etLat = EditText(requireContext()).apply {
            hint = "Latitude (contoh: -7.0521013)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        val etLng = EditText(requireContext()).apply {
            hint = "Longitude (contoh: 113.6630085)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            setPadding(0, 30, 0, 0)
        }

        layout.addView(etLat)
        layout.addView(etLng)

        db.collection("config").document("app_settings").get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    etLat.setText((doc.getDouble("officeLat") ?: -7.0521013).toString())
                    etLng.setText((doc.getDouble("officeLng") ?: 113.6630085).toString())
                }
            }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📍 Edit Lokasi Kantor")
            .setView(layout)
            .setMessage("Masukkan koordinat yang akurat.")
            .setPositiveButton("Simpan") { _, _ ->
                val lat = etLat.text.toString().toDoubleOrNull()
                val lng = etLng.text.toString().toDoubleOrNull()

                if (lat == null || lng == null) {
                    Toast.makeText(requireContext(), "Format koordinat tidak valid", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val data = hashMapOf(
                    "officeLat" to lat,
                    "officeLng" to lng,
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                db.collection("config").document("app_settings")
                    .set(data, SetOptions.merge())
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Lokasi kantor diperbarui", Toast.LENGTH_SHORT).show()
                        loadCurrentSettings(requireView())
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Gagal menyimpan lokasi", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ── Edit Radius ────────────────────────────────────────────────────────
    private fun showEditRadiusDialog() {
        val layout = createDialogLayout()
        val input = EditText(requireContext()).apply {
            hint = "Radius dalam meter (contoh: 100)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        layout.addView(input)

        db.collection("config").document("app_settings").get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    input.setText((doc.getLong("radiusMeter") ?: 100).toString())
                }
            }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📏 Edit Radius Absen")
            .setView(layout)
            .setPositiveButton("Simpan") { _, _ ->
                val radius = input.text.toString().toLongOrNull()
                if (radius == null || radius <= 0) {
                    Toast.makeText(requireContext(), "Radius tidak valid", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val data = hashMapOf(
                    "radiusMeter" to radius,
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                db.collection("config").document("app_settings")
                    .set(data, SetOptions.merge())
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Radius diperbarui ke ${radius}m", Toast.LENGTH_SHORT).show()
                        loadCurrentSettings(requireView())
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Gagal menyimpan radius", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}