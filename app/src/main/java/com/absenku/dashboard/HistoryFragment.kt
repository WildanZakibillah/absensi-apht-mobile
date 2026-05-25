package com.absenku.dashboard

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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

class HistoryFragment : Fragment() {

    companion object {
        fun newInstance() = HistoryFragment()
    }

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db   by lazy { FirebaseFirestore.getInstance() }
    private var firestoreListener: ListenerRegistration? = null

    private lateinit var tvMonthYear     : TextView
    private lateinit var btnPrevMonth    : ImageView
    private lateinit var btnNextMonth    : ImageView
    private lateinit var recycler        : RecyclerView
    private lateinit var layoutEmpty     : LinearLayout
    private lateinit var layoutLoading   : LinearLayout

    // Inisialisasi Adapter dan set listener klik untuk membuka Detail
    private val historyAdapter = HistoryAdapter { clickedItem ->
        val fragment = DetailHistoryFragment.newInstance(clickedItem)
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right,
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right
            )
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    // Calendar untuk melacak bulan yang sedang dipilih
    private val selectedMonth: Calendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_history, container, false)
        bindViews(view)

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = historyAdapter

        setupMonthPicker()
        return view
    }

    override fun onStart() {
        super.onStart()
        loadHistory()
    }

    override fun onStop() {
        super.onStop()
        firestoreListener?.remove()
        firestoreListener = null
    }

    private fun bindViews(v: View) {
        tvMonthYear     = v.findViewById(R.id.tvMonthYear)
        btnPrevMonth    = v.findViewById(R.id.btnPrevMonth)
        btnNextMonth    = v.findViewById(R.id.btnNextMonth)
        recycler        = v.findViewById(R.id.rvHistory)
        layoutEmpty     = v.findViewById(R.id.layoutEmpty)
        layoutLoading   = v.findViewById(R.id.layoutLoading)
    }

    private fun setupMonthPicker() {
        refreshMonthUI()

        btnPrevMonth.setOnClickListener {
            selectedMonth.add(Calendar.MONTH, -1)
            refreshMonthUI()
            loadHistory()
        }

        btnNextMonth.setOnClickListener {
            val now = Calendar.getInstance()
            val isCurrentOrFuture =
                selectedMonth.get(Calendar.YEAR) > now.get(Calendar.YEAR) ||
                        (selectedMonth.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                                selectedMonth.get(Calendar.MONTH) >= now.get(Calendar.MONTH))

            if (isCurrentOrFuture) {
                Toast.makeText(requireContext(), "Belum ada data bulan depan", Toast.LENGTH_SHORT).show()
            } else {
                selectedMonth.add(Calendar.MONTH, 1)
                refreshMonthUI()
                loadHistory()
            }
        }
    }

    private fun refreshMonthUI() {
        val fmtLabel = SimpleDateFormat("MMM yyyy", Locale("id", "ID"))
        tvMonthYear.text = fmtLabel.format(selectedMonth.time)
    }

    private fun loadHistory() {
        // PENGAMAN MUTLAK: Hanya ambil UID milik user yang sedang login!
        val uid = auth.currentUser?.uid ?: return

        // Ubah bulan yang dipilih menjadi format "yyyyMM" (Misal: 202604)
        val currentMonthKey = SimpleDateFormat("yyyyMM", Locale.getDefault()).format(selectedMonth.time)

        layoutLoading.visibility = View.VISIBLE
        firestoreListener?.remove()

        // Ambil data yang UID dan monthKey-nya cocok dengan milik user
        firestoreListener = db.collection("attendance")
            .whereEqualTo("uid", uid)
            .whereEqualTo("monthKey", currentMonthKey)
            .addSnapshotListener { snapshot, error ->
                layoutLoading.visibility = View.GONE
                if (error != null || snapshot == null) return@addSnapshotListener

                val items: List<HistoryItem> = snapshot.documents.mapNotNull { doc ->
                    HistoryItem(
                        checkInTime   = doc.getLong("checkInTime") ?: return@mapNotNull null,
                        checkOutTime  = doc.getLong("checkOutTime"),
                        status        = doc.getString("status") ?: "hadir",
                        isLate        = doc.getBoolean("isLate") ?: false,
                        checkInNotes  = doc.getString("checkInNotes") ?: "",
                        checkOutNotes = doc.getString("checkOutNotes") ?: "",
                        checkInLat    = doc.getDouble("checkInLat"),
                        checkInLng    = doc.getDouble("checkInLng")
                    )
                }

                // Mengurutkan data dari tanggal terbaru ke terlama secara lokal agar tidak error Index Firestore
                val sortedItems = items.sortedByDescending { it.checkInTime }

                historyAdapter.submitList(sortedItems)
                layoutEmpty.visibility = if (sortedItems.isEmpty()) View.VISIBLE else View.GONE
                recycler.visibility    = if (sortedItems.isEmpty()) View.GONE else View.VISIBLE
            }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Data Model
// ════════════════════════════════════════════════════════════════════════════

data class HistoryItem(
    val checkInTime   : Long,
    val checkOutTime  : Long?,
    val status        : String,
    val isLate        : Boolean,
    val checkInNotes  : String,
    val checkOutNotes : String,
    val checkInLat    : Double?,
    val checkInLng    : Double?
)

// ════════════════════════════════════════════════════════════════════════════
//  RecyclerView Adapter
// ════════════════════════════════════════════════════════════════════════════

class HistoryAdapter(private val onItemClick: (HistoryItem) -> Unit) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    private val items = mutableListOf<HistoryItem>()

    fun submitList(newItems: List<HistoryItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return VH(view, onItemClick)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    class VH(itemView: View, private val onClick: (HistoryItem) -> Unit) : RecyclerView.ViewHolder(itemView) {

        private val tvDay           : TextView         = itemView.findViewById(R.id.tvDay)
        private val tvDayName       : TextView         = itemView.findViewById(R.id.tvDayName)
        private val tvFullDate      : TextView         = itemView.findViewById(R.id.tvFullDate)
        private val tvCheckIn       : TextView         = itemView.findViewById(R.id.tvCheckIn)
        private val tvCheckOut      : TextView         = itemView.findViewById(R.id.tvCheckOut)
        private val cardCheckOut    : MaterialCardView = itemView.findViewById(R.id.cardCheckOutBox)
        private val tvDuration      : TextView         = itemView.findViewById(R.id.tvDuration)
        private val tvStatusBadge   : TextView         = itemView.findViewById(R.id.tvStatusBadge)
        private val cardStatusBadge : MaterialCardView = itemView.findViewById(R.id.cardStatusBadge)
        private val cardLateBadge   : MaterialCardView = itemView.findViewById(R.id.cardLateBadge)
        private val tvNotes         : TextView         = itemView.findViewById(R.id.tvNotes)
        private val layoutNotes     : LinearLayout     = itemView.findViewById(R.id.layoutNotes)
        private val dividerLine     : View             = itemView.findViewById(R.id.dividerLine)

        private val dayFmt  = SimpleDateFormat("dd",                    Locale.getDefault())
        private val dayName = SimpleDateFormat("EEE",                   Locale("id", "ID"))
        private val fullFmt = SimpleDateFormat("EEEE, dd MMMM yyyy",    Locale("id", "ID"))
        private val timeFmt = SimpleDateFormat("HH:mm",                 Locale.getDefault())

        fun bind(item: HistoryItem) {
            val date = Date(item.checkInTime)

            tvDay.text      = dayFmt.format(date)
            tvDayName.text  = dayName.format(date)

            // Cek apakah XML item_history punya tvFullDate (jika null, abaikan tanpa error)
            tvFullDate?.text = fullFmt.format(date)

            tvCheckIn.text  = timeFmt.format(date)

            if (item.checkOutTime != null) {
                tvCheckOut?.text        = timeFmt.format(Date(item.checkOutTime))
                cardCheckOut?.visibility = View.VISIBLE
                dividerLine?.visibility  = View.VISIBLE
                val durationMs          = item.checkOutTime - item.checkInTime
                val hours               = (durationMs / (1000 * 60 * 60)).toInt()
                val minutes             = ((durationMs / (1000 * 60)) % 60).toInt()
                tvDuration?.text         = "⏱  ${hours}j ${minutes}m"
                tvDuration?.visibility   = View.VISIBLE
            } else {
                cardCheckOut?.visibility = View.GONE
                tvDuration?.visibility   = View.GONE
                dividerLine?.visibility  = View.GONE
            }

            val (label, txtColor, bgColor) = when (item.status.lowercase()) {
                "izin"  -> Triple("Izin",  "#F59E0B", "#FFF8E7")
                "sakit" -> Triple("Sakit", "#EF4444", "#FFF0F0")
                else    -> Triple("Hadir", "#4ECCA3", "#E8FBF4")
            }
            tvStatusBadge.text = label
            tvStatusBadge.setTextColor(Color.parseColor(txtColor))
            cardStatusBadge.setCardBackgroundColor(Color.parseColor(bgColor))

            cardLateBadge?.visibility = if (item.isLate) View.VISIBLE else View.GONE

            val combinedNotes = buildList {
                if (item.checkInNotes.isNotEmpty())  add("📥 ${item.checkInNotes}")
                if (item.checkOutNotes.isNotEmpty()) add("📤 ${item.checkOutNotes}")
            }.joinToString("\n")

            if (combinedNotes.isNotEmpty()) {
                tvNotes?.text           = combinedNotes
                layoutNotes?.visibility = View.VISIBLE
            } else {
                layoutNotes?.visibility = View.GONE
            }

            // Atur aksi klik pada item recycler untuk membuka halaman detail
            itemView.setOnClickListener {
                onClick(item)
            }
        }
    }
}