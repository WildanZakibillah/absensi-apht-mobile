package com.absenku.dashboard

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.absenku.R
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db   by lazy { FirebaseFirestore.getInstance() }

    private lateinit var tvName           : TextView
    private lateinit var tvGreeting       : TextView
    private lateinit var tvClock          : TextView
    private lateinit var tvDate           : TextView
    private lateinit var tvScheduleIn     : TextView
    private lateinit var tvScheduleOut    : TextView
    private lateinit var tvStatusIn       : TextView
    private lateinit var tvStatusOut      : TextView
    private lateinit var cardCheckIn      : MaterialCardView
    private lateinit var cardCheckOut     : MaterialCardView
    private lateinit var cardHadir        : MaterialCardView
    private lateinit var cardTerlambat    : MaterialCardView
    private lateinit var cardIzin         : MaterialCardView
    private lateinit var btnNotification  : MaterialCardView
    private lateinit var tvMonthYear      : TextView
    private lateinit var btnPrevMonth     : ImageView
    private lateinit var btnNextMonth     : ImageView
    private lateinit var tvHadirCount     : TextView
    private lateinit var tvTerlambatCount : TextView
    private lateinit var tvIzinCount      : TextView
    private lateinit var tvStatHadir      : TextView
    private lateinit var tvStatTerlambat  : TextView
    private lateinit var tvStatIzin       : TextView
    private lateinit var progressBar      : ProgressBar
    private lateinit var tvProgressPct    : TextView
    private lateinit var tvProgressDetail : TextView

    private val handler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClockAndDate()
            handler.postDelayed(this, 1000)
        }
    }

    private val selectedMonth: Calendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    // Listener Database
    private var scheduleListener  : ListenerRegistration? = null
    private var attendanceListener: ListenerRegistration? = null
    private var izinListener      : ListenerRegistration? = null
    private var rekapAbsenListener: ListenerRegistration? = null
    private var rekapIzinListener : ListenerRegistration? = null

    // Variabel Gembok (Kunci Absen)
    private var isHoliday = false
    private var holidayName = "" // Tambahkan variabel untuk menyimpan nama hari libur
    private var hasCheckIn = false
    private var hasCheckOut = false
    private var checkInTimeMillis: Long = 0
    private var checkOutTimeMillis: Long = 0
    private var hasIzinMasuk = false
    private var hasIzinPulang = false

    // Variabel Tanggal
    private var todayDateStr = ""
    private var todayNameIndo = ""

    // Variabel Penghitung Rekap
    private var countHadir = 0
    private var countTerlambat = 0
    private var countIzin = 0

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        bindViews(view)
        setupClickListeners()
        loadUserName()
        return view
    }

    override fun onStart() {
        super.onStart()
        handler.post(clockRunnable)
        setupDateTime()

        // 🟢 LANGKAH 4: Simpan FCM Token ke Firestore saat Home dibuka
        saveFcmTokenToFirestore()

        startRealtimeSchedule()
        startRealtimeAttendanceToday()
        startRealtimeIzinToday()
        loadRekapBulan()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(clockRunnable)
        scheduleListener?.remove()
        attendanceListener?.remove()
        izinListener?.remove()
        rekapAbsenListener?.remove()
        rekapIzinListener?.remove()
    }

    // ── Bind Views ────────────────────────────────────────────────────────────

    private fun bindViews(view: View) {
        tvName            = view.findViewById(R.id.tvName)
        tvGreeting        = view.findViewById(R.id.tvGreeting)
        tvClock           = view.findViewById(R.id.tvClock)
        tvDate            = view.findViewById(R.id.tvDate)
        tvScheduleIn      = view.findViewById(R.id.tvScheduleIn)
        tvScheduleOut     = view.findViewById(R.id.tvScheduleOut)
        tvStatusIn        = view.findViewById(R.id.tvStatusIn)
        tvStatusOut       = view.findViewById(R.id.tvStatusOut)
        cardCheckIn       = view.findViewById(R.id.cardCheckIn)
        cardCheckOut      = view.findViewById(R.id.cardCheckOut)
        cardHadir         = view.findViewById(R.id.cardHadir)
        cardTerlambat     = view.findViewById(R.id.cardTerlambat)
        cardIzin          = view.findViewById(R.id.cardIzin)
        btnNotification   = view.findViewById(R.id.btnNotification)
        tvMonthYear       = view.findViewById(R.id.tvMonthYear)
        btnPrevMonth      = view.findViewById(R.id.btnPrevMonth)
        btnNextMonth      = view.findViewById(R.id.btnNextMonth)
        tvHadirCount      = view.findViewById(R.id.tvHadirCount)
        tvTerlambatCount  = view.findViewById(R.id.tvTerlambatCount)
        tvIzinCount       = view.findViewById(R.id.tvIzinCount)
        tvStatHadir       = view.findViewById(R.id.tvStatHadir)
        tvStatTerlambat   = view.findViewById(R.id.tvStatTerlambat)
        tvStatIzin        = view.findViewById(R.id.tvStatIzin)
        progressBar       = view.findViewById(R.id.progressKehadiran)
        tvProgressPct     = view.findViewById(R.id.tvProgressPercent)
        tvProgressDetail  = view.findViewById(R.id.tvProgressDetail)
    }

    private fun setupDateTime() {
        val calendar = Calendar.getInstance()
        val formatDb = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        todayDateStr = formatDb.format(calendar.time)

        val daysIndo = arrayOf("Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu")
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        todayNameIndo = daysIndo[dayOfWeek - 1]
    }

    // ── FCM Token ─────────────────────────────────────────────────────────────
    private fun saveFcmTokenToFirestore() {
        val uid = auth.currentUser?.uid ?: return

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM_TOKEN", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            // Dapatkan token baru
            val token = task.result

            // Simpan token ke dokumen user di Firestore
            db.collection("users").document(uid)
                .update("fcmToken", token)
                .addOnSuccessListener {
                    Log.d("FCM_TOKEN", "FCM Token berhasil disimpan: $token")
                }
                .addOnFailureListener { e ->
                    Log.e("FCM_TOKEN", "Gagal menyimpan FCM Token", e)
                }
        }
    }

    // ── Click Listeners ───────────────────────────────────────────────────────

    private fun setupClickListeners() {
        cardCheckIn.setOnClickListener {
            if (isHoliday) {
                val msg = if (holidayName.isNotEmpty()) "Hari libur: $holidayName, tidak bisa absen masuk." else "Hari libur, tidak bisa absen masuk."
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (hasIzinMasuk) {
                Toast.makeText(requireContext(), "Izin Anda sudah disetujui, tidak bisa absen.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (hasCheckIn) {
                Toast.makeText(requireContext(), "Anda sudah melakukan absen masuk hari ini.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            openFragment(AbsenFragment.newInstance("masuk"))
        }

        cardCheckOut.setOnClickListener {
            if (isHoliday) {
                val msg = if (holidayName.isNotEmpty()) "Hari libur: $holidayName, tidak bisa absen pulang." else "Hari libur, tidak bisa absen pulang."
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (hasIzinPulang) {
                Toast.makeText(requireContext(), "Izin Anda sudah disetujui, tidak bisa absen.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (hasCheckOut) {
                Toast.makeText(requireContext(), "Anda sudah melakukan absen pulang hari ini.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            openFragment(AbsenFragment.newInstance("pulang"))
        }

        btnNotification.setOnClickListener {
            openFragment(NotificationFragment.newInstance())
        }

        cardHadir.setOnClickListener     { openDetailRekap("hadir") }
        cardTerlambat.setOnClickListener { openDetailRekap("terlambat") }
        cardIzin.setOnClickListener      { openDetailRekap("izin")  }

        btnPrevMonth.setOnClickListener {
            selectedMonth.add(Calendar.MONTH, -1)
            refreshMonthUI()
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
            }
        }
    }

    private fun openFragment(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun openDetailRekap(status: String) {
        openFragment(DetailRekapFragment.newInstance(status, getMonthKey(selectedMonth), getMonthLabel(selectedMonth)))
    }

    private fun updateClockAndDate() {
        val now = Date()
        tvClock.text = SimpleDateFormat("HH : mm : ss", Locale("id", "ID")).format(now)
        tvDate.text  = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")).format(now)
        tvGreeting.text = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..10  -> "Selamat Pagi ☀️"
            in 11..14 -> "Selamat Siang 🌤"
            in 15..17 -> "Selamat Sore 🌇"
            else      -> "Selamat Malam 🌙"
        }
    }

    private fun loadUserName() {
        val uid = auth.currentUser?.uid ?: run { tvName.text = "User"; return }
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                tvName.text = if (doc.exists()) (doc.getString("name") ?: "User") else "User"
            }
            .addOnFailureListener { tvName.text = "User" }
    }

    // ── Logika Status Hari Ini (Jadwal, Izin, Absen) ──────────────────────────

    private fun startRealtimeIzinToday() {
        izinListener?.remove()
        val uid = auth.currentUser?.uid ?: return

        izinListener = db.collection("izin").document("${uid}_${todayDateStr}_izin")
            .addSnapshotListener { doc, _ ->
                hasIzinMasuk = false
                hasIzinPulang = false

                if (doc != null && doc.exists()) {
                    val status = doc.getString("approvalStatus") ?: ""
                    if (status == "approved") {
                        val absentType = doc.getString("absentType")?.lowercase() ?: "full"
                        if (absentType == "masuk") hasIzinMasuk = true
                        else if (absentType == "pulang") hasIzinPulang = true
                        else { hasIzinMasuk = true; hasIzinPulang = true }
                    }
                }
                updateStatusUI()
            }
    }

    private fun startRealtimeAttendanceToday() {
        attendanceListener?.remove()
        val uid = auth.currentUser?.uid ?: return

        attendanceListener = db.collection("attendance").document("${uid}_$todayDateStr")
            .addSnapshotListener { doc, _ ->
                hasCheckIn = false
                hasCheckOut = false

                if (doc != null && doc.exists()) {
                    val checkIn = doc.getLong("checkInTime")
                    val checkOut = doc.getLong("checkOutTime")

                    if (checkIn != null) { hasCheckIn = true; checkInTimeMillis = checkIn }
                    if (checkOut != null) { hasCheckOut = true; checkOutTimeMillis = checkOut }
                }
                updateStatusUI()
            }
    }

    private fun startRealtimeSchedule() {
        scheduleListener?.remove()
        val todayDateDash = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        scheduleListener = db.collection("schedule")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                var todayScheduleMap: Map<String, Any>? = null

                // Prioritas 1: Tanggal Spesifik
                for (doc in snapshot.documents) {
                    if (doc.getString("scheduleMode") == "specific" && doc.getString("specificDate") == todayDateDash) {
                        todayScheduleMap = doc.data
                        break
                    }
                }

                // Prioritas 2: Mingguan Berulang
                if (todayScheduleMap == null) {
                    for (doc in snapshot.documents) {
                        if (doc.getString("scheduleMode") == "weekly") {
                            val workDays = doc.get("workDays") as? List<String> ?: emptyList()
                            if (workDays.contains(todayNameIndo)) {
                                todayScheduleMap = doc.data
                                break
                            }
                        }
                    }
                }

                if (todayScheduleMap == null) {
                    isHoliday = true
                    holidayName = ""
                    tvScheduleIn.text = "LIBUR"
                    tvScheduleOut.text = "LIBUR"
                } else {
                    val isHolidayDb = todayScheduleMap["isHoliday"] as? Boolean ?: false
                    isHoliday = isHolidayDb

                    if (isHolidayDb) {
                        holidayName = todayScheduleMap["name"] as? String ?: ""
                        tvScheduleIn.text = "LIBUR"
                        tvScheduleOut.text = "LIBUR"
                    } else {
                        holidayName = ""
                        tvScheduleIn.text = todayScheduleMap["checkInTime"]?.toString() ?: "08:00"
                        tvScheduleOut.text = todayScheduleMap["checkOutTime"]?.toString() ?: "17:00"
                    }
                }
                updateStatusUI()
            }
    }

    private fun updateStatusUI() {
        if (isHoliday) {
            val statusText = if (holidayName.isNotEmpty()) holidayName else "Libur Akhir Pekan / Nasional"
            tvStatusIn.text = statusText
            tvStatusIn.setTextColor(Color.parseColor("#94A3B8"))
            tvStatusOut.text = statusText
            tvStatusOut.setTextColor(Color.parseColor("#94A3B8"))
            return
        }

        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

        // UI Absen Masuk
        if (hasIzinMasuk) {
            tvStatusIn.text = "Izin Disetujui"
            tvStatusIn.setTextColor(Color.parseColor("#3B82F6")) // Biru
        } else if (hasCheckIn) {
            tvStatusIn.text = "Sudah ✓ ${timeFmt.format(Date(checkInTimeMillis))}"
            tvStatusIn.setTextColor(Color.parseColor("#10B981")) // Hijau
        } else {
            tvStatusIn.text = "Belum Absen"
            tvStatusIn.setTextColor(Color.parseColor("#6C63FF")) // Ungu
        }

        // UI Absen Pulang
        if (hasIzinPulang) {
            tvStatusOut.text = "Izin Disetujui"
            tvStatusOut.setTextColor(Color.parseColor("#3B82F6")) // Biru
        } else if (hasCheckOut) {
            tvStatusOut.text = "Sudah ✓ ${timeFmt.format(Date(checkOutTimeMillis))}"
            tvStatusOut.setTextColor(Color.parseColor("#10B981")) // Hijau
        } else {
            tvStatusOut.text = "Belum Absen"
            tvStatusOut.setTextColor(Color.parseColor("#10B981")) // Hijau bawaan XML
        }
    }

    // ── Logika Rekap Bulan Ini ────────────────────────────────────────────────

    private fun refreshMonthUI() {
        tvMonthYear.text = getMonthLabel(selectedMonth)
        loadRekapBulan()
    }

    private fun loadRekapBulan() {
        tvMonthYear.text = getMonthLabel(selectedMonth)
        val uid = auth.currentUser?.uid ?: return

        val start = (selectedMonth.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        val end = (selectedMonth.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }
        val monthKeyStr = getMonthKey(selectedMonth)

        rekapAbsenListener?.remove()
        rekapIzinListener?.remove()

        rekapAbsenListener = db.collection("attendance")
            .whereEqualTo("uid", uid)
            .whereGreaterThanOrEqualTo("checkInTime", start.timeInMillis)
            .whereLessThanOrEqualTo("checkInTime", end.timeInMillis)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                countHadir = 0
                countTerlambat = 0

                for (doc in snapshot.documents) {
                    val status = doc.getString("status")?.lowercase()
                    if (status == "terlambat") {
                        countTerlambat++
                    } else {
                        countHadir++
                    }
                }
                updateRekapUI()
            }

        rekapIzinListener = db.collection("izin")
            .whereEqualTo("monthKey", monthKeyStr)
            .whereEqualTo("approvalStatus", "approved")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                countIzin = 0
                for (doc in snapshot.documents) {
                    if (doc.id.startsWith(uid)) {
                        countIzin++
                    }
                }
                updateRekapUI()
            }
    }

    private fun updateRekapUI() {
        val total = countHadir + countIzin + countTerlambat
        val pct = if (total > 0) ((countHadir + countTerlambat) * 100 / total) else 0

        tvHadirCount.text     = "$countHadir Hari"
        tvTerlambatCount.text = "$countTerlambat Hari"
        tvIzinCount.text      = "$countIzin Hari"

        tvStatHadir.text      = "$countHadir"
        tvStatTerlambat.text  = "$countTerlambat"
        tvStatIzin.text       = "$countIzin"

        progressBar.progress  = pct
        tvProgressPct.text    = "$pct%"
        tvProgressDetail.text = "${countHadir + countTerlambat} dari $total hari tercatat"
    }

    private fun getMonthKey(cal: Calendar)   = SimpleDateFormat("yyyyMM", Locale.getDefault()).format(cal.time)
    private fun getMonthLabel(cal: Calendar) = SimpleDateFormat("MMM yyyy", Locale("id", "ID")).format(cal.time)
}