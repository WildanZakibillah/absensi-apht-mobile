package com.absenku.dashboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.absenku.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AbsenFragment : Fragment() {

    companion object {
        private const val ARG_TYPE = "type"

        // Tipe Izin
        const val IZIN_SAKIT         = "Sakit"
        const val IZIN_KEPERLUAN     = "Keperluan Pribadi"
        const val IZIN_DINAS         = "Dinas Luar"
        const val IZIN_CUTI          = "Cuti"
        const val IZIN_LAINNYA       = "Lainnya"

        fun newInstance(type: String) = AbsenFragment().apply {
            arguments = Bundle().apply { putString(ARG_TYPE, type) }
        }
    }

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db   by lazy { FirebaseFirestore.getInstance() }

    // ════════════════════════════════════════════════════════════════════════
    //  VARIABEL KONFIGURASI LOKASI (Dari config/app_settings)
    // ════════════════════════════════════════════════════════════════════════
    private var officeLat: Double    = -7.0521013
    private var officeLng: Double    = 113.6630085
    private var radiusMeter: Double  = 100.0

    // ════════════════════════════════════════════════════════════════════════
    //  VARIABEL JADWAL DINAMIS (Dari schedule)
    // ════════════════════════════════════════════════════════════════════════
    private var isHoliday            = false
    private var scheduleLoaded       = false
    private var checkInTimeStr       = "08:00"
    private var checkOutTimeStr      = "17:00"
    private var toleranceMinutes     = 15

    private var todayDateStr         = ""
    private var todayNameIndo        = ""

    // ── GPS ───────────────────────────────────────────────────────────────
    private lateinit var fusedClient : FusedLocationProviderClient
    private var locationCallback     : LocationCallback? = null
    private var currentLocation      : Location? = null
    private var isInsideRadius       = false

    // ── State ─────────────────────────────────────────────────────────────
    private val absenType      by lazy { arguments?.getString(ARG_TYPE) ?: "masuk" }
    private var capturedBitmap : Bitmap? = null
    private var savedPhotoPath : String? = null

    // ── Mode: normal atau izin ─────────────────────────────────────────────
    private var isIzinMode          = false
    private var selectedIzinType    = ""

    // ── Views ─────────────────────────────────────────────────────────────
    private lateinit var btnBack              : MaterialCardView
    private lateinit var tvHeaderEmoji        : TextView
    private lateinit var tvHeaderTitle        : TextView
    private lateinit var tvHeaderSub          : TextView
    private lateinit var chipStatus           : MaterialCardView
    private lateinit var tvChipStatus         : TextView
    private lateinit var tvCurrentTime        : TextView
    private lateinit var tvCurrentDate        : TextView
    private lateinit var cardGpsStatus        : MaterialCardView
    private lateinit var tvGpsStatus          : TextView
    private lateinit var tvLocationName       : TextView
    private lateinit var tvLocationCoord      : TextView
    private lateinit var tvDistance           : TextView
    private lateinit var progressDistance     : ProgressBar
    private lateinit var cardPhotoPreview     : MaterialCardView
    private lateinit var layoutPlaceholder    : LinearLayout
    private lateinit var ivPhotoPreview       : ImageView
    private lateinit var btnRetakePhoto       : MaterialCardView
    private lateinit var etNotes              : TextInputEditText
    private lateinit var btnAbsen             : MaterialButton
    private lateinit var layoutLoading        : LinearLayout

    // Views khusus izin
    private lateinit var cardIzinMode         : MaterialCardView
    private lateinit var layoutIzinDetails    : LinearLayout
    private lateinit var rgIzinType           : RadioGroup
    private lateinit var rbSakit              : RadioButton
    private lateinit var rbKeperluan          : RadioButton
    private lateinit var rbDinas              : RadioButton
    private lateinit var rbCuti               : RadioButton
    private lateinit var rbLainnya            : RadioButton
    private lateinit var tilIzinAlasan        : TextInputLayout
    private lateinit var etIzinAlasan         : TextInputEditText
    private lateinit var tvIzinBadge          : TextView
    private lateinit var tvLocationRadiusInfo : TextView

    // ── Clock ─────────────────────────────────────────────────────────────
    private val handler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            updateStatusChip()
            handler.postDelayed(this, 1000)
        }
    }

    // ── Launchers (Diperbarui untuk Kamera Depan) ─────────────────────────
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            if (bitmap != null) {
                capturedBitmap               = bitmap
                savedPhotoPath               = savePhotoToLocal(bitmap)
                ivPhotoPreview.setImageBitmap(bitmap)
                ivPhotoPreview.visibility    = View.VISIBLE
                layoutPlaceholder.visibility = View.GONE
                btnRetakePhoto.visibility    = View.VISIBLE
            }
        }
    }

    private val locationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
                || perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) startLocationUpdates()
        else showGpsError("Izin lokasi ditolak")
    }

    private val cameraPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openCamera() // Langsung jalankan openCamera jika diizinkan
        else Toast.makeText(requireContext(), "Izin kamera ditolak", Toast.LENGTH_SHORT).show()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_absen, container, false)
        fusedClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        bindViews(view)

        setupDateTime()
        setupClickListeners()
        setupIzinUI()

        // Panggil 2 konfigurasi secara bersamaan
        fetchAppLocationConfig()
        fetchTodaySchedule()

        return view
    }

    override fun onStart() {
        super.onStart()
        handler.post(clockRunnable)
        checkAndRequestLocationPermission()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(clockRunnable)
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
    }

    private fun setupDateTime() {
        val calendar = Calendar.getInstance()
        val formatDb = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        todayDateStr = formatDb.format(calendar.time)

        val daysIndo = arrayOf("Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu")
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        todayNameIndo = daysIndo[dayOfWeek - 1]
    }

    // ── Fetch Pengaturan Geofencing dari config ───────────────────────────
    private fun fetchAppLocationConfig() {
        db.collection("config").document("app_settings").get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    officeLat    = doc.getDouble("officeLat") ?: officeLat
                    officeLng    = doc.getDouble("officeLng") ?: officeLng
                    radiusMeter  = doc.getDouble("radiusMeter") ?: doc.getLong("radiusMeter")?.toDouble() ?: radiusMeter

                    if (!isIzinMode) {
                        tvLocationRadiusInfo.text = "Radius absen: ${radiusMeter.toInt()} meter dari kantor"
                    }
                    currentLocation?.let { onLocationReceived(it) }
                }
            }
    }

    // ── Fetch Pengaturan Jam & Jadwal dari schedule ───────────────────────
    private fun fetchTodaySchedule() {
        setLoading(true)
        db.collection("schedule").get()
            .addOnSuccessListener { snapshot ->
                var todayScheduleMap: Map<String, Any>? = null

                // 1. Cek Jadwal Spesifik
                for (doc in snapshot.documents) {
                    if (doc.getString("scheduleMode") == "specific" && doc.getString("specificDate") == todayDateStr) {
                        todayScheduleMap = doc.data
                        break
                    }
                }

                // 2. Cek Jadwal Mingguan
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
                    checkInTimeStr = "--:--"
                    checkOutTimeStr = "--:--"

                    showDialog("Hari Libur", "Hari ini adalah hari libur atau jadwal tidak tersedia.\nKamu tidak perlu melakukan absensi.")
                } else {
                    isHoliday = false
                    checkInTimeStr = todayScheduleMap["checkInTime"]?.toString() ?: "08:00"
                    checkOutTimeStr = todayScheduleMap["checkOutTime"]?.toString() ?: "17:00"
                    toleranceMinutes = (todayScheduleMap["toleranceMinutes"] as? Number)?.toInt() ?: 15
                }

                scheduleLoaded = true
                setupHeaderUI()
                updateStatusChip()
                setLoading(false)
            }
            .addOnFailureListener { e ->
                scheduleLoaded = true
                setLoading(false)
                Toast.makeText(requireContext(), "Gagal memuat jadwal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ── Bind Views ────────────────────────────────────────────────────────
    private fun bindViews(v: View) {
        btnBack              = v.findViewById(R.id.btnBack)
        tvHeaderEmoji        = v.findViewById(R.id.tvHeaderEmoji)
        tvHeaderTitle        = v.findViewById(R.id.tvHeaderTitle)
        tvHeaderSub          = v.findViewById(R.id.tvHeaderSub)
        chipStatus           = v.findViewById(R.id.chipStatus)
        tvChipStatus         = v.findViewById(R.id.tvChipStatus)
        tvCurrentTime        = v.findViewById(R.id.tvCurrentTime)
        tvCurrentDate        = v.findViewById(R.id.tvCurrentDate)
        cardGpsStatus        = v.findViewById(R.id.cardGpsStatus)
        tvGpsStatus          = v.findViewById(R.id.tvGpsStatus)
        tvLocationName       = v.findViewById(R.id.tvLocationName)
        tvLocationCoord      = v.findViewById(R.id.tvLocationCoord)
        tvDistance           = v.findViewById(R.id.tvDistance)
        progressDistance     = v.findViewById(R.id.progressDistance)
        cardPhotoPreview     = v.findViewById(R.id.cardPhotoPreview)
        layoutPlaceholder    = v.findViewById(R.id.layoutPhotoPlaceholder)
        ivPhotoPreview       = v.findViewById(R.id.ivPhotoPreview)
        btnRetakePhoto       = v.findViewById(R.id.btnRetakePhoto)
        etNotes              = v.findViewById(R.id.etNotes)
        btnAbsen             = v.findViewById(R.id.btnAbsen)
        layoutLoading        = v.findViewById(R.id.layoutLoading)

        cardIzinMode         = v.findViewById(R.id.cardIzinMode)
        layoutIzinDetails    = v.findViewById(R.id.layoutIzinDetails)
        rgIzinType           = v.findViewById(R.id.rgIzinType)
        rbSakit              = v.findViewById(R.id.rbSakit)
        rbKeperluan          = v.findViewById(R.id.rbKeperluan)
        rbDinas              = v.findViewById(R.id.rbDinas)
        rbCuti               = v.findViewById(R.id.rbCuti)
        rbLainnya            = v.findViewById(R.id.rbLainnya)
        tilIzinAlasan        = v.findViewById(R.id.tilIzinAlasan)
        etIzinAlasan         = v.findViewById(R.id.etIzinAlasan)
        tvIzinBadge          = v.findViewById(R.id.tvIzinBadge)
        tvLocationRadiusInfo = v.findViewById(R.id.tvRadiusInfo)
    }

    // ── Header UI ─────────────────────────────────────────────────────────
    private fun setupHeaderUI() {
        if (absenType == "masuk") {
            tvHeaderEmoji.text = "📍"
            tvHeaderTitle.text = "Absen Masuk"
            tvHeaderSub.text   = if(isHoliday) "Hari Libur" else "Jadwal Masuk: $checkInTimeStr WIB"
            btnAbsen.text      = "Absen Masuk Sekarang"
        } else {
            tvHeaderEmoji.text = "🏠"
            tvHeaderTitle.text = "Absen Pulang"
            tvHeaderSub.text   = if(isHoliday) "Hari Libur" else "Jadwal Pulang: $checkOutTimeStr WIB"
            btnAbsen.text      = "Absen Pulang Sekarang"
        }
    }

    // ── Setup Izin UI ─────────────────────────────────────────────────────
    private fun setupIzinUI() {
        layoutIzinDetails.visibility = View.GONE
        tvIzinBadge.visibility       = View.GONE

        rgIzinType.setOnCheckedChangeListener { _, checkedId ->
            selectedIzinType = when (checkedId) {
                R.id.rbSakit    -> IZIN_SAKIT
                R.id.rbKeperluan-> IZIN_KEPERLUAN
                R.id.rbDinas    -> IZIN_DINAS
                R.id.rbCuti     -> IZIN_CUTI
                R.id.rbLainnya  -> IZIN_LAINNYA
                else            -> ""
            }
            tilIzinAlasan.hint = if (checkedId == R.id.rbLainnya)
                "Jelaskan alasan izin (wajib)..."
            else
                "Alasan izin tambahan (opsional)..."
        }
    }

    // ── Click Listeners ───────────────────────────────────────────────────
    private fun setupClickListeners() {
        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        cardPhotoPreview.setOnClickListener { openCamera() }
        btnRetakePhoto.setOnClickListener   { openCamera() }
        btnAbsen.setOnClickListener         { validateAndSubmit() }

        cardIzinMode.setOnClickListener {
            toggleIzinMode()
        }
    }

    // ── Toggle Mode Izin ──────────────────────────────────────────────────
    private fun toggleIzinMode() {
        isIzinMode = !isIzinMode

        if (isIzinMode) {
            layoutIzinDetails.visibility = View.VISIBLE
            tvIzinBadge.visibility       = View.VISIBLE
            cardIzinMode.setCardBackgroundColor(Color.parseColor("#FFF3CD"))

            tvHeaderEmoji.text = "📋"
            val titleSuffix = if (absenType == "masuk") "Masuk" else "Pulang"
            tvHeaderTitle.text = "Izin Absen $titleSuffix"

            btnAbsen.text = "Kirim Izin Sekarang"
            tvLocationRadiusInfo.text = "Mode izin aktif — lokasi tetap dicatat otomatis"
            tvIzinBadge.text = "✓ Mode Izin Aktif"

            showInfoSnackbar("Mode Izin aktif. Lokasi kamu tetap dicatat namun radius diabaikan.")
        } else {
            layoutIzinDetails.visibility = View.GONE
            tvIzinBadge.visibility       = View.GONE
            cardIzinMode.setCardBackgroundColor(Color.parseColor("#FFFFFF"))

            selectedIzinType = ""
            rgIzinType.clearCheck()
            etIzinAlasan.setText("")

            setupHeaderUI()
            tvLocationRadiusInfo.text = "Radius absen: ${radiusMeter.toInt()} meter dari kantor"

            val titleSuffix = if (absenType == "masuk") "Masuk" else "Pulang"
            btnAbsen.text = "Absen $titleSuffix Sekarang"
        }

        currentLocation?.let { onLocationReceived(it) }
    }

    // ── Clock & Chip ──────────────────────────────────────────────────────
    private fun updateClock() {
        val now = Date()
        tvCurrentTime.text = SimpleDateFormat("HH : mm : ss", Locale("id", "ID")).format(now)
        tvCurrentDate.text = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")).format(now)
    }

    private fun timeToMinutes(timeStr: String): Int {
        val parts = timeStr.split(":")
        if(parts.size != 2) return 0
        return (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
    }

    private fun updateStatusChip() {
        if (!scheduleLoaded) return

        if (isIzinMode) {
            setChip("📋 Mode Izin", "#F59E0B", "#FFF8E7", enabled = true)
            return
        }

        if (isHoliday) {
            setChip("🏖️ Hari Libur", "#EF4444", "#FFF0F0", enabled = false)
            return
        }

        val cal = Calendar.getInstance()
        val nowMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        val inMins = timeToMinutes(checkInTimeStr)
        val outMins = timeToMinutes(checkOutTimeStr)
        val lateMins = inMins + toleranceMinutes

        if (absenType == "masuk") {
            when {
                nowMins < inMins -> setChip("⏰ Belum Waktunya", "#F59E0B", "#FFF8E7", enabled = false)
                nowMins <= lateMins -> setChip("✓ Tepat Waktu",     "#4ECCA3", "#E8FBF4", enabled = true)
                else -> setChip("⚠ Terlambat",       "#EF4444", "#FFF0F0", enabled = true)
            }
        } else {
            if (nowMins < outMins) setChip("⏰ Belum Waktunya", "#F59E0B", "#FFF8E7", enabled = false)
            else setChip("✓ Bisa Pulang",     "#4ECCA3", "#E8FBF4", enabled = true)
        }
    }

    private fun setChip(label: String, txtHex: String, bgHex: String, enabled: Boolean) {
        tvChipStatus.text = label
        tvChipStatus.setTextColor(Color.parseColor(txtHex))
        chipStatus.setCardBackgroundColor(Color.parseColor(bgHex))
        if (layoutLoading.visibility == View.GONE) {
            btnAbsen.isEnabled = enabled
            btnAbsen.alpha     = if (enabled) 1f else 0.55f
        }
    }

    // ── GPS & Lokasi ──────────────────────────────────────────────────────
    private fun checkAndRequestLocationPermission() {
        val fine   = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED)
            startLocationUpdates()
        else
            locationPermLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
    }

    private fun startLocationUpdates() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { onLocationReceived(it) }
            }
        }

        try {
            fusedClient.requestLocationUpdates(req, locationCallback!!, Looper.getMainLooper())
            fusedClient.lastLocation.addOnSuccessListener { loc -> loc?.let { onLocationReceived(it) } }
        } catch (e: SecurityException) {
            showGpsError("Gagal mengakses GPS")
        }
    }

    private fun onLocationReceived(location: Location) {
        currentLocation = location
        val lat = location.latitude
        val lng = location.longitude

        tvLocationCoord.text = "Lat: ${fmtCoord(lat)},  Lng: ${fmtCoord(lng)}"

        val office = Location("office").apply {
            latitude  = officeLat
            longitude = officeLng
        }
        val distanceM  = location.distanceTo(office)
        isInsideRadius = distanceM <= radiusMeter

        tvDistance.text = "Jarak ke kantor: ${distanceM.toInt()}m  (radius ${radiusMeter.toInt()}m)"

        val progress = if (distanceM < radiusMeter) 100
        else ((radiusMeter / distanceM) * 100).toInt().coerceIn(0, 100)
        progressDistance.progress = progress

        if (isIzinMode) {
            tvGpsStatus.text = "📍 Lokasi Terdeteksi"
            tvGpsStatus.setTextColor(Color.parseColor("#6C63FF"))
            cardGpsStatus.setCardBackgroundColor(Color.parseColor("#EEF0FF"))
            progressDistance.progressTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor("#6C63FF"))
        } else {
            if (isInsideRadius) {
                tvGpsStatus.text = "✓ Dalam Radius"
                tvGpsStatus.setTextColor(Color.parseColor("#4ECCA3"))
                cardGpsStatus.setCardBackgroundColor(Color.parseColor("#E8FBF4"))
                progressDistance.progressTintList =
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#4ECCA3"))
            } else {
                tvGpsStatus.text = "✗ Di Luar Radius"
                tvGpsStatus.setTextColor(Color.parseColor("#EF4444"))
                cardGpsStatus.setCardBackgroundColor(Color.parseColor("#FFF0F0"))
                progressDistance.progressTintList =
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#EF4444"))
            }
        }

        try {
            @Suppress("DEPRECATION")
            val addresses = Geocoder(requireContext(), Locale("id", "ID"))
                .getFromLocation(lat, lng, 1)
            val addr = addresses?.firstOrNull()
            tvLocationName.text = listOfNotNull(
                addr?.thoroughfare,
                addr?.subLocality,
                addr?.locality
            ).joinToString(", ").ifEmpty { "Lokasi berhasil didapat" }
        } catch (_: Exception) {
            tvLocationName.text = "Lokasi berhasil didapat"
        }
    }

    private fun showGpsError(msg: String) {
        tvGpsStatus.text = msg
        tvGpsStatus.setTextColor(Color.parseColor("#EF4444"))
        cardGpsStatus.setCardBackgroundColor(Color.parseColor("#FFF0F0"))
        tvLocationName.text = "Gagal mendapatkan lokasi"
    }

    // ── Kamera (Diperbarui untuk Kamera Depan) ────────────────────────────
    private fun openCamera() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            // Membuat Intent manual untuk memanggil kamera
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                // Kumpulan 'Extras' untuk memaksa OS membuka kamera depan (Selfie)
                putExtra("android.intent.extras.CAMERA_FACING", 1)
                putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
                putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
            }
            cameraLauncher.launch(intent)
        } else {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun savePhotoToLocal(bitmap: Bitmap): String? {
        return try {
            val uid      = auth.currentUser?.uid ?: return null
            val today    = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            val suffix   = if (isIzinMode) "izin" else absenType
            val fileName = "${uid}_${today}_${suffix}.jpg"
            val dir      = File(requireContext().filesDir, "absen_photos").also { it.mkdirs() }
            val file     = File(dir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    // ── Validasi & Submit ─────────────────────────────────────────────────
    private fun validateAndSubmit() {
        if (isHoliday && !isIzinMode) {
            showDialog("Hari Libur", "Hari ini adalah hari libur. Tidak dapat melakukan absen.")
            return
        }

        if (isIzinMode) {
            validateAndSubmitIzin()
        } else {
            validateAndSubmitAbsen()
        }
    }

    private fun validateAndSubmitAbsen() {
        if (currentLocation == null) {
            showDialog("Lokasi Belum Tersedia",
                "Tunggu sebentar hingga GPS terdeteksi.\nPastikan GPS aktif di pengaturan HP.")
            return
        }
        if (!isInsideRadius) {
            val office = Location("office").apply {
                latitude = officeLat; longitude = officeLng
            }
            val dist = currentLocation!!.distanceTo(office).toInt()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Di Luar Radius Absen")
                .setMessage(
                    "Kamu berada ${dist}m dari kantor.\n" +
                            "Absen hanya bisa dalam radius ${radiusMeter.toInt()}m.\n\n" +
                            "Apakah kamu ingin mengajukan Izin sebagai gantinya?"
                )
                .setNegativeButton("Tutup", null)
                .setPositiveButton("Ajukan Izin") { _, _ ->
                    if (!isIzinMode) toggleIzinMode()
                }
                .show()
            return
        }
        checkDuplicateAbsen {
            val label = if (absenType == "masuk") "Absen Masuk" else "Absen Pulang"
            val time  = SimpleDateFormat("HH:mm", Locale("id", "ID")).format(Date())
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Konfirmasi $label")
                .setMessage(
                    "Waktu   : $time\n" +
                            "Lokasi  : ${tvLocationName.text}\n\n" +
                            "Lanjutkan $label?"
                )
                .setNegativeButton("Batal", null)
                .setPositiveButton("Ya, Absen!") { _, _ -> doSubmitAbsen() }
                .show()
        }
    }

    private fun validateAndSubmitIzin() {
        if (selectedIzinType.isEmpty()) {
            showDialog("Pilih Tipe Izin", "Silakan pilih jenis izin terlebih dahulu.")
            return
        }

        val alasan = etIzinAlasan.text?.toString()?.trim() ?: ""
        if (selectedIzinType == IZIN_LAINNYA && alasan.isEmpty()) {
            showDialog("Alasan Diperlukan",
                "Untuk izin jenis 'Lainnya', mohon tuliskan alasan kamu.")
            return
        }

        if (currentLocation == null) {
            showDialog("Lokasi Belum Tersedia",
                "GPS belum terdeteksi.\nMohon pastikan GPS aktif agar lokasi kamu bisa dicatat.")
            return
        }

        if (capturedBitmap == null) {
            showDialog("Foto Diperlukan",
                "Mohon ambil foto selfie sebagai bukti pengajuan izin.")
            return
        }

        checkDuplicateIzin {
            val time = SimpleDateFormat("HH:mm", Locale("id", "ID")).format(Date())
            val notesText = etNotes.text?.toString()?.trim() ?: ""
            val alasanDisplay = if (alasan.isNotEmpty()) "\nAlasan  : $alasan" else ""
            val catatanDisplay = if (notesText.isNotEmpty()) "\nCatatan : $notesText" else ""

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Konfirmasi Pengajuan Izin")
                .setMessage(
                    "Jenis Izin : $selectedIzinType$alasanDisplay\n" +
                            "Waktu      : $time\n" +
                            "Lokasi     : ${tvLocationName.text}$catatanDisplay\n\n" +
                            "Izin ini akan diteruskan ke admin untuk disetujui."
                )
                .setNegativeButton("Batal", null)
                .setPositiveButton("Kirim Izin") { _, _ -> doSubmitIzin() }
                .show()
        }
    }

    private fun checkDuplicateAbsen(onNotDuplicate: () -> Unit) {
        val uid   = auth.currentUser?.uid ?: return
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val docId = "${uid}_$today"
        val field = if (absenType == "masuk") "checkInTime" else "checkOutTime"

        setLoading(true)
        db.collection("attendance").document(docId).get()
            .addOnSuccessListener { doc ->
                setLoading(false)
                val existing = doc.getLong(field)
                if (doc.exists() && existing != null) {
                    val t = SimpleDateFormat("HH:mm", Locale("id", "ID")).format(Date(existing))
                    val ket = if (absenType == "masuk") "absen masuk" else "absen pulang"
                    showDialog("Sudah Absen", "Kamu sudah $ket hari ini pukul $t.")
                } else {
                    onNotDuplicate()
                }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                showDialog("Koneksi Error", "Gagal memeriksa data:\n${e.message}")
            }
    }

    private fun checkDuplicateIzin(onNotDuplicate: () -> Unit) {
        val uid   = auth.currentUser?.uid ?: return
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val docId = "${uid}_${today}_izin"

        setLoading(true)
        db.collection("izin").document(docId).get()
            .addOnSuccessListener { doc ->
                setLoading(false)
                if (doc.exists()) {
                    showDialog("Sudah Mengajukan Izin",
                        "Kamu sudah mengajukan izin hari ini.\n" +
                                "Silakan hubungi admin jika ingin membatalkan.")
                } else {
                    onNotDuplicate()
                }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                showDialog("Koneksi Error", "Gagal memeriksa data:\n${e.message}")
            }
    }

    private fun doSubmitAbsen() {
        val uid      = auth.currentUser?.uid ?: return
        val today    = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val monthKey = SimpleDateFormat("yyyyMM",   Locale.getDefault()).format(Date())
        val docId    = "${uid}_$today"
        val now      = System.currentTimeMillis()

        // Logika Status Terlambat Menggunakan Jadwal Dinamis
        val cal = Calendar.getInstance()
        val nowMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val inMins = timeToMinutes(checkInTimeStr)
        val lateMins = inMins + toleranceMinutes
        val isLate = absenType == "masuk" && nowMins > lateMins

        val notes    = etNotes.text?.toString()?.trim() ?: ""
        val lat      = currentLocation!!.latitude
        val lng      = currentLocation!!.longitude

        val data = mutableMapOf<String, Any>(
            "uid"      to uid,
            "status"   to if (isLate) "terlambat" else "hadir", // Status Disimpan Otomatis
            "date"     to today,
            "monthKey" to monthKey
        )

        if (absenType == "masuk") {
            data["checkInTime"] = now
            data["checkInLat"]  = lat
            data["checkInLng"]  = lng
            data["isLate"]      = isLate
            if (notes.isNotEmpty())       data["checkInNotes"]     = notes
            savedPhotoPath?.let { path -> data["checkInPhotoPath"] = path }
        } else {
            data["checkOutTime"] = now
            data["checkOutLat"]  = lat
            data["checkOutLng"]  = lng
            if (notes.isNotEmpty())       data["checkOutNotes"]     = notes
            savedPhotoPath?.let { path -> data["checkOutPhotoPath"] = path }
        }

        setLoading(true)
        btnAbsen.isEnabled = false

        db.collection("attendance").document(docId)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                setLoading(false)
                showSuccess(isLate)
            }
            .addOnFailureListener { e ->
                setLoading(false)
                btnAbsen.isEnabled = true
                showDialog("Gagal Menyimpan", "Terjadi kesalahan:\n${e.message}")
            }
    }

    private fun doSubmitIzin() {
        val uid      = auth.currentUser?.uid ?: return
        val today    = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val monthKey = SimpleDateFormat("yyyyMM",   Locale.getDefault()).format(Date())
        val docId    = "${uid}_${today}_izin"
        val now      = System.currentTimeMillis()
        val notes    = etNotes.text?.toString()?.trim() ?: ""
        val alasan   = etIzinAlasan.text?.toString()?.trim() ?: ""
        val lat      = currentLocation!!.latitude
        val lng      = currentLocation!!.longitude

        val data = mutableMapOf<String, Any>(
            "uid"           to uid,
            "date"          to today,
            "monthKey"      to monthKey,
            "status"        to "izin",
            "izinType"      to selectedIzinType,
            "absenType"     to absenType,
            "submitTime"    to now,
            "lat"           to lat,
            "lng"           to lng,
            "approvalStatus" to "pending"
        )

        if (alasan.isNotEmpty())        data["alasan"]    = alasan
        if (notes.isNotEmpty())         data["notes"]     = notes
        savedPhotoPath?.let { path ->   data["photoPath"] = path }

        setLoading(true)
        btnAbsen.isEnabled = false

        db.collection("izin").document(docId)
            .set(data)
            .addOnSuccessListener {
                val attendData = mapOf(
                    "uid"      to uid,
                    "status"   to "izin",
                    "date"     to today,
                    "monthKey" to monthKey,
                    "izinType" to selectedIzinType,
                    "izinDocId" to docId
                )
                db.collection("attendance").document("${uid}_$today")
                    .set(attendData, SetOptions.merge())
                    .addOnCompleteListener {
                        setLoading(false)
                        showIzinSuccess()
                    }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                btnAbsen.isEnabled = true
                showDialog("Gagal Menyimpan", "Terjadi kesalahan:\n${e.message}")
            }
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private fun setLoading(show: Boolean) {
        layoutLoading.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) {
            updateStatusChip()
        } else {
            btnAbsen.isEnabled = false
        }
    }

    private fun showSuccess(isLate: Boolean) {
        val label   = if (absenType == "masuk") "Absen Masuk" else "Absen Pulang"
        val timeNow = SimpleDateFormat("HH:mm", Locale("id", "ID")).format(Date())
        val extra   = if (isLate) "\n\n⚠️ Kamu tercatat terlambat hari ini." else ""
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("✅ $label Berhasil!")
            .setMessage("$label tercatat pada pukul $timeNow.$extra")
            .setPositiveButton("OK") { _, _ -> parentFragmentManager.popBackStack() }
            .setCancelable(false)
            .show()
    }

    private fun showIzinSuccess() {
        val timeNow = SimpleDateFormat("HH:mm", Locale("id", "ID")).format(Date())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📋 Izin Terkirim!")
            .setMessage(
                "Izin kamu ($selectedIzinType) telah dikirim pada pukul $timeNow.\n\n" +
                        "Lokasi kamu: ${tvLocationName.text}\n\n" +
                        "Menunggu persetujuan admin. Kamu akan mendapat notifikasi setelah diproses."
            )
            .setPositiveButton("OK") { _, _ -> parentFragmentManager.popBackStack() }
            .setCancelable(false)
            .show()
    }

    private fun showDialog(title: String, msg: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showInfoSnackbar(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    }

    private fun pad(n: Int)           = n.toString().padStart(2, '0')
    private fun fmtCoord(d: Double)   = String.format("%.6f", d)
}