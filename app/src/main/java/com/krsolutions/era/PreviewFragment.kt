package com.krsolutions.era

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
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
import java.util.*
import android.util.DisplayMetrics
import android.util.Size
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.net.toUri
import androidx.fragment.app.FragmentManager
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions
import java.io.BufferedOutputStream
import java.lang.Exception
import java.nio.ByteBuffer


class PreviewFragment: Fragment(),ActivityCompat.OnRequestPermissionsResultCallback {


    private lateinit var mFile: File;
    lateinit var sizes: Array<Size>
    var count_push =0;
    private val MAX_HEIGHT = 1440;
    private val MAX_WIDTH = 1080;
    private val photo_height=1920;
    private val photo_width=1080;
    private var imageReader = ImageReader.newInstance(photo_width, photo_height, ImageFormat.JPEG, 30)
    private lateinit var captureSession: CameraCaptureSession;
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    val cameraManager by lazy { activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager };
    private lateinit var cameraDevice: CameraDevice;
    val highAccuracyOpts = FirebaseVisionFaceDetectorOptions.Builder()
            .setPerformanceMode(FirebaseVisionFaceDetectorOptions.ACCURATE)
            .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
            .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
            .build()
    val realTimeOpts = FirebaseVisionFaceDetectorOptions.Builder()
            .setContourMode(FirebaseVisionFaceDetectorOptions.ALL_CONTOURS)
            .build()
    val detector = FirebaseVision.getInstance().getVisionFaceDetector(realTimeOpts)

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
//        Log.d("Img", "onImageAvailable")
//        var image=reader.acquireNextImage()
        val image = reader.acquireLatestImage()
//        if(image!=null&&count_push<30){
        if(image!=null&&!face_detected){
                count_push++
            Log.d(TAG,"request_number ${count_push}")
            backgroundHandler.post(ImageSaver(image, mFile,detector,context!!,count_push,view,fragmentManager!!))
        }else if(image!=null){
            image.close()
        }
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
//        getSizes(lens)
//        Log.d(TAG,"==========Sizes:\n"+sizes.toString())
        try {
            return deviceID[0];
        }catch (e: IndexOutOfBoundsException){
            AppDialog.ErrorDialog.newInstance("Can't connect to Camera, please restart your device.").show(fragmentManager,"ConnectionError")
            return "not found"
        }
    }

    fun getSizes(lens: Int){
        val map = getCameraCharacteristics(cameraID(lens),CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        sizes= map.getOutputSizes(SurfaceTexture::class.java)
    }

    fun expandTextureView(){
        val aspect=0.75f
        val metrics = DisplayMetrics()
        activity!!.windowManager!!.defaultDisplay!!.getMetrics(metrics)
        var screenWidth=metrics.widthPixels
        var screenHeight=metrics.heightPixels
        var finalWidth=screenWidth
        var finalHeight=screenHeight
        var heightDiff:Int=0
        var widthDiff:Int=0
        val screenAspectRatio=(screenWidth)/screenHeight
        if(screenAspectRatio>=aspect) {
            finalHeight = (screenWidth / aspect) as Int
            heightDiff = finalHeight - screenHeight
        }else{
            finalWidth=(screenHeight*aspect).toInt()
            widthDiff=finalWidth - screenWidth
        }
        var params:FrameLayout.LayoutParams=view!!.layoutParams as FrameLayout.LayoutParams
        params.height=finalHeight
        params.width=finalWidth
        params.leftMargin=-(widthDiff/2)
        params.topMargin=-(heightDiff/2)
        view!!.layoutParams.apply { params }
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
        private val TAG = PreviewFragment::class.qualifiedName
        fun newInstance() = PreviewFragment()
        var face_detected= false;
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_preview, container, false);
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState);
        mFile= File(activity!!.applicationContext!!.getExternalFilesDir(null),"cache");
        if(!mFile.exists()){
            Log.d(TAG,"file saved")
            mFile.mkdirs()
            Log.d(TAG,mFile.absolutePath)
        }
        mFile = File(mFile,"sdskjdkjsdk")
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
            expandTextureView()
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
            private val file: File,

            private val detector: FirebaseVisionFaceDetector,
            private val context:Context,
            private val count:Int,
            private val view: View?,
            private val fragmentManager: FragmentManager
    ) : Runnable {

        override fun run() {
            val buffer = image.planes[0].buffer
            var bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val bitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.size)
            var matrix = Matrix()
            when(270){
                (90)->matrix.postRotate(90f)
                (180)->matrix.postRotate(180f)
                (270)->matrix.postRotate(270f)
            }
            try{
                var rotatedBitmap = Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
                var output= FileOutputStream(file)
                val size=rotatedBitmap.rowBytes*rotatedBitmap.height
                var bufferedOutputStream = BufferedOutputStream(output,size)
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG,100,bufferedOutputStream)
                bufferedOutputStream.flush()
                bufferedOutputStream.close()
                output.close()
                var byteBuffer = ByteBuffer.allocate(size)
                rotatedBitmap.copyPixelsToBuffer(byteBuffer)
                val image = FirebaseVisionImage.fromFilePath(context,file.toUri())
                detector.detectInImage(image)
                        .addOnSuccessListener { faces->
                            if(faces.size>0&&!face_detected){
                                Toast.makeText(context,"Shakal dikh rahi hai",Toast.LENGTH_SHORT).show()
                                face_detected=true
                                AppDialog.ErrorDialog.newInstance("Face Detected").show(fragmentManager,"FACE_DETECTED")
                            }
                            Log.d(TAG,"========No. of faces detected:\t+${faces.size} on request number ${count}")
                        }
//                try {
//                    output = FileOutputStream(file).apply {
//                        write(bytes)
//                    }
//                    //Firebase Facedetection
////                    val metadata = FirebaseVisionImageMetadata.Builder()
////                            .setWidth(1080) // 480x360 is typically sufficient for
////                            .setHeight(1920) // image recognition
////                            .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_YV12)
////                            .setRotation(FirebaseVisionImageMetadata.ROTATION_90)
////                            .build()
////                    Log.d(TAG,"\nWidth: ${image.width}\tHeight: ${image.height}")
////                val detect_image = FirebaseVisionImage.fromFilePath(context,file.toUri())
////                val detect_image = FirebaseVisionImage.fromByteArray(bytes,metadata)
////                var detect_image = FirebaseVisionImage.fromMediaImage(image,FirebaseVisionImageMetadata.ROTATION_270)
//////                try{
////                detector.detectInImage(detect_image)
////                        .addOnSuccessListener {faces->
////                            Log.d(TAG,faces.size.toString())
//////                            try{if(faces.isEmpty()){
////////                                view.findViewById<TextView>(R.id.message).setText("Position Your Face Inside The Oval")
//////                            }else{
//////                                Toast.makeText(context,"face detected", Toast.LENGTH_SHORT).show()
//////                            }}catch (e: Exception){
//////                                Log.e(TAG,e.toString())
//////                            }
////                        }
////                }catch (e: Exception){
////                    Log.e(TAG,e.toString())
////                }
//                } catch (e: IOException) {
//                    Log.e(TAG, e.toString())
//                } finally {
////                    image.close()
//                    output?.let {
//                        try {
//                            it.close()
//                        } catch (e: IOException) {
//                            Log.e(TAG, e.toString())
//                        }
//                    }
//                }
            }catch(e: Exception){
                e.printStackTrace()
            }finally {
                image.close()
            }

        }
    }


    fun rotateBitmap(bitmap: Bitmap,degree:Int):Bitmap?{
        var matrix = Matrix()
        when(degree){
            (90)->matrix.setRotate(90f)
            (180)->matrix.setRotate(180f)
            (270)->matrix.setRotate(270f)
        }
        try{
            var rotatedBitmap = Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
            bitmap.recycle()
            return rotatedBitmap
        }catch(e: Exception){
            e.printStackTrace()
            return null
        }
    }
}