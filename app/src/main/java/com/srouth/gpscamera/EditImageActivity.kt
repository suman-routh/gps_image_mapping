package com.srouth.gpscamera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.exifinterface.media.ExifInterface
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Locale

class EditImageActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var imageList: ArrayList<Uri>
    private lateinit var infoText: TextView
    private lateinit var infoContainer: FrameLayout
    private lateinit var adapter: ImagePagerAdapter

    private var address = ""
    private var dateTime = ""

    private fun getExifDate(uri: Uri, fallback: String): String {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val result = if (inputStream != null) {
                val exif = ExifInterface(inputStream)
                val dateStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                inputStream.close()
                if (!dateStr.isNullOrEmpty()) formatExifDate(dateStr) else fallback
            } else {
                fallback
            }
            result
        } catch (e: Exception) {
            fallback
        }
    }

    private fun formatExifDate(exifDate: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
            val date = parser.parse(exifDate) ?: return exifDate
            val day = SimpleDateFormat("d", Locale.getDefault()).format(date).toInt()
            val monthYear = SimpleDateFormat("MMMM, yyyy", Locale.getDefault()).format(date)
            val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
            val suffix = when {
                day in 11..13 -> "th"
                day % 10 == 1 -> "st"
                day % 10 == 2 -> "nd"
                day % 10 == 3 -> "rd"
                else -> "th"
            }
            "$day$suffix $monthYear | $time"
        } catch (e: Exception) {
            exifDate
        }
    }

    private val locationPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            address = result.data?.getStringExtra("selected_address") ?: address
            updateOverlayText()
        }
    }

    private fun updateOverlayText() {
        if (::infoText.isInitialized) {
            infoText.text = "📍 $address\n🕒 $dateTime"
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val root = RelativeLayout(this)
        root.setBackgroundColor(Color.BLACK)
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        imageList = intent.getParcelableArrayListExtra("images") ?: arrayListOf()
        address = intent.getStringExtra("address") ?: "Unknown Location"
        val intentDateTime = intent.getStringExtra("datetime") ?: "Date Unknown"

        // 1. Back Button
        val tvBack = TextView(this).apply {
            id = View.generateViewId()
            text = "✕ Back"
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(40, 20, 40, 40)
            setOnClickListener { finish() }
        }
        root.addView(tvBack, RelativeLayout.LayoutParams(-2, -2))

        // 2. Bottom Buttons
        val btnLayout = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(30, 20, 30, 40)
        }

        val btnChange = createStyledButton("Change Location", "#E53935") {
            locationPickerLauncher.launch(Intent(this, MapsActivity::class.java))
        }

        val btnSave = createStyledButton("Save", "#66BB6A") {
            saveCurrentImage()
        }

        btnLayout.addView(btnChange)
        btnLayout.addView(btnSave)
        root.addView(btnLayout, RelativeLayout.LayoutParams(-1, -2).apply {
            addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
        })

        // 3. ViewPager & Adapter Initialization
        adapter = ImagePagerAdapter()
        viewPager = ViewPager2(this).apply {
            this.adapter = this@EditImageActivity.adapter
            // Removed the manual LayoutManager override to prevent NullPointerException
        }

        root.addView(viewPager, RelativeLayout.LayoutParams(-1, -1).apply {
            addRule(RelativeLayout.BELOW, tvBack.id)
            addRule(RelativeLayout.ABOVE, btnLayout.id)
        })

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (imageList.isNotEmpty() && position < imageList.size) {
                    dateTime = getExifDate(imageList[position], intentDateTime)
                    updateOverlayText()
                }
            }
        })

        // 4. Draggable Overlay
        infoContainer = FrameLayout(this).apply {
            setPadding(30, 30, 30, 30)
            background = GradientDrawable().apply {
                cornerRadius = 30f
                setColor(Color.parseColor("#CC000000"))
            }
        }

        infoText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        infoContainer.addView(infoText)

        val resizeHandle = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_crop)
            setColorFilter(Color.WHITE)
        }
        infoContainer.addView(resizeHandle, FrameLayout.LayoutParams(50, 50, Gravity.BOTTOM or Gravity.END))

        root.addView(infoContainer, RelativeLayout.LayoutParams(650, 350).apply {
            addRule(RelativeLayout.CENTER_IN_PARENT)
        })

        setupDragAndResize(infoContainer, resizeHandle)

        if (imageList.isNotEmpty()) {
            dateTime = getExifDate(imageList[0], intentDateTime)
            updateOverlayText()
        }
    }

    private fun saveCurrentImage() {
        if (imageList.isEmpty()) {
            finish()
            return
        }

        val position = viewPager.currentItem
        if (position < 0 || position >= imageList.size) return

        // 1. Capture Bitmap
        val bitmap = Bitmap.createBitmap(viewPager.width, viewPager.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        viewPager.draw(canvas)

        canvas.save()
        canvas.translate(infoContainer.x - viewPager.x, infoContainer.y - viewPager.y)
        infoContainer.draw(canvas)
        canvas.restore()

        // 2. Save to Storage
        val fileName = "GPS_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GPSCamera")
            }
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            val outputStream: OutputStream? = contentResolver.openOutputStream(it)
            outputStream?.use { os -> bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os) }
            Toast.makeText(this, "Saved to Gallery", Toast.LENGTH_SHORT).show()

            // 3. Update List and Adapter SAFELY
            imageList.removeAt(position)

            // Using notifyDataSetChanged() here is safer for ViewPager2 when removing items
            // to avoid internal state inconsistencies and NullPointerExceptions
            adapter.notifyDataSetChanged()

            if (imageList.isEmpty()) {
                finish()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragAndResize(container: View, handle: View) {
        var dX = 0f
        var dY = 0f
        container.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                dX = v.x - event.rawX
                dY = v.y - event.rawY
            } else if (event.action == MotionEvent.ACTION_MOVE) {
                v.animate().x(event.rawX + dX).y(event.rawY + dY).setDuration(0).start()
            }
            true
        }
        handle.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_MOVE) {
                val newWidth = (event.rawX - container.x).toInt()
                val newHeight = (event.rawY - container.y).toInt()
                if (newWidth > 250 && newHeight > 150) {
                    container.layoutParams.width = newWidth
                    container.layoutParams.height = newHeight
                    container.requestLayout()
                }
            }
            true
        }
    }

    private fun createStyledButton(txt: String, color: String, onClick: () -> Unit) =
        Button(this).apply {
            text = txt
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = GradientDrawable().apply {
                cornerRadius = 25f
                setColor(Color.parseColor(color))
            }
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(10, 0, 10, 0) }
            setOnClickListener { onClick() }
        }

    inner class ImagePagerAdapter : RecyclerView.Adapter<ImagePagerAdapter.ViewHolder>() {
        inner class ViewHolder(val iv: ImageView) : RecyclerView.ViewHolder(iv)
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(ImageView(p.context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            scaleType = ImageView.ScaleType.FIT_CENTER
        })
        override fun getItemCount() = imageList.size
        override fun onBindViewHolder(h: ViewHolder, p: Int) {
            if (p < imageList.size) {
                h.iv.setImageURI(imageList[p])
            }
        }
    }
}