package com.example.childlocate.data.model

data class Task(
    val id: String = "",
    val name: String = "",
    val time: String = "",
    var childCompleted: Boolean = false,
    var parentApproved: Boolean = false
)

