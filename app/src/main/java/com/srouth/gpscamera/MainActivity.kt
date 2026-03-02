package com.srouth.gpscamera

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.*
import android.view.View.VISIBLE
import android.widget.*
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var gridView: GridView
    private val imageList = ArrayList<Uri>()
    private lateinit var adapter: ImageAdapter
    private lateinit var locationBtn: Button
    private lateinit var editImageBtn : Button

    // Permission Codes
    private val PERMISSION_REQUEST_CODE = 2000

    private var address : String = ""
    private var lat : String = ""
    private var lng : String = ""
    private var dateTime : String = ""

    // ---------------- MAP RESULT LAUNCHER ----------------
    private val mapResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                address = result.data?.getStringExtra("selected_address").toString()
                lat = result.data?.getDoubleExtra("selected_lat", 0.0).toString()
                lng = result.data?.getDoubleExtra("selected_lng", 0.0).toString()
                dateTime = result.data?.getStringExtra("selected_datetime").toString()

                locationBtn.text = "Change Location"
                editImageBtn.visibility = VISIBLE
                Toast.makeText(this, "📍 $address", Toast.LENGTH_SHORT).show()
            }
        }

    // ---------------- PHOTO PICKER ----------------
    private val multiPhotoPicker =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            if (uris.isNotEmpty()) {
                imageList.clear()
                imageList.addAll(uris)
                adapter.notifyDataSetChanged()
                locationBtn.visibility = View.VISIBLE
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // UI Setup (Same as your previous code)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.BLACK)
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(30, systemBars.top + 40, 30, systemBars.bottom + 20)
            insets
        }

        val button = createStyledButton("Select Multiple Photos", "#6ca9f5")
        gridView = GridView(this).apply {
            numColumns = 3
            verticalSpacing = 20
            horizontalSpacing = 20
        }
        locationBtn = createStyledButton("Choose Location", "#5abf89").apply { visibility = View.GONE }
        editImageBtn = createStyledButton("Edit Images", "#e34944").apply { visibility = View.GONE }

        root.addView(button)
        root.addView(gridView, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 40 })
        root.addView(locationBtn)
        root.addView(editImageBtn)
        setContentView(root)

        adapter = ImageAdapter()
        gridView.adapter = adapter

        button.setOnClickListener { checkPermissionsAndOpenGallery() }
        locationBtn.setOnClickListener { checkPermissionsAndOpenMap() }
        editImageBtn.setOnClickListener { openEditImagePage() }
    }

    // --- PERMISSION LOGIC ---

    private fun checkPermissionsAndOpenGallery() {
        val permissions = mutableListOf<String>()

        // Gallery Permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (hasPermissions(permissions)) {
            openGallery()
        } else {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private fun checkPermissionsAndOpenMap() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (hasPermissions(permissions.toList())) {
            openMapForLocation()
        } else {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
        }
    }

    private fun hasPermissions(permissions: List<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allGranted) {
                Toast.makeText(this, "Permissions Granted!", Toast.LENGTH_SHORT).show()
                // Automatically trigger the action the user intended
                if (permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    openMapForLocation()
                } else {
                    openGallery()
                }
            } else {
                // Check if user clicked "Deny" but NOT "Don't ask again"
                val shouldShowRationale = permissions.any {
                    ActivityCompat.shouldShowRequestPermissionRationale(this, it)
                }

                if (shouldShowRationale) {
                    // Show a "Retry or Exit" Alert for standard denial
                    showRetryAlert("Permissions Denied", "This app needs these permissions to work. Do you want to try again?") {
                        // Retry: Re-request the same permissions
                        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
                    }
                } else {
                    // User clicked "Don't ask again" or denied permanently
                    showSettingsAlert()
                }
            }
        }
    }

    private fun showRetryAlert(title: String, message: String, onRetry: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Retry") { _, _ -> onRetry() }
            .setNegativeButton("Exit App") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun showSettingsAlert() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Permanently Denied")
            .setMessage("You have disabled required permissions. You must enable them in System Settings to use this app, or exit.")
            .setPositiveButton("Go to Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Exit App") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    // --- HELPERS ---

    private fun openGallery() {
        multiPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun openMapForLocation() {
        val intent = Intent(this, MapsActivity::class.java)
        mapResultLauncher.launch(intent)
    }

    private fun openEditImagePage() {
        if (imageList.isEmpty()) return
        val intent = Intent(this, EditImageActivity::class.java).apply {
            putParcelableArrayListExtra("images", imageList)
            putExtra("address", address)
            putExtra("datetime", dateTime)
        }
        startActivity(intent)
    }

    private fun createStyledButton(txt: String, color: String) = Button(this).apply {
        text = txt
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = 40f
        }
        setPadding(40, 25, 40, 25)
        val params = LinearLayout.LayoutParams(-1, -2)
        params.setMargins(0, 10, 0, 10)
        layoutParams = params
    }

    inner class ImageAdapter : BaseAdapter() {
        override fun getCount(): Int = imageList.size
        override fun getItem(p: Int) = imageList[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, v: View?, parent: ViewGroup?): View {
            val iv = ImageView(this@MainActivity)
            iv.layoutParams = AbsListView.LayoutParams(-1, 350)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setImageURI(imageList[p])
            return iv
        }
    }
}