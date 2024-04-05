package com.pushpal.download

import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var snackbar: Snackbar
    private lateinit var downloadNotification: Notification
    private val multiplePermissionId = 14
    private val multiplePermissionNameList = if (Build.VERSION.SDK_INT >= 33) {
        arrayListOf()
    } else {
        arrayListOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val rootView = findViewById<View>(R.id.rootView)


        snackbar = Snackbar.make(
            rootView,
            "No Internet Connection",
            Snackbar.LENGTH_INDEFINITE
        ).setAction("Setting") {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }

        val downloadButton = findViewById<Button>(R.id.download_button)
        downloadButton.setOnClickListener {

            if (checkMultiplePermission()) {
                startDownload()
            } else {
                Toast.makeText(this, "No Permission", Toast.LENGTH_SHORT).show()
            }

        }
    }

    private fun checkMultiplePermission(): Boolean {
        val listPermissionNeeded = arrayListOf<String>()
        for (permission in multiplePermissionNameList) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                listPermissionNeeded.add(permission)
            }
        }
        if (listPermissionNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                listPermissionNeeded.toTypedArray(),
                multiplePermissionId
            )
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == multiplePermissionId) {
            if (grantResults.isNotEmpty()) {
                var isGrant = true
                for (element in grantResults) {
                    if (element == PackageManager.PERMISSION_DENIED) {
                        isGrant = false
                    }
                }
                if (isGrant) {
                    // here all permission granted successfully
                    startDownload()
                } else {
                    var someDenied = false
                    for (permission in permissions) {
                        if (!ActivityCompat.shouldShowRequestPermissionRationale(
                                this,
                                permission
                            )
                        ) {
                            if (ActivityCompat.checkSelfPermission(
                                    this,
                                    permission
                                ) == PackageManager.PERMISSION_DENIED
                            ) {
                                someDenied = true
                            }
                        }
                    }
                    if (someDenied) {
                        // here app Setting open because all permission is not granted
                        // and permanent denied
                        appSettingOpen(this)
                    } else {
                        // here warning permission show
                        warningPermissionDialog(this) { _: DialogInterface, which: Int ->
                            when (which) {
                                DialogInterface.BUTTON_POSITIVE ->
                                    checkMultiplePermission()
                            }
                        }
                    }
                }
            }
        }
    }


    private fun startDownload() {
        val url =
            "https://file-examples.com/storage/fe0e2ce82f660c1579f31b4/2017/04/file_example_MP4_1280_10MG.mp4" // Replace with your actual download URL
        val fileName = "downloaded_file.pdf"


        if (isConnected(this)) {
            if (snackbar.isShown) {
                snackbar.dismiss()
            }

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            downloadNotification = createDownloadNotification(fileName, 0)
            notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, downloadNotification)

            // Start download in background thread
            Thread {
                downloadFile(url)
                runOnUiThread {
                    notificationManager.cancel(DOWNLOAD_NOTIFICATION_ID)
                    updateProgress(100) // Update progress to 100% after download completes
                }
            }.start()
        } else {
            snackbar.show()
        }



    }

    private fun downloadFile(url: String) {
        val folder = File(
            Environment.getExternalStorageDirectory().toString() + "/Download/Image"
        )
        if (!folder.exists()) {
            folder.mkdirs()
        }
        val fileName = url.split("/").last()

        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url))
        request.setAllowedNetworkTypes(
            DownloadManager.Request.NETWORK_WIFI or
                    DownloadManager.Request.NETWORK_MOBILE
        )
        request.setTitle(fileName)
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            "Image/$fileName"
        )
        downloadManager.enqueue(request)


        val urlConnection = URL(url).openConnection() as HttpURLConnection
        urlConnection.connect()

        val inputStream = urlConnection.inputStream
        val outputStream = openFileOutput(fileName, Context.MODE_PRIVATE)

        val buffer = ByteArray(1024)
        var readBytes: Int
        var downloaded = 0

        while (inputStream.read(buffer).also { readBytes = it } != -1) {
            outputStream.write(buffer, 0, readBytes)
            downloaded += readBytes
            updateProgress(downloaded * 100 / urlConnection.contentLength)
        }

        inputStream.close()
        outputStream.close()

    }

    private fun updateProgress(progress: Int)  {
        downloadNotification = createDownloadNotification("Downloading...", progress)
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, downloadNotification)

        runOnUiThread {
            val progressBar = findViewById<ProgressBar>(R.id.download_progress)
            val txtView = findViewById<TextView>(R.id.text_view)
            progressBar?.visibility = View.VISIBLE
            progressBar.progress = progress
            txtView.text = "$progress/100"
        }
    }

    private fun createDownloadNotification(title: String, progress: Int): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText("$progress%")
            .setProgress(100, progress, false)
            .setOngoing(true)

        if (progress == 100) {
            builder.setContentText("Download complete!")
                .setOngoing(false)
                .setAutoCancel(true)
        }

        return builder.build()
    }

    companion object {
        private const val DOWNLOAD_NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "download_channel"
    }
}