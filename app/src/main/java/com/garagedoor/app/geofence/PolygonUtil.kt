package com.garagedoor.app.geofence

import com.garagedoor.app.data.LatLngPoint
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil

object PolygonUtil {

    fun contains(lat: Double, lng: Double, polygon: List<LatLngPoint>): Boolean {
        if (polygon.size < 3) return false
        val path = polygon.map { LatLng(it.lat, it.lng) }
        return PolyUtil.containsLocation(LatLng(lat, lng), path, true)
    }
}
