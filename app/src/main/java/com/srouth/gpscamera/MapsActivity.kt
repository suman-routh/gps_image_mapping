package com.srouth.gpscamera

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.os.Bundle
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import java.text.SimpleDateFormat
import java.util.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var confirmBtn: Button
    private lateinit var addressPreview: TextView
    private lateinit var root: FrameLayout

    private var marker: Marker? = null
    private var selectedAddress = ""
    private var selectedLatLng: LatLng? = null

    private val LOCATION_PERMISSION_CODE = 2001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        root = FrameLayout(this)
        setContentView(root)

        // Apply proper insets
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        // Map container
        val mapContainer = FrameLayout(this)
        mapContainer.id = View.generateViewId()
        root.addView(
            mapContainer,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // Address Preview (Dark Grey)
        addressPreview = TextView(this)
        addressPreview.setTextColor(Color.WHITE)
        addressPreview.textSize = 14f
        addressPreview.visibility = View.GONE
        addressPreview.setPadding(40, 30, 40, 30)

        val previewBg = GradientDrawable()
        previewBg.setColor(Color.parseColor("#333333"))
        previewBg.cornerRadius = 40f
        addressPreview.background = previewBg

        val previewParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        previewParams.gravity = Gravity.BOTTOM
        previewParams.setMargins(40, 0, 40, 160)
        root.addView(addressPreview, previewParams)

        // Confirm Button
        confirmBtn = Button(this)
        confirmBtn.text = "Confirm Location"
        confirmBtn.setTextColor(Color.WHITE)

        val drawable = GradientDrawable()
        drawable.setColor(Color.parseColor("#02ed70"))
        drawable.cornerRadius = 40f
        confirmBtn.background = drawable
        confirmBtn.setPadding(40, 25, 40, 25)

        val btnParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        btnParams.gravity = Gravity.BOTTOM
        btnParams.setMargins(40, 0, 40, 60)
        root.addView(confirmBtn, btnParams)

        val mapFragment = SupportMapFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(mapContainer.id, mapFragment)
            .commit()
        mapFragment.getMapAsync(this)

        confirmBtn.setOnClickListener {
            if (selectedLatLng == null) {
                Toast.makeText(this, "Please select location", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val resultIntent = intent
            resultIntent.putExtra("selected_address", selectedAddress)
            resultIntent.putExtra("selected_lat", selectedLatLng!!.latitude)
            resultIntent.putExtra("selected_lng", selectedLatLng!!.longitude)
            resultIntent.putExtra("selected_datetime", getCurrentDateTime())

            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        map.setOnMapLongClickListener {
            animateMarkerDrop(it)
        }

        checkLocationPermission()
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_CODE
            )
        } else {
            enableLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableLocation() {
        map.isMyLocationEnabled = true

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val currentLatLng = LatLng(it.latitude, it.longitude)

                // Show current location marker automatically
                marker = map.addMarker(
                    MarkerOptions()
                        .position(currentLatLng)
                        .title("Your Current Location")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                )

                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(currentLatLng, 17f)
                )
            }
        }
    }

    // Smooth marker drop animation
    private fun animateMarkerDrop(latLng: LatLng) {

        selectedAddress = getAddress(latLng)
        selectedLatLng = latLng

        addressPreview.text = selectedAddress
        addressPreview.visibility = View.VISIBLE

        marker?.remove()

        val startLatLng = LatLng(latLng.latitude + 0.01, latLng.longitude)

        marker = map.addMarker(
            MarkerOptions()
                .position(startLatLng)
                .title(selectedAddress)
        )

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 800
        animator.interpolator = DecelerateInterpolator()

        animator.addUpdateListener {
            val value = it.animatedValue as Float
            val lat = startLatLng.latitude - (0.01 * value)
            marker?.position = LatLng(lat, latLng.longitude)
        }

        animator.start()

        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
    }

    private fun getAddress(latLng: LatLng): String {
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val list = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            list?.get(0)?.getAddressLine(0) ?: "Unknown location"
        } catch (e: Exception) {
            "Unknown location"
        }
    }

    private fun getCurrentDateTime(): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        return sdf.format(Date())
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == LOCATION_PERMISSION_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            enableLocation()
        }
    }
}