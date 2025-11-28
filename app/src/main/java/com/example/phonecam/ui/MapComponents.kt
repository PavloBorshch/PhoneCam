package com.example.phonecam.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.phonecam.ControllerLocation
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

// Екран карти на основі OpenStreetMap
@Composable
fun MapScreen(
    controllers: List<ControllerLocation>
) {
    val context = LocalContext.current

    // Ініціалізація конфігурації OSM
    Configuration.getInstance().userAgentValue = context.packageName

    // Центр карти
    val kyivCenter = GeoPoint(50.4501, 30.5234)

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(13.0)
                    controller.setCenter(kyivCenter)
                }
            },
            update = { mapView ->
                // Очищення попередніх оверлеїв
                mapView.overlays.clear()

                // Додавання геофенсінг зони
                val circle = Polygon().apply {
                    points = Polygon.pointsAsCircle(kyivCenter, 1500.0) // 1.5 км радіус
                    fillPaint.color = AndroidColor.argb(50, 46, 125, 50) // Прозорий зелений
                    outlinePaint.color = AndroidColor.rgb(46, 125, 50)
                    outlinePaint.strokeWidth = 3f
                    title = "Безпечна зона"
                }
                mapView.overlays.add(circle)

                // Додавання маркерів контролерів
                controllers.forEach { controller ->
                    val marker = Marker(mapView).apply {
                        position = controller.position
                        title = controller.name
                        snippet = if (controller.isActive) "Онлайн" else "Офлайн"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    mapView.overlays.add(marker)
                }

                // Перемалювати карту
                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        // Легенда карти
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), MaterialTheme.shapes.medium)
                .padding(8.dp)
        ) {
            Text("OSM Карта об'єктів", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("Зелена зона: Безпечний периметр", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
            Text("OpenStreetMap", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}