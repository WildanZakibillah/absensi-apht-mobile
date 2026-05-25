package com.absenku.admin

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.absenku.R
import com.google.android.material.card.MaterialCardView

class RecentAbsenAdapter(
    private val items: List<RecentAbsenItem>
) : RecyclerView.Adapter<RecentAbsenAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvNama   : TextView         = view.findViewById(R.id.tvRecentNama)
        val tvStatus : TextView         = view.findViewById(R.id.tvRecentStatus)
        val tvTimeIn : TextView         = view.findViewById(R.id.tvRecentTimeIn)
        val tvTimeOut: TextView         = view.findViewById(R.id.tvRecentTimeOut)
        val cardBg   : MaterialCardView = view.findViewById(R.id.cardRecentItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_recent_absen, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvNama.text    = item.uid
        holder.tvTimeIn.text  = "Masuk: ${item.timeIn}"
        holder.tvTimeOut.text = "Pulang: ${item.timeOut}"

        when (item.status) {
            "hadir" -> {
                if (item.isLate) {
                    holder.tvStatus.text = "⚠ Terlambat"
                    holder.tvStatus.setTextColor(Color.parseColor("#F59E0B"))
                    holder.cardBg.setCardBackgroundColor(Color.parseColor("#FFFBEB"))
                } else {
                    holder.tvStatus.text = "✓ Hadir"
                    holder.tvStatus.setTextColor(Color.parseColor("#4ECCA3"))
                    holder.cardBg.setCardBackgroundColor(Color.parseColor("#F0FDF4"))
                }
            }
            "izin" -> {
                holder.tvStatus.text = "📋 Izin"
                holder.tvStatus.setTextColor(Color.parseColor("#6C63FF"))
                holder.cardBg.setCardBackgroundColor(Color.parseColor("#F5F3FF"))
            }
            else -> {
                holder.tvStatus.text = "-"
                holder.tvStatus.setTextColor(Color.parseColor("#9CA3AF"))
            }
        }
    }
}

class AdminUsersAdapter(
    private val items   : List<UserItem>,
    private val onClick : (UserItem) -> Unit
) : RecyclerView.Adapter<AdminUsersAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvNama  : TextView = view.findViewById(R.id.tvUserNama)
        val tvEmail : TextView = view.findViewById(R.id.tvUserEmail)
        val tvNip   : TextView = view.findViewById(R.id.tvUserNip)
        val tvRole  : TextView = view.findViewById(R.id.tvUserRole)
        val tvInitial: TextView= view.findViewById(R.id.tvUserInitial)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_admin_user, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        holder.tvNama.text   = item.name
        holder.tvEmail.text  = item.email
        holder.tvNip.text    = "NIP: ${item.nik_perusahaan}"
        holder.tvRole.text   = item.role

        holder.tvInitial.text = item.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        if (item.role.uppercase() == "ADMIN") {
            holder.tvRole.setTextColor(Color.parseColor("#6C63FF"))
            holder.tvRole.setBackgroundColor(Color.parseColor("#EEF0FF"))
        } else {
            holder.tvRole.setTextColor(Color.parseColor("#4ECCA3"))
            holder.tvRole.setBackgroundColor(Color.parseColor("#E8FBF4"))
        }

        holder.itemView.setOnClickListener { onClick(item) }
    }
}

class AdminIzinAdapter(
    private val items   : List<IzinItem>,
    private val onClick : (IzinItem) -> Unit
) : RecyclerView.Adapter<AdminIzinAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvNama      : TextView = view.findViewById(R.id.tvIzinNama)
        val tvType      : TextView = view.findViewById(R.id.tvIzinType)
        val tvDate      : TextView = view.findViewById(R.id.tvIzinDate)
        val tvStatus    : TextView = view.findViewById(R.id.tvIzinStatus)
        val tvAlasan    : TextView = view.findViewById(R.id.tvIzinAlasan)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_admin_izin, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvNama.text   = item.namaUser
        holder.tvType.text   = item.izinType
        holder.tvDate.text   = formatDate(item.date)
        holder.tvAlasan.text = if (item.alasan.isNotEmpty()) "\"${item.alasan}\"" else ""
        holder.tvAlasan.visibility = if (item.alasan.isNotEmpty()) View.VISIBLE else View.GONE

        when (item.approvalStatus) {
            "pending" -> {
                holder.tvStatus.text = "⏳ Menunggu"
                holder.tvStatus.setTextColor(Color.parseColor("#F59E0B"))
            }
            "approved" -> {
                holder.tvStatus.text = "✅ Disetujui"
                holder.tvStatus.setTextColor(Color.parseColor("#4ECCA3"))
            }
            "rejected" -> {
                holder.tvStatus.text = "❌ Ditolak"
                holder.tvStatus.setTextColor(Color.parseColor("#EF4444"))
            }
        }

        holder.itemView.setOnClickListener { onClick(item) }
    }

    private fun formatDate(raw: String): String {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
            val out = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale("id","ID"))
            out.format(sdf.parse(raw)!!)
        } catch (_: Exception) { raw }
    }
}

class AdminRekapAdapter(
    private val items: List<RekapRowItem>
) : RecyclerView.Adapter<AdminRekapAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvNo        : TextView = view.findViewById(R.id.tvRekapNo)
        val tvNama      : TextView = view.findViewById(R.id.tvRekapNama)
        val tvNip       : TextView = view.findViewById(R.id.tvRekapNip)
        val tvHadir     : TextView = view.findViewById(R.id.tvRekapHadir)
        val tvIzin      : TextView = view.findViewById(R.id.tvRekapIzin)
        val tvTerlambat : TextView = view.findViewById(R.id.tvRekapTerlambat)
        val tvAlpha     : TextView = view.findViewById(R.id.tvRekapAlpha)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_rekap_row, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvNo.text        = "${position + 1}"
        holder.tvNama.text      = item.nama
        holder.tvNip.text       = item.nip
        holder.tvHadir.text     = item.hadir.toString()
        holder.tvIzin.text      = item.izin.toString()
        holder.tvTerlambat.text = item.terlambat.toString()
        holder.tvAlpha.text     = item.alpha.toString()

        if (item.terlambat >= 3 || item.alpha >= 3) {
            holder.itemView.setBackgroundColor(Color.parseColor("#FFF8F8"))
        } else {
            holder.itemView.setBackgroundColor(Color.WHITE)
        }
    }
}