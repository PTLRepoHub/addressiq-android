package com.addressiq.android.ui.screens

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Interactive Google map (Maps JS) in a built-in [WebView] — dependency-free vs
 * the native Google Maps SDK. A fixed centre pin marks the chosen point; when
 * the map settles, the centre coordinate is posted back via [onCenterChanged].
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapWebView(
    apiKey: String,
    lat: Double,
    lon: Double,
    onCenterChanged: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                addJavascriptInterface(MapBridge(onCenterChanged), "PinChannel")
                loadDataWithBaseURL(null, mapHtml(apiKey, lat, lon), "text/html", "utf-8", null)
            }
        },
    )
}

private class MapBridge(private val onCenterChanged: (Double, Double) -> Unit) {
    @JavascriptInterface
    fun postCenter(latlng: String) {
        val parts = latlng.split(",")
        if (parts.size == 2) {
            val la = parts[0].toDoubleOrNull()
            val lo = parts[1].toDoubleOrNull()
            if (la != null && lo != null) onCenterChanged(la, lo)
        }
    }
}

/** Street View panorama in a [WebView], shown when coverage exists. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun StreetViewWebView(apiKey: String, lat: Double, lon: Double, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                loadDataWithBaseURL(null, streetViewHtml(apiKey, lat, lon), "text/html", "utf-8", null)
            }
        },
    )
}

private fun mapHtml(apiKey: String, lat: Double, lon: Double): String = """
<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<style>html,body,#map{height:100%;margin:0;padding:0}
#pin{position:absolute;left:50%;top:50%;transform:translate(-50%,-100%);font-size:32px;z-index:5}</style></head>
<body><div id="map"></div><div id="pin">📍</div>
<script>
function init(){
  var map = new google.maps.Map(document.getElementById('map'), {
    center: {lat: $lat, lng: $lon}, zoom: 17, disableDefaultUI: true, gestureHandling: 'greedy'
  });
  map.addListener('idle', function(){
    var c = map.getCenter();
    if (window.PinChannel) PinChannel.postCenter(c.lat()+','+c.lng());
  });
}
</script>
<script async src="https://maps.googleapis.com/maps/api/js?key=$apiKey&callback=init"></script>
</body></html>
""".trimIndent()

private fun streetViewHtml(apiKey: String, lat: Double, lon: Double): String = """
<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<style>html,body,#pano{height:100%;margin:0;padding:0}</style></head>
<body><div id="pano"></div>
<script>
function init(){
  new google.maps.StreetViewPanorama(document.getElementById('pano'), {
    position: {lat: $lat, lng: $lon}, pov: {heading: 0, pitch: 0}, zoom: 1,
    addressControl: false, fullscreenControl: false, motionTracking: false, motionTrackingControl: false
  });
}
</script>
<script async src="https://maps.googleapis.com/maps/api/js?key=$apiKey&callback=init"></script>
</body></html>
""".trimIndent()
