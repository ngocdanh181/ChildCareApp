package com.example.childlocate.ui.parent.home

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.example.childlocate.R
import com.example.childlocate.data.model.Child


class ChildSpinnerAdapter(context: Context) : ArrayAdapter<Child>(context, R.layout.spinner_item_layout) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createViewFromResource(position, convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createViewFromResource(position, convertView, parent)
    }

    private fun createViewFromResource(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.spinner_item_layout, parent, false)
        val textView = view.findViewById<TextView>(R.id.spinnerItemText)
        getItem(position)?.let { child ->
            textView.text = if (position == 0) {
                "Child now is ${child.childName}"

            } else {
                child.childName
            }
            Log.d("Child","${child.childName}")
        }
        return view
    }
}