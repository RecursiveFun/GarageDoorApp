package com.garagedoor.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class DraggableCornerMarker(
    mapView: MapView,
    context: Context,
) : Marker(mapView) {

    private val touchRadiusPx = touchRadiusPx(context)

    init {
        icon = createIcon(context)
        setAnchor(ANCHOR_CENTER, ANCHOR_CENTER)
        setInfoWindow(null)
        setPanToView(false)
        isDraggable = true
    }

    override fun hitTest(event: MotionEvent, mapView: MapView): Boolean {
        val projection = mapView.projection ?: return false
        val screen = projection.toPixels(position, null) ?: return false
        val dx = event.x - screen.x
        val dy = event.y - screen.y
        return dx * dx + dy * dy <= touchRadiusPx * touchRadiusPx
    }

    companion object {
        const val TOUCH_RADIUS_DP = 72f

        fun touchRadiusPx(context: Context): Float =
            TOUCH_RADIUS_DP * context.resources.displayMetrics.density

        fun createIcon(context: Context): BitmapDrawable {
            val density = context.resources.displayMetrics.density
            val size = (56 * density).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val center = size / 2f
            val radius = center - 2f * density

            canvas.drawCircle(
                center,
                center,
                radius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x661565C0
                    style = Paint.Style.FILL
                },
            )
            canvas.drawCircle(
                center,
                center,
                radius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF1565C0.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = 3f * density
                },
            )
            canvas.drawCircle(
                center,
                center,
                6f * density,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                },
            )

            return BitmapDrawable(context.resources, bitmap)
        }
    }
}
