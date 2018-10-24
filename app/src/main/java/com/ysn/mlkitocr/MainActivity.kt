package com.ysn.mlkitocr

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionText
import com.otaliastudios.cameraview.Audio
import com.otaliastudios.cameraview.CameraListener
import com.otaliastudios.cameraview.CameraUtils
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val TAG = javaClass.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initCameraView()
        initListeners()
        val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(permissions, 100)
        } else {
            ActivityCompat.requestPermissions(this, permissions, 100)
        }
    }

    override fun onPause() {
        if (camera_view.isStarted) {
            camera_view.stop()
        }
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_activity_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean =
            when (item?.itemId) {
                R.id.menu_item_camera -> {
                    showCameraView()
                    true
                }
                R.id.menu_item_upload_photo -> {
                    showGalleryView()
                    true
                }
                else -> {
                    /* nothing to do in here */
                    super.onOptionsItemSelected(item)
                }
            }

    private fun initListeners() {
        camera_view.addCameraListener(object : CameraListener() {
            override fun onPictureTaken(jpeg: ByteArray?) {
                camera_view.stop()
                CameraUtils.decodeBitmap(jpeg) { bitmap ->
                    image_view.scaleType = ImageView.ScaleType.FIT_XY
                    image_view.setImageBitmap(bitmap)
                    val image = FirebaseVisionImage.fromBitmap(bitmap)
                    val textRecognizer = FirebaseVision.getInstance()
                            .onDeviceTextRecognizer
                    textRecognizer.processImage(image)
                            .addOnSuccessListener {
                                camera_view.visibility = View.GONE
                                image_view.visibility = View.VISIBLE
                                relative_layout_panel_overlay_camera.visibility = View.GONE
                                relative_layout_panel_overlay_result.visibility = View.VISIBLE
                                processTextRecognitionResult(it)
                            }
                            .addOnFailureListener {
                                showToast(it.localizedMessage)
                            }
                    super.onPictureTaken(jpeg)
                }
            }
        })
        button_take_picture.setOnClickListener {
            camera_view.captureSnapshot()
        }
    }

    private fun initCameraView() {
        camera_view.audio = Audio.OFF
        camera_view.playSounds = false
        camera_view.cropOutput = true
    }

    private fun showCameraView() {
        camera_view.start()
        camera_view.visibility = View.VISIBLE
        image_view.visibility = View.GONE
        relative_layout_panel_overlay_camera.visibility = View.VISIBLE
        relative_layout_panel_overlay_result.visibility = View.GONE
    }

    private fun showGalleryView() {
        if (camera_view.isStarted) {
            camera_view.stop()
        }
        camera_view.visibility = View.GONE
        image_view.visibility = View.GONE
        relative_layout_panel_overlay_camera.visibility = View.GONE
        relative_layout_panel_overlay_result.visibility = View.GONE
        val intentGallery = Intent()
        intentGallery.type = "image/*"
        intentGallery.action = Intent.ACTION_GET_CONTENT
        val intentChooser = Intent.createChooser(intentGallery, "Pick Picture")
        startActivityForResult(intentChooser, 100)
    }

    private fun processTextRecognitionResult(firebaseVisionText: FirebaseVisionText) {
        text_view_result.text = firebaseVisionText.text
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT)
                .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                100 -> {
                    val uriSelectedImage = data?.data
                    val filePathColumn = arrayOf(MediaStore.Images.Media.DATA)
                    val cursor = contentResolver.query(uriSelectedImage!!, filePathColumn, null, null, null)
                    if (cursor == null || cursor.count < 1) {
                        return
                    }

                    cursor.moveToFirst()
                    val columnIndex = cursor.getColumnIndex(filePathColumn[0])
                    if (columnIndex < 0) {
                        showToast("Invalid image")
                        return
                    }

                    val picturePath = cursor.getString(columnIndex)
                    if (picturePath == null) {
                        showToast("Picture path not found")
                        return
                    }
                    cursor.close()
                    Log.d(TAG, "picturePath: $picturePath")
                    val bitmap = BitmapFactory.decodeFile(picturePath)
                    image_view.setImageBitmap(bitmap)

                    val image = FirebaseVisionImage.fromBitmap(bitmap)
                    val textRecognizer = FirebaseVision.getInstance()
                            .onDeviceTextRecognizer
                    textRecognizer.processImage(image)
                            .addOnSuccessListener {
                                camera_view.visibility = View.GONE
                                image_view.visibility = View.VISIBLE
                                relative_layout_panel_overlay_result.visibility = View.VISIBLE
                                relative_layout_panel_overlay_camera.visibility = View.GONE
                                image_view.scaleType = ImageView.ScaleType.CENTER_CROP
                                processTextRecognitionResult(it)
                            }
                            .addOnFailureListener {
                                showToast(it.localizedMessage)
                            }
                }
                else -> {
                    /* nothing to do in here */
                }
            }
        }
    }

}
