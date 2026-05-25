package com.absenku.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.absenku.R
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminHomeFragment : Fragment() {

    private val db by lazy { FirebaseFirestore.getInstance() }

    // Stat views
    private lateinit var tvTotalUsers    : TextView
    private lateinit var tvHadirHariIni  : TextView
    private lateinit var tvIzinHariIni   : TextView
    private lateinit var tvTidakHadir    : TextView
    private lateinit var tvTerlambat     : TextView
    private lateinit var tvPendingIzin   : TextView
    private lateinit var tvTanggal       : TextView
    private lateinit var rvRecentAbsen   : RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_admin_home, container, false)
        bindViews(view)
        loadStats()
        loadRecentAbsen()
        return view
    }

    private fun bindViews(v: View) {
        tvTotalUsers   = v.findViewById(R.id.tvStatTotalUsers)
        tvHadirHariIni = v.findViewById(R.id.tvStatHadir)
        tvIzinHariIni  = v.findViewById(R.id.tvStatIzin)
        tvTidakHadir   = v.findViewById(R.id.tvStatTidakHadir)
        tvTerlambat    = v.findViewById(R.id.tvStatTerlambat)
        tvPendingIzin  = v.findViewById(R.id.tvStatPendingIzin)
        tvTanggal      = v.findViewById(R.id.tvAdminTanggal)
        rvRecentAbsen  = v.findViewById(R.id.rvRecentAbsen)

        val today = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id","ID")).format(Date())
        tvTanggal.text = today

        rvRecentAbsen.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun loadStats() {
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

        // Total users
        db.collection("users").get().addOnSuccessListener { snap ->
            tvTotalUsers.text = snap.size().toString()
        }

        // Attendance hari ini
        db.collection("attendance")
            .whereEqualTo("date", today)
            .get()
            .addOnSuccessListener { snap ->
                var hadir    = 0
                var izin     = 0
                var terlambat = 0
                snap.documents.forEach { doc ->
                    when (doc.getString("status")) {
                        "hadir" -> {
                            hadir++
                            if (doc.getBoolean("isLate") == true) terlambat++
                        }
                        "izin"  -> izin++
                    }
                }
                tvHadirHariIni.text = hadir.toString()
                tvIzinHariIni.text  = izin.toString()
                tvTerlambat.text    = terlambat.toString()

                // Tidak hadir = total users - hadir - izin
                db.collection("users").get().addOnSuccessListener { userSnap ->
                    val total    = userSnap.size()
                    val absen    = total - hadir - izin
                    tvTidakHadir.text = if (absen < 0) "0" else absen.toString()
                }
            }

        // Pending izin
        db.collection("izin")
            .whereEqualTo("approvalStatus", "pending")
            .get()
            .addOnSuccessListener { snap ->
                tvPendingIzin.text = snap.size().toString()
            }
    }

    private fun loadRecentAbsen() {
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        db.collection("attendance")
            .whereEqualTo("date", today)
            .limit(20)
            .get()
            .addOnSuccessListener { snap ->
                val items = snap.documents.mapNotNull { doc ->
                    val uid      = doc.getString("uid") ?: return@mapNotNull null
                    val status   = doc.getString("status") ?: "-"
                    val isLate   = doc.getBoolean("isLate") ?: false
                    val checkIn  = doc.getLong("checkInTime")
                    val checkOut = doc.getLong("checkOutTime")
                    val timeIn   = checkIn?.let {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
                    } ?: "-"
                    val timeOut  = checkOut?.let {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
                    } ?: "-"
                    RecentAbsenItem(uid, status, isLate, timeIn, timeOut)
                }

                // Enrich dengan nama user
                val enriched = mutableListOf<RecentAbsenItem>()
                var loaded   = 0
                if (items.isEmpty()) {
                    rvRecentAbsen.adapter = RecentAbsenAdapter(emptyList())
                    return@addOnSuccessListener
                }
                items.forEach { item ->
                    db.collection("users").document(item.uid).get()
                        .addOnSuccessListener { uDoc ->
                            val nama = uDoc.getString("nama") ?: uDoc.getString("name") ?: item.uid
                            enriched.add(item.copy(uid = nama))
                            loaded++
                            if (loaded == items.size) {
                                rvRecentAbsen.adapter = RecentAbsenAdapter(enriched)
                            }
                        }
                        .addOnFailureListener {
                            enriched.add(item)
                            loaded++
                            if (loaded == items.size) {
                                rvRecentAbsen.adapter = RecentAbsenAdapter(enriched)
                            }
                        }
                }
            }
    }
}

data class RecentAbsenItem(
    val uid      : String,
    val status   : String,
    val isLate   : Boolean,
    val timeIn   : String,
    val timeOut  : String
)