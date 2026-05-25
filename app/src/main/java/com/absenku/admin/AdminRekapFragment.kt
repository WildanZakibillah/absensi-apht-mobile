package com.absenku.admin

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.absenku.R
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AdminRekapFragment : Fragment() {

    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var spinnerBulan  : Spinner
    private lateinit var spinnerTahun  : Spinner
    private lateinit var rvRekap       : RecyclerView
    private lateinit var tvSummary     : TextView
    private lateinit var btnDownloadCsv: MaterialButton
    private lateinit var btnDownloadTxt: MaterialButton

    private var selectedMonthKey = ""
    private var rekapData        = listOf<RekapRowItem>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_admin_rekap, container, false)

        spinnerBulan   = view.findViewById(R.id.spinnerBulan)
        spinnerTahun   = view.findViewById(R.id.spinnerTahun)
        rvRekap        = view.findViewById(R.id.rvAdminRekap)
        tvSummary      = view.findViewById(R.id.tvRekapSummary)
        btnDownloadCsv = view.findViewById(R.id.btnDownloadCsv)
        btnDownloadTxt = view.findViewById(R.id.btnDownloadTxt)

        rvRekap.layoutManager = LinearLayoutManager(requireContext())
        setupSpinners()

        btnDownloadCsv.setOnClickListener { downloadCsv() }
        btnDownloadTxt.setOnClickListener { downloadTxt() }

        return view
    }

    // ── Spinner Bulan & Tahun ─────────────────────────────────────────────
    private fun setupSpinners() {
        val bulanList = listOf(
            "Januari","Februari","Maret","April","Mei","Juni",
            "Juli","Agustus","September","Oktober","November","Desember"
        )
        val cal        = Calendar.getInstance()
        val curMonth   = cal.get(Calendar.MONTH)   // 0-based
        val curYear    = cal.get(Calendar.YEAR)
        val tahunList  = ((curYear - 3)..(curYear + 1)).map { it.toString() }

        spinnerBulan.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_dropdown_item, bulanList)
        spinnerTahun.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_dropdown_item, tahunList)

        spinnerBulan.setSelection(curMonth)
        spinnerTahun.setSelection(tahunList.indexOf(curYear.toString()))

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val bulan = (spinnerBulan.selectedItemPosition + 1).toString().padStart(2, '0')
                val tahun = spinnerTahun.selectedItem.toString()
                selectedMonthKey = "$tahun$bulan"
                loadRekap()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinnerBulan.onItemSelectedListener = listener
        spinnerTahun.onItemSelectedListener = listener
    }

    // ── Load Rekap ─────────────────────────────────────────────────────────
    private fun loadRekap() {
        if (selectedMonthKey.isEmpty()) return

        // Load semua user
        db.collection("users").get().addOnSuccessListener { userSnap ->
            val users = userSnap.documents.mapNotNull { doc ->
                val uid  = doc.id
                val nama = doc.getString("nama") ?: doc.getString("name") ?: uid
                val nip  = doc.getString("nip") ?: "-"
                Triple(uid, nama, nip)
            }

            // Load attendance bulan ini
            db.collection("attendance")
                .whereEqualTo("monthKey", selectedMonthKey)
                .get()
                .addOnSuccessListener { attSnap ->

                    // Group by uid
                    val attByUid = mutableMapOf<String, MutableList<com.google.firebase.firestore.DocumentSnapshot>>()
                    attSnap.documents.forEach { doc ->
                        val uid = doc.getString("uid") ?: return@forEach
                        attByUid.getOrPut(uid) { mutableListOf() }.add(doc)
                    }

                    // Load izin bulan ini
                    db.collection("izin")
                        .whereEqualTo("monthKey", selectedMonthKey)
                        .whereEqualTo("approvalStatus", "approved")
                        .get()
                        .addOnSuccessListener { izinSnap ->

                            val izinByUid = mutableMapOf<String, Int>()
                            izinSnap.documents.forEach { doc ->
                                val uid = doc.getString("uid") ?: return@forEach
                                izinByUid[uid] = (izinByUid[uid] ?: 0) + 1
                            }

                            val rows = users.map { (uid, nama, nip) ->
                                val docs      = attByUid[uid] ?: emptyList()
                                val hadir     = docs.count { it.getString("status") == "hadir" }
                                val izin      = (izinByUid[uid] ?: 0) +
                                        docs.count { it.getString("status") == "izin" }
                                val terlambat = docs.count {
                                    it.getString("status") == "hadir" && it.getBoolean("isLate") == true
                                }
                                val alpha     = maxOf(0, 26 - hadir - izin)

                                RekapRowItem(uid, nama, nip, hadir, izin, terlambat, alpha)
                            }.sortedBy { it.nama }

                            rekapData = rows

                            // Summary
                            val totalHadir     = rows.sumOf { it.hadir }
                            val totalIzin      = rows.sumOf { it.izin }
                            val totalTerlambat = rows.sumOf { it.terlambat }
                            tvSummary.text =
                                "Total Hadir: $totalHadir  •  Izin: $totalIzin  •  Terlambat: $totalTerlambat"

                            rvRekap.adapter = AdminRekapAdapter(rows)
                        }
                }
        }
    }

    // ── Download CSV ────────────────────────────────────────────────────────
    private fun downloadCsv() {
        if (rekapData.isEmpty()) {
            Toast.makeText(requireContext(), "Tidak ada data untuk diunduh", Toast.LENGTH_SHORT).show()
            return
        }

        val sb = StringBuilder()
        sb.appendLine("NIP,Nama,Hadir,Izin,Terlambat,Alpha")
        rekapData.forEach { row ->
            sb.appendLine("${row.nip},\"${row.nama}\",${row.hadir},${row.izin},${row.terlambat},${row.alpha}")
        }

        val fileName = "rekap_absen_${selectedMonthKey}.csv"
        saveToDownloads(fileName, "text/csv", sb.toString().toByteArray())
    }

    // ── Download TXT (laporan teks) ─────────────────────────────────────────
    private fun downloadTxt() {
        if (rekapData.isEmpty()) {
            Toast.makeText(requireContext(), "Tidak ada data untuk diunduh", Toast.LENGTH_SHORT).show()
            return
        }

        val bulanNama = SimpleDateFormat("MMMM yyyy", Locale("id","ID"))
            .format(SimpleDateFormat("yyyyMM", Locale.getDefault()).parse(selectedMonthKey)!!)

        val sb = StringBuilder()
        sb.appendLine("═══════════════════════════════════════")
        sb.appendLine("  REKAP ABSENSI — ${bulanNama.uppercase()}")
        sb.appendLine("  Dicetak: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id","ID")).format(Date())}")
        sb.appendLine("═══════════════════════════════════════")
        sb.appendLine()
        sb.appendLine(String.format("%-4s %-20s %6s %5s %10s %6s", "No", "Nama", "Hadir", "Izin", "Terlambat", "Alpha"))
        sb.appendLine("-".repeat(55))
        rekapData.forEachIndexed { i, row ->
            sb.appendLine(String.format("%-4d %-20s %6d %5d %10d %6d",
                i + 1, row.nama.take(20), row.hadir, row.izin, row.terlambat, row.alpha))
        }
        sb.appendLine("-".repeat(55))
        sb.appendLine()
        sb.appendLine("Total Hadir     : ${rekapData.sumOf { it.hadir }}")
        sb.appendLine("Total Izin      : ${rekapData.sumOf { it.izin }}")
        sb.appendLine("Total Terlambat : ${rekapData.sumOf { it.terlambat }}")

        val fileName = "rekap_absen_${selectedMonthKey}.txt"
        saveToDownloads(fileName, "text/plain", sb.toString().toByteArray())
    }

    // ── Save File ke Downloads ──────────────────────────────────────────────
    private fun saveToDownloads(fileName: String, mimeType: String, data: ByteArray) {
        try {
            val outputStream: OutputStream?

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = requireContext().contentResolver
                val uri      = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                outputStream = uri?.let { resolver.openOutputStream(it) }
            } else {
                val dir  = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = java.io.File(dir, fileName)
                outputStream = java.io.FileOutputStream(file)
            }

            outputStream?.use { it.write(data) }
            Toast.makeText(requireContext(),
                "✅ File tersimpan di Downloads/$fileName", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(requireContext(),
                "Gagal menyimpan: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

data class RekapRowItem(
    val uid       : String,
    val nama      : String,
    val nip       : String,
    val hadir     : Int,
    val izin      : Int,
    val terlambat : Int,
    val alpha     : Int
)