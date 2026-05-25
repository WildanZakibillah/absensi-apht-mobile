package com.absenku.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.absenku.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminIzinFragment : Fragment() {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private lateinit var rvIzin    : RecyclerView
    private lateinit var tvCount   : TextView
    private lateinit var chipGroup : ChipGroup

    private var allIzin     = listOf<IzinItem>()
    private var filterStatus = "pending" // pending | approved | rejected | all

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_admin_izin, container, false)
        rvIzin    = view.findViewById(R.id.rvAdminIzin)
        tvCount   = view.findViewById(R.id.tvIzinCount)
        chipGroup = view.findViewById(R.id.chipGroupIzinFilter)

        rvIzin.layoutManager = LinearLayoutManager(requireContext())

        setupChipFilter()
        loadIzin()
        return view
    }

    private fun setupChipFilter() {
        val chipPending  = chipGroup.findViewById<Chip>(R.id.chipPending)
        val chipApproved = chipGroup.findViewById<Chip>(R.id.chipApproved)
        val chipRejected = chipGroup.findViewById<Chip>(R.id.chipRejected)
        val chipAll      = chipGroup.findViewById<Chip>(R.id.chipAllIzin)

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            filterStatus = when {
                checkedIds.contains(R.id.chipPending)  -> "pending"
                checkedIds.contains(R.id.chipApproved) -> "approved"
                checkedIds.contains(R.id.chipRejected) -> "rejected"
                else                                   -> "all"
            }
            applyFilter()
        }
        chipPending?.isChecked = true
    }

    private fun loadIzin() {
        db.collection("izin")
            .orderBy("submitTime", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .addOnSuccessListener { snap ->
                val rawList = snap.documents.mapNotNull { doc ->
                    val uid      = doc.getString("uid") ?: return@mapNotNull null
                    val type     = doc.getString("izinType") ?: "-"
                    val status   = doc.getString("approvalStatus") ?: "pending"
                    val alasan   = doc.getString("alasan") ?: ""
                    val submitMs = doc.getLong("submitTime") ?: 0L
                    val date     = doc.getString("date") ?: "-"
                    val lat      = doc.getDouble("lat") ?: 0.0
                    val lng      = doc.getDouble("lng") ?: 0.0
                    IzinItem(doc.id, uid, uid, type, status, alasan, submitMs, date, lat, lng)
                }

                // Enrich nama
                val enriched = rawList.toMutableList()
                var loaded   = 0
                if (rawList.isEmpty()) {
                    allIzin = emptyList()
                    applyFilter()
                    return@addOnSuccessListener
                }
                rawList.forEachIndexed { i, item ->
                    db.collection("users").document(item.uid).get()
                        .addOnSuccessListener { uDoc ->
                            val nama = uDoc.getString("nama") ?: uDoc.getString("name") ?: item.uid
                            enriched[i] = item.copy(namaUser = nama)
                            loaded++
                            if (loaded == rawList.size) {
                                allIzin = enriched
                                applyFilter()
                            }
                        }
                        .addOnFailureListener {
                            loaded++
                            if (loaded == rawList.size) {
                                allIzin = enriched
                                applyFilter()
                            }
                        }
                }
            }
    }

    private fun applyFilter() {
        val filtered = if (filterStatus == "all") allIzin
        else allIzin.filter { it.approvalStatus == filterStatus }

        tvCount.text = "${filtered.size} pengajuan"
        rvIzin.adapter = AdminIzinAdapter(filtered) { item -> showIzinDetailDialog(item) }
    }

    // ── Dialog Detail Izin ────────────────────────────────────────────────
    private fun showIzinDetailDialog(item: IzinItem) {
        val fmt  = SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID"))
        val time = fmt.format(Date(item.submitTimeMs))

        val msg = buildString {
            appendLine("Nama       : ${item.namaUser}")
            appendLine("Tanggal    : ${item.date}")
            appendLine("Waktu      : $time")
            appendLine("Jenis      : ${item.izinType}")
            if (item.alasan.isNotEmpty()) appendLine("Alasan     : ${item.alasan}")
            appendLine("Status     : ${item.approvalStatus.uppercase()}")
            appendLine()
            if (item.lat != 0.0) appendLine("Lokasi     : ${item.lat}, ${item.lng}")
        }

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Detail Izin")
            .setMessage(msg)
            .setNegativeButton("Tutup", null)

        if (item.approvalStatus == "pending") {
            builder
                .setPositiveButton("✅ Setujui") { _, _ ->
                    updateApprovalStatus(item.docId, "approved", item.namaUser)
                }
                .setNeutralButton("❌ Tolak") { _, _ ->
                    showRejectReasonDialog(item)
                }
        }
        builder.show()
    }

    private fun showRejectReasonDialog(item: IzinItem) {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "Alasan penolakan (opsional)"
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Tolak Izin — ${item.namaUser}")
            .setView(input)
            .setPositiveButton("Tolak") { _, _ ->
                val alasan = input.text.toString().trim()
                updateApprovalStatus(item.docId, "rejected", item.namaUser, alasan)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun updateApprovalStatus(
        docId     : String,
        newStatus : String,
        namaUser  : String,
        alasanTolak: String = ""
    ) {
        val updates = mutableMapOf<String, Any>("approvalStatus" to newStatus)
        if (alasanTolak.isNotEmpty()) updates["alasanTolak"] = alasanTolak
        updates["processedTime"] = System.currentTimeMillis()

        db.collection("izin").document(docId)
            .update(updates)
            .addOnSuccessListener {
                val label = if (newStatus == "approved") "disetujui" else "ditolak"
                Toast.makeText(requireContext(),
                    "Izin $namaUser berhasil $label", Toast.LENGTH_SHORT).show()
                loadIzin()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}

data class IzinItem(
    val docId          : String,
    val uid            : String,
    val namaUser       : String,
    val izinType       : String,
    val approvalStatus : String,
    val alasan         : String,
    val submitTimeMs   : Long,
    val date           : String,
    val lat            : Double,
    val lng            : Double
)