package com.absenku.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.absenku.R
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class NotificationFragment : Fragment() {

    companion object {
        fun newInstance() = NotificationFragment()
    }

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db   by lazy { FirebaseFirestore.getInstance() }
    private var firestoreListener: ListenerRegistration? = null

    private lateinit var btnBack        : MaterialCardView
    private lateinit var cardBadge      : MaterialCardView
    private lateinit var tvBadgeCount   : TextView
    private lateinit var recycler       : RecyclerView
    private lateinit var layoutEmpty    : LinearLayout
    private lateinit var layoutLoading  : LinearLayout

    private val notifAdapter = NotificationAdapter()

    // Meminta izin untuk Push Notification (Android 13+)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(requireContext(), "Izin notifikasi ditolak. Anda mungkin melewatkan pemberitahuan penting.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_notification, container, false)
        bindViews(view)
        setupRecycler()
        askNotificationPermission()
        return view
    }

    override fun onStart() {
        super.onStart()
        loadNotifications()
    }

    override fun onStop() {
        super.onStop()
        firestoreListener?.remove()
        firestoreListener = null
    }

    private fun bindViews(v: View) {
        btnBack       = v.findViewById(R.id.btnBack)
        cardBadge     = v.findViewById(R.id.cardBadge)
        tvBadgeCount  = v.findViewById(R.id.tvBadgeCount)
        recycler      = v.findViewById(R.id.rvNotifications)
        layoutEmpty   = v.findViewById(R.id.layoutEmpty)
        layoutLoading = v.findViewById(R.id.layoutLoading)

        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
    }

    private fun setupRecycler() {
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = notifAdapter
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // ── Load Notifikasi dari Firestore ────────────────────────────────────────
    private fun loadNotifications() {
        val uid = auth.currentUser?.uid ?: return
        setLoading(true)

        firestoreListener?.remove()

        firestoreListener = db.collection("notifications")
            .whereEqualTo("uid", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                setLoading(false)
                if (error != null) {
                    Toast.makeText(requireContext(), "Gagal memuat notifikasi", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val items = snapshot.documents.mapNotNull { doc ->
                    val title     = doc.getString("title")     ?: return@mapNotNull null
                    val message   = doc.getString("message")   ?: ""
                    val createdAt = doc.getLong("createdAt")   ?: 0L
                    val isRead    = doc.getBoolean("isRead")   ?: false
                    val icon      = doc.getString("icon")      ?: "📢"

                    NotificationItem(
                        id        = doc.id,
                        title     = title,
                        message   = message,
                        createdAt = createdAt,
                        isRead    = isRead,
                        icon      = icon
                    )
                }

                notifAdapter.submitList(items)

                // Hitung jumlah yang belum dibaca
                val unreadItems = items.filter { !it.isRead }
                val unreadCount = unreadItems.size

                if (unreadCount > 0) {
                    cardBadge.visibility  = View.VISIBLE
                    tvBadgeCount.text     = "$unreadCount Baru"
                } else {
                    cardBadge.visibility  = View.GONE
                }

                layoutEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                recycler.visibility    = if (items.isEmpty()) View.GONE    else View.VISIBLE

                // Hanya tandai dibaca jika ADA yang belum dibaca (Mencegah Infinite Loop)
                if (unreadItems.isNotEmpty()) {
                    markAsReadInBatch(unreadItems)
                }
            }
    }

    private fun markAsReadInBatch(unreadItems: List<NotificationItem>) {
        // Menggunakan Batch Write agar lebih efisien dan hemat kuota database
        db.runBatch { batch ->
            unreadItems.forEach { item ->
                val docRef = db.collection("notifications").document(item.id)
                batch.update(docRef, "isRead", true)
            }
        }.addOnFailureListener {
            // Abaikan jika gagal
        }
    }

    private fun setLoading(show: Boolean) {
        layoutLoading.visibility = if (show) View.VISIBLE else View.GONE
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Data Model
// ════════════════════════════════════════════════════════════════════════════
data class NotificationItem(
    val id        : String,
    val title     : String,
    val message   : String,
    val createdAt : Long,
    val isRead    : Boolean,
    val icon      : String
)

// ════════════════════════════════════════════════════════════════════════════
//  Adapter
// ════════════════════════════════════════════════════════════════════════════
class NotificationAdapter : RecyclerView.Adapter<NotificationAdapter.VH>() {

    private val items = mutableListOf<NotificationItem>()

    fun submitList(newItems: List<NotificationItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return VH(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvIcon    : TextView = itemView.findViewById(R.id.tvIcon)
        private val tvTitle   : TextView = itemView.findViewById(R.id.tvTitle)
        private val tvMessage : TextView = itemView.findViewById(R.id.tvMessage)
        private val tvTime    : TextView = itemView.findViewById(R.id.tvTime)
        private val dotUnread : View     = itemView.findViewById(R.id.dotUnread)

        private val timeFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))

        fun bind(item: NotificationItem) {
            tvIcon.text    = item.icon
            tvTitle.text   = item.title
            tvMessage.text = item.message

            tvTime.text = if (item.createdAt > 0) {
                timeFmt.format(Date(item.createdAt))
            } else {
                "Baru saja"
            }

            dotUnread.visibility = if (!item.isRead) View.VISIBLE else View.GONE

            // Memberikan efek highlight background jika belum dibaca
            val cardView = itemView as? com.google.android.material.card.MaterialCardView
            if (!item.isRead) {
                cardView?.setCardBackgroundColor(android.graphics.Color.parseColor("#F4F3FF")) // Ungu sangat muda
                cardView?.strokeWidth = 2
                cardView?.strokeColor = android.graphics.Color.parseColor("#E0E7FF")
            } else {
                cardView?.setCardBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"))
                cardView?.strokeWidth = 0
            }
        }
    }
}