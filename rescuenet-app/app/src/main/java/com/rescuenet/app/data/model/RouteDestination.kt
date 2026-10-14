package com.rescuenet.app.data.model

/** A place the user wants a safe route to (chosen from Nearby Help or the destination picker). */
data class RouteDestination(
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
)
