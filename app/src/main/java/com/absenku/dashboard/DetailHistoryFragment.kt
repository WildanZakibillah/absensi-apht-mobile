package com.absenku.dashboard

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.absenku.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.*

class DetailHistoryFragment : Fragment() {

    companion object {
        fun newInstance(item: HistoryItem): DetailHistoryFragment {
            val fragment = DetailHistoryFragment()
            val args = Bundle().apply {
                putLong("checkInTime", item.checkInTime)
                putLong("checkOutTime", item.checkOutTime ?: 0L)
                putString("status", item.status)
                putBoolean("isLate", item.isLate)
                putString("checkInNotes", item.checkInNotes)
                putString("checkOutNotes", item.checkOutNotes)
                putDouble("lat", item.checkInLat ?: 0.0)
                putDouble("lng", item.checkInLng ?: 0.0)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_detail_history, container, false)

        view.findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        arguments?.let { args ->
            val checkInMs = args.getLong("checkInTime")
            val checkOutMs = args.getLong("checkOutTime")
            val lat = args.getDouble("lat")
            val lng = args.getDouble("lng")

            val date = Date(checkInMs)
            val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

            view.findViewById<TextView>(R.id.tvDetailDayName).text = SimpleDateFormat("EEEE", Locale("id", "ID")).format(date)
            view.findViewById<TextView>(R.id.tvDetailFullDate).text = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(date)
            view.findViewById<TextView>(R.id.tvDetailCheckIn).text = timeFmt.format(date)

            val tvCheckOut = view.findViewById<TextView>(R.id.tvDetailCheckOut)
            val tvDuration = view.findViewById<TextView>(R.id.tvDetailDuration)
            val layoutDuration = view.findViewById<LinearLayout>(R.id.layoutDetailDuration)

            if (checkOutMs > 0L) {
                tvCheckOut.text = timeFmt.format(Date(checkOutMs))

                val durationMs = checkOutMs - checkInMs
                val hours = (durationMs / (1000 * 60 * 60)).toInt()
                val minutes = ((durationMs / (1000 * 60)) % 60).toInt()
                tvDuration.text = "${hours} jam ${minutes} menit"
                layoutDuration.visibility = View.VISIBLE
            } else {
                tvCheckOut.text = "--:--"
                layoutDuration.visibility = View.GONE
            }

            // Status Logic (Background dibikin Putih karena sekarang numpang di Header Ungu)
            val tvStatus = view.findViewById<TextView>(R.id.tvDetailStatus)
            val cardStatus = view.findViewById<MaterialCardView>(R.id.cardDetailStatus)
            val (label, textColor) = when {
                args.getString("status") == "izin" -> "Izin" to "#3B82F6"
                args.getBoolean("isLate") -> "Terlambat" to "#F59E0B"
                else -> "Hadir Tepat Waktu" to "#10B981"
            }
            tvStatus.text = label
            tvStatus.setTextColor(Color.parseColor(textColor))
            cardStatus.setCardBackgroundColor(Color.parseColor("#FFFFFF")) // Solid White Background

            view.findViewById<TextView>(R.id.tvDetailNotesIn).text = args.getString("checkInNotes").takeIf { !it.isNullOrEmpty() } ?: "Tidak ada catatan"
            view.findViewById<TextView>(R.id.tvDetailNotesOut).text = args.getString("checkOutNotes").takeIf { !it.isNullOrEmpty() } ?: "Tidak ada catatan"

            val tvCoords = view.findViewById<TextView>(R.id.tvDetailCoords)
            val btnMap = view.findViewById<MaterialButton>(R.id.btnOpenMap)
            if (lat != 0.0 && lng != 0.0) {
                tvCoords.text = "$lat, $lng"
                btnMap.visibility = View.VISIBLE
                btnMap.setOnClickListener {
                    val gmmIntentUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(Lokasi Presensi)")
                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                    startActivity(mapIntent)
                }
            } else {
                tvCoords.text = "Lokasi tidak tercatat"
                btnMap.visibility = View.GONE
            }
        }
        return view
    }
}