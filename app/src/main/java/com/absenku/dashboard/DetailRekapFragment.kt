package com.absenku.dashboard

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.absenku.R
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

class DetailRekapFragment : Fragment() {

    companion object {
        private const val ARG_STATUS       = "status"
        private const val ARG_MONTH_KEY    = "monthKey"
        private const val ARG_MONTH_LABEL  = "monthLabel"

        fun newInstance(status: String, monthKey: String, monthLabel: String): DetailRekapFragment {
            return DetailRekapFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_STATUS,      status)
                    putString(ARG_MONTH_KEY,   monthKey)
                    putString(ARG_MONTH_LABEL, monthLabel)
                }
            }
        }
    }

    // ── Firebase ──────────────────────────────────────────────────────────────
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db   by lazy { FirebaseFirestore.getInstance() }

    // ── Views ─────────────────────────────────────────────────────────────────
    private lateinit var toolbar       : Toolbar
    private lateinit var tvSummary     : TextView
    private lateinit var tvSummaryLabel: TextView
    private lateinit var layoutEmpty   : LinearLayout
    private lateinit var recycler      : RecyclerView

    // ── State ─────────────────────────────────────────────────────────────────
    private val rekapAdapter = RekapAdapter()
    private var firestoreListener: ListenerRegistration? = null

    private val statusFilter by lazy { arguments?.getString(ARG_STATUS) ?: "hadir" }
    private val monthKey     by lazy { arguments?.getString(ARG_MONTH_KEY) ?: "" }
    private val monthLabel   by lazy { arguments?.getString(ARG_MONTH_LABEL) ?: "" }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_detailrekap, container, false)

        toolbar        = view.findViewById(R.id.toolbar)
        tvSummary      = view.findViewById(R.id.tvSummaryCount)
        tvSummaryLabel = view.findViewById(R.id.tvSummaryLabel)
        layoutEmpty    = view.findViewById(R.id.layoutEmpty)
        recycler       = view.findViewById(R.id.rvDetail)

        toolbar.title    = statusFilter.replaceFirstChar { it.uppercase() }
        toolbar.subtitle = monthLabel
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Penyesuaian warna aksen berdasarkan status (Hadir, Izin, Terlambat)
        val accentColor = when (statusFilter.lowercase()) {
            "izin"      -> "#3B82F6" // Biru Izin
            "terlambat" -> "#F59E0B" // Oren Terlambat
            else        -> "#10B981" // Hijau Hadir
        }
        tvSummary.setTextColor(Color.parseColor(accentColor))

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = rekapAdapter

        return view
    }

    override fun onStart() {
        super.onStart()
        loadDetail()
    }

    override fun onStop() {
        super.onStop()
        firestoreListener?.remove()
        firestoreListener = null
    }

    // ── Load Data Firestore ───────────────────────────────────────────────────

    private fun loadDetail() {
        val uid = auth.currentUser?.uid ?: return
        if (monthKey.length < 6) return

        firestoreListener?.remove()

        // Pisahkan logika pencarian koleksi berdasarkan status
        if (statusFilter.lowercase() == "izin") {
            loadIzinData(uid)
        } else {
            loadAttendanceData(uid)
        }
    }

    private fun loadAttendanceData(uid: String) {
        try {
            val cal = Calendar.getInstance().apply {
                val parsed = SimpleDateFormat("yyyyMM", Locale.getDefault()).parse(monthKey)
                time = parsed ?: Date()
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startMs = cal.timeInMillis

            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            val endMs = cal.timeInMillis

            // PERBAIKAN SINTAKS QUERY FIREBASE DI SINI (Hapus 'field=' dan 'value=')
            firestoreListener = db.collection("attendance")
                .whereEqualTo("uid", uid)
                .whereGreaterThanOrEqualTo("checkInTime", startMs)
                .whereLessThanOrEqualTo("checkInTime", endMs)
                .orderBy("checkInTime")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("AbsenKu", "Listen failed.", error)
                        return@addSnapshotListener
                    }
                    if (snapshot == null) return@addSnapshotListener

                    val items = mutableListOf<RekapItem>()
                    for (doc in snapshot.documents) {
                        val status = doc.getString("status")?.lowercase() ?: "hadir"

                        // Filtering data Hadir vs Terlambat
                        if (statusFilter == "terlambat" && status != "terlambat") continue
                        if (statusFilter == "hadir" && status == "terlambat") continue

                        val checkIn = doc.getLong("checkInTime") ?: continue
                        val checkOut = doc.getLong("checkOutTime")

                        items.add(
                            RekapItem(
                                timestamp = checkIn,
                                checkOutTime = checkOut,
                                status = statusFilter,
                                izinType = null
                            )
                        )
                    }
                    updateUI(items)
                }
        } catch (e: Exception) {
            Log.e("AbsenKu", "Error parsing date: ${e.message}")
        }
    }

    private fun loadIzinData(uid: String) {
        firestoreListener = db.collection("izin")
            .whereEqualTo("monthKey", monthKey)
            .whereEqualTo("approvalStatus", "approved")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("AbsenKu", "Listen failed.", error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val items = mutableListOf<RekapItem>()
                val sdfStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

                for (doc in snapshot.documents) {
                    // Hanya ambil data milik user yang sedang login
                    if (!doc.id.startsWith(uid)) continue

                    val dateStr = doc.getString("date") ?: continue
                    val izinType = doc.getString("izinType") ?: "Izin Pribadi"
                    val absentType = doc.getString("absentType") ?: "full"

                    var timestamp: Long = 0
                    try {
                        val parsedDate = sdfStr.parse(dateStr)
                        if (parsedDate != null) timestamp = parsedDate.time
                    } catch (e: Exception) {
                        continue
                    }

                    items.add(
                        RekapItem(
                            timestamp = timestamp,
                            checkOutTime = null,
                            status = "izin",
                            izinType = "$izinType ($absentType)"
                        )
                    )
                }

                // Urutkan manual dari tanggal terbaru
                items.sortByDescending { it.timestamp }
                updateUI(items)
            }
    }

    private fun updateUI(items: List<RekapItem>) {
        rekapAdapter.submitList(items)

        val total = items.size
        tvSummary.text = "$total"
        tvSummaryLabel.text = "Total Hari ${statusFilter.replaceFirstChar { it.uppercase() }}"

        layoutEmpty.visibility = if (total == 0) View.VISIBLE else View.GONE
        recycler.visibility    = if (total == 0) View.GONE    else View.VISIBLE
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Data Model (Diperbarui untuk mengakomodasi Izin)
// ════════════════════════════════════════════════════════════════════════════

data class RekapItem(
    val timestamp   : Long,
    val checkOutTime: Long?,
    val status      : String,
    val izinType    : String?
)

// ════════════════════════════════════════════════════════════════════════════
//  RecyclerView Adapter
// ════════════════════════════════════════════════════════════════════════════

class RekapAdapter : RecyclerView.Adapter<RekapAdapter.VH>() {

    private val items = mutableListOf<RekapItem>()

    fun submitList(newItems: List<RekapItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rekap, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val tvDay        : TextView         = itemView.findViewById(R.id.tvDay)
        private val tvDayName    : TextView         = itemView.findViewById(R.id.tvDayName)
        private val tvFullDate   : TextView         = itemView.findViewById(R.id.tvFullDate)
        private val tvCheckInOut : TextView         = itemView.findViewById(R.id.tvCheckInOut)
        private val tvStatusBadge: TextView         = itemView.findViewById(R.id.tvStatusBadge)
        private val cardBadge    : MaterialCardView = itemView.findViewById(R.id.cardStatusBadge)

        private val dayFmt  = SimpleDateFormat("dd",   Locale.getDefault())
        private val dayName = SimpleDateFormat("EEE",  Locale("id", "ID"))
        private val fullFmt = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID"))
        private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun bind(item: RekapItem) {
            val date = Date(item.timestamp)

            tvDay.text       = dayFmt.format(date)
            tvDayName.text   = dayName.format(date)
            tvFullDate.text  = fullFmt.format(date)

            // Atur teks bagian bawah (Jam Absen vs Keterangan Izin)
            if (item.status.lowercase() == "izin") {
                tvCheckInOut.text = "Tipe Izin: ${item.izinType}"
            } else {
                val masuk  = timeFmt.format(date)
                val pulang = item.checkOutTime?.let { timeFmt.format(Date(it)) } ?: "Belum"
                tvCheckInOut.text = "Masuk: $masuk  ·  Pulang: $pulang"
            }

            // Sesuaikan warna status card
            val (label, txtColor, bgColor) = when (item.status.lowercase()) {
                "izin"      -> Triple("Izin", "#3B82F6", "#DBEAFE")      // Biru
                "terlambat" -> Triple("Terlambat", "#F59E0B", "#FEF3C7") // Oren
                else        -> Triple("Hadir", "#10B981", "#D1FAE5")     // Hijau
            }

            tvStatusBadge.text = label
            tvStatusBadge.setTextColor(Color.parseColor(txtColor))
            cardBadge.setCardBackgroundColor(Color.parseColor(bgColor))
        }
    }
}