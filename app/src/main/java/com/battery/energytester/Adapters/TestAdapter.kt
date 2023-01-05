package com.battery.energytester.Adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.battery.energytester.Database.DBHelper
import com.battery.energytester.Database.Test
import com.battery.energytester.R

class TestAdapter(context: Context, private val mList: List<Test>, val mClickListener: ClickListener) : RecyclerView.Adapter<TestAdapter.ViewHolder>() {

    interface ClickListener{
        fun showInfo(position: Int)
    }

    val db = DBHelper(context, null)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // inflates the card_view_design view
        // that is used to hold list item
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_row_test, parent, false)

        return ViewHolder(view)
    }

    // binds the list items to a view
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val test = mList[position]
        val num = position + 1
        val name = test.name
        val test_number_and_name = "$num - $name"
        holder.view_test_name.text = test_number_and_name
        holder.view_duration.text = String.format("%.0f", test.duration)
        holder.view_power.text = String.format("%.3f", test.avg_power)
        holder.view_energy.text = String.format("%.3f", test.energy)
        holder.view_layout.setOnClickListener {
            mClickListener.showInfo(position)
        }
    }

    override fun getItemCount(): Int {
        return mList.size
    }

    class ViewHolder(ItemView: View) : RecyclerView.ViewHolder(ItemView) {
        val view_layout: LinearLayout = itemView.findViewById(R.id.test_layout)
        val view_test_name: TextView = itemView.findViewById(R.id.test_number_and_name)
        val view_duration: TextView = itemView.findViewById(R.id.test_duration)
        val view_power: TextView = itemView.findViewById(R.id.test_power)
        val view_energy: TextView = itemView.findViewById(R.id.test_energy)

    }

}