package com.krsolutions.era

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.Camera
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_preview.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import android.opengl.ETC1.getHeight
import android.opengl.ETC1.getWidth
import io.reactivex.ObservableOnSubscribe
import io.reactivex.internal.operators.observable.ObservableCreate


class PreviewFragment: Fragment(),ActivityCompat.OnRequestPermissionsResultCallback {


    private lateinit var mFile: File;
    private lateinit var images:Array<Image>;
    private val MAX_HEIGHT = 1920;
    private val MAX_WIDTH = 1080;
    private var imageReader = ImageReader.newInstance(MAX_WIDTH, MAX_HEIGHT, ImageFormat.JPEG, 10)
    private lateinit var captureSession: CameraCaptureSession;
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    val cameraManager by lazy { activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager };
    private lateinit var cameraDevice: CameraDevice;

    private val deviceStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "camera device opened")
            if (camera != null) {
                cameraDevice = camera;
                previewSession()
            }
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.d(TAG, "camera device dsconnected")
            camera?.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(TAG, "camera device error")
            this@PreviewFragment.activity?.finish()
        }
    }


    //
    private lateinit var backgroundThread: HandlerThread;
    private lateinit var backgroundHandler: Handler;


    fun previewSession() {
        val surfaceTexture = preview.surfaceTexture;
        surfaceTexture.setDefaultBufferSize(MAX_WIDTH, MAX_HEIGHT)
        val surface = Surface(surfaceTexture)
//        var images = ObservableCreate<Image>(ObservableOnSubscribe<Image> {
//
//        }


        imageReader.setOnImageAvailableListener(mOnImageAvailableListener, backgroundHandler)
        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder.addTarget(surface)
        captureRequestBuilder.addTarget(imageReader.surface)
        cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigureFailed(session: CameraCaptureSession?) {
                Log.e(TAG, "creating camera capture session failed")
            }

            override fun onConfigured(session: CameraCaptureSession?) {
                if (session != null) {
                    captureSession = session;
                    captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                    captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                }
            }
        }, null)
    }


    private val mOnImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        Log.d("Img", "onImageAvailable")
        var image =reader.acquireNextImage()
        image.close()
//        val matrix = Matrix()
//        matrix.postRotate(90f)
//        var bmp = Bitmap.createBitmap(image as Bitmap, 0, 0, (image as Bitmap).width, (image as Bitmap).height, matrix, true)
//        backgroundHandler.post(ImageSaver(image, mFile))

    }

    fun closeCamera() {
        if (this::captureSession.isInitialized) {
            captureSession.close()
        }
        if (this::cameraDevice.isInitialized) {
            cameraDevice.close()
        }
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("ERA_Cam").also { it.start() }
        backgroundHandler = Handler(backgroundThread.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join()
        } catch (e: InterruptedException) {
            Log.e(TAG, e.toString());
        }
    }

    fun <T> getCameraCharacteristics(cameraId: String, key: CameraCharacteristics.Key<T>): T {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId);
        return when (key) {
            CameraCharacteristics.LENS_FACING -> characteristics.get(key);
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP -> characteristics.get(key);
            else -> throw IllegalArgumentException("Key not recognized");
        }
    }

    fun cameraID(lens: Int): String {
        var deviceID = listOf<String>();
        try {
            val cameraIdList = cameraManager.cameraIdList;
            deviceID = cameraIdList.filter { lens == getCameraCharacteristics(it, CameraCharacteristics.LENS_FACING) }
        } catch (e: CameraAccessException) {
            Log.d(TAG, "Unable to access camera");
        }
        return deviceID[0];
    }

    fun connectCamera() {
        val deviceId = cameraID(CameraCharacteristics.LENS_FACING_FRONT);
        Log.d(TAG, "Front CamID: ${deviceId}");
        try {
            cameraManager.openCamera(deviceId, deviceStateCallback, backgroundHandler);
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, e.toString())
        } catch (e: SecurityException) {
            Log.e(TAG, e.toString())
        }

    }

    companion object {
        private val TAG = PreviewFragment::class.qualifiedName;
        public fun newInstance() = PreviewFragment();
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_preview, container, false);
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState);
        activity?.window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        mFile= File(activity!!.applicationContext!!.getExternalFilesDir(null),"ERAAAA");
        if(!mFile.exists()){
            Log.d(TAG,"file saved")
            mFile.mkdirs()
            Log.d(TAG,mFile.absolutePath)
        }
        mFile = File(mFile,"test.jpg")

    }

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
//            Log.d(TAG,"updated texture")
        };

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean = true;

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            Log.d(TAG, "SurfaceTexture is available\n width: ${width}\theight: ${height}");
            openCamera();
        }
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (preview.isAvailable) {
            Log.d(TAG, "Opening Camera")
            openCamera();
        } else {
            preview.surfaceTextureListener = textureListener;
        }
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    fun openCamera() {
        if (ContextCompat.checkSelfPermission(activity!!, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission()
            return
        } else {
            connectCamera()
        }
    }


    private fun requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            AppDialog.ConfirmationDialog().show(childFragmentManager, AppDialog.FRAGMENT_DIALOG)
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), AppDialog.REQUEST_CAMERA_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AppDialog.REQUEST_CAMERA_PERMISSION) {
            if (grantResults.size != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                AppDialog.ErrorDialog.newInstance(getString(R.string.request_permission))
                        .show(childFragmentManager, AppDialog.FRAGMENT_DIALOG)
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }


    //=======================ImageSaver===========(ripped from camera2basic)
    class ImageSaver(
            /**
             * The JPEG image
             */
            private val image: Image,

            /**
             * The file we save the image into.
             */
            private val file: File
    ) : Runnable {

        override fun run() {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            var output: FileOutputStream? = null
            try {
                output = FileOutputStream(file).apply {
                    write(bytes)
                }
            } catch (e: IOException) {
                Log.e(TAG, e.toString())
            } finally {
                image.close()
                output?.let {
                    try {
                        it.close()
                    } catch (e: IOException) {
                        Log.e(TAG, e.toString())
                    }
                }
            }
        }
    }
}