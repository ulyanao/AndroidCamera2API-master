package edmt.dev.androidcamera2api;
//Test for pc syncing

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import static java.lang.Math.abs;

public class MainActivity extends AppCompatActivity {

    private TextureView textureView;

    //Check state orientation of output image
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static{
        ORIENTATIONS.append(Surface.ROTATION_0,90);
        ORIENTATIONS.append(Surface.ROTATION_90,0);
        ORIENTATIONS.append(Surface.ROTATION_180,270);
        ORIENTATIONS.append(Surface.ROTATION_270,180);
    }

    //Camera variables
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSessions;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private int width;
    private int height;

    //Save to FILE
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private boolean mFlashSupported;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private final ImageData imageData = new ImageData();
    public boolean recordingData;



    //Manual camera settings
    private Long expUpper;
    private Long expLower;
    private Integer senUpper;
    private Integer senLower;
    private Long fraUpper;
    private Long fraLower;

    //Callback of camera device
    CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            setUpCamera();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            cameraDevice.close();
            cameraDevice=null;
        }
    };

    //Listener for texture surface
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };
    //</editor-fold>

    //<editor-fold desc="Activity creator">
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textureView = (TextureView)findViewById(R.id.textureView);
        //From Java 1.4 , you can use keyword 'assert' to check expression true or false
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);
        Button btnCapture = (Button) findViewById(R.id.btnCapture);
        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recordingData = !recordingData;
            }
        });
    }
    //</editor-fold>

    //<editor-fold desc="Main methods">
    /**
     * Creates the camera preview
     */
    private void createCameraPreview() {
        try{
            //This is to create the surface of the preview texture field
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert  texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(),imageDimension.getHeight());
            //The surface of the preview texture
            Surface surface = new Surface(texture);
            //This is to create the surface to capture the image to the reader
            Surface imageSurface = imageReader.getSurface();

            List<Surface> outputSurface = new ArrayList<>(2);
            outputSurface.add(imageSurface);
            outputSurface.add(surface);

            //Set up the capture Builder with settings
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF);
            captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, expLower);
            captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,senUpper);
            captureRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION,fraUpper);

            //Add target to Builder - both the texture field and the reader
            captureRequestBuilder.addTarget(surface);
            captureRequestBuilder.addTarget(imageSurface);

            cameraDevice.createCaptureSession(outputSurface, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if(cameraDevice == null)
                        return;
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Changed", Toast.LENGTH_SHORT).show();
                }
            },null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Updates the camera preview, or more it starts capturing the frames and paths them to the surfaces of the session
     */
    private void updatePreview() {
        if(cameraDevice == null)
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
        //Doesn't do anything,don't know why but I took it out, just to be sure, as it should not set the mode back to auto
        //captureRequestBuilder.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
        try{
            //This says that it should repeatedly capture frames with the settings set
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(),null,mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    /**
     * Opens the camera, first initialization
     */
    private void openCamera() {
        CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try{
            //Get Camera ID
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);


            //Stuff with permission and things I don't understand
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            //Check real time permission if run higher API 23
            if(ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            {
                ActivityCompat.requestPermissions(this,new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                },REQUEST_CAMERA_PERMISSION);
                return;
            }

            //This calls the setUpCamera method to set up the camera parameters
            manager.openCamera(cameraId,stateCallback,null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCamera() {
        CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try{
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap configs = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            //Set size of image reader
            Size[] yuvSizes = null;
            if(characteristics != null)
                yuvSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        .getOutputSizes(ImageFormat.YUV_420_888);

            //Capture image with custom size
            width = 640;
            height = 480;
            //Size is from 0(biggest) to length-1(smallest)
            if(yuvSizes != null && yuvSizes.length > 0)
            {
                width = yuvSizes[0].getWidth();
                height = yuvSizes[0].getHeight();
            }
            //Set up image reader with custom size and format
            imageReader = ImageReader.newInstance(width,height,ImageFormat.YUV_420_888,1);


            //<editor-fold desc="Listener of image reader">
            final ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                //If image is passed to surface by capturing, the image is available in th reader and this method is called
                @Override
                public void onImageAvailable(ImageReader imageReader) {
                    //Get image from image reader
                    Image image = imageReader.acquireNextImage();

                    if (recordingData) {

                        //Create image yuv out of planes
                        Image.Plane Y = image.getPlanes()[0];

                        int Yb = Y.getBuffer().remaining();

                        //The data buffer where the data of the image is stored
                        byte[] data = new byte[Yb];

                        //Add data to buffer
                        Y.getBuffer().get(data, 0, Yb);

                        //Start thread to process image data
                        ThreadImageProcessing threadImageProcessing = new ThreadImageProcessing(data, image.getHeight(),image.getWidth());
                        threadImageProcessing.start();
                    }
                    image.close();


                }

            };
            //</editor-fold>

            //Image reader is set to image reader listener
            imageReader.setOnImageAvailableListener(readerListener,mBackgroundHandler);

            //Sets the manual exposure values
            Range expRange  = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            expUpper = (Long) expRange.getUpper();
            expLower = (Long) expRange.getLower();

            Range senRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            senUpper = (Integer) senRange.getUpper();
            senLower = (Integer) senRange.getLower();

            expLower = (Long) (long) (1000000000/16000);
            senUpper = (Integer) (int) 100;
            fraUpper = (Long) (long) 1000000000/30;


            createCameraPreview();

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private void saveYData(int[] data, int currentImage) throws IOException{
        //set up the file path
        File file = new File(Environment.getExternalStorageDirectory()+"/yuv/picture_"+width+"_"+height+"_"+currentImage+"+_YData.txt");
        //Stream of text file
        DataOutputStream dataOutputStream = null;
        try{
            dataOutputStream = new DataOutputStream(new FileOutputStream(file));

            for(int n=0; n<(height);n++) {
                dataOutputStream.writeBytes(Integer.toString(data[n])+"\n");
            }
            dataOutputStream.writeBytes("The length of the data int: "+Integer.toString(data.length));


        }finally {
            if(dataOutputStream != null)
                dataOutputStream.close();
        }

    }

    //</editor-fold>

    //<editor-fold desc="Sub methods">
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
       if(requestCode == REQUEST_CAMERA_PERMISSION)
       {
           if(grantResults[0] != PackageManager.PERMISSION_GRANTED)
           {
               Toast.makeText(this, "You can't use camera without permission", Toast.LENGTH_SHORT).show();
                finish();
           }
       }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if(textureView.isAvailable())
            openCamera();
        else
            textureView.setSurfaceTextureListener(textureListener);
    }

    @Override
    protected void onPause() {
        stopBackgroundThread();
        super.onPause();
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try{
            mBackgroundThread.join();
            mBackgroundThread= null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    //</editor-fold>

    public class ThreadSaveData extends Thread {


        public void run() {
            Log.d("Image","The saving Thread is started: "+Thread.currentThread().getName());
            synchronized (imageData) {
                Log.d("Image","The saving Thread accesses the data: "+Thread.currentThread().getName());
                try {

                    for(int n=0; n<imageData.dataY.size(); n++) {

                        saveYData(imageData.dataY.get(n),n);

                    }
                    imageData.dataY.clear();
                    //imageData.lastFrameCaptured=false;
                    Log.d("Image","The Thread is active: "+Thread.currentThread().getName()+"  That many active: " + Thread.activeCount());
                    Log.d("Image","The saving Thread has ended: "+Thread.currentThread().getName());

                    //Toast.makeText(MainActivity.this, "Saved the images!", Toast.LENGTH_SHORT).show();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public class ThreadImageProcessing extends Thread {

        byte[] data;
        int imageWidth;
        int imageHeight;

        ThreadImageProcessing(byte[] data, int imageHeight, int imageWidth) {
            this.data = data;
            this.imageHeight = imageHeight;
            this.imageWidth = imageWidth;
        }

        public void run() {

            Log.d("Image","New Thread started: "+Thread.currentThread().getName());
            //<editor-fold desc="Step1: get image data">

            /*
            //Create image yuv out of planes
            Image.Plane Y = image.getPlanes()[0];

            int Yb = Y.getBuffer().remaining();

            //The data buffer where the data of the image is stored
            byte[] data = new byte[Yb];

            //Add data to buffer
            Y.getBuffer().get(data, 0, Yb);
            */
            //</editor-fold>

            //<editor-fold desc="Step2: translate to 255">
            int dataLength = imageHeight*imageWidth;
            int[] data255 = new int[dataLength];

            for(int i=0;i<dataLength;i++) {
                if(data[i] < 0) {
                    data255[i] = (127 + abs(data[i]));
                }else{
                    data255[i] = data[i];
                }
            }
            //</editor-fold>

            //<editor-fold desc="Step3: data1Dim">
            int[] data1Dim = new int[imageHeight];

            long sumOfLine = 0;
            int counterLines = -1;

            for(int i=0;i<dataLength;i++) {
                sumOfLine += data255[i];
                if((i+1)%imageWidth==0) {
                    counterLines++;
                    data1Dim[counterLines] = (int) (sumOfLine/imageWidth);
                    sumOfLine = 0;
                }
            }
            //</editor-fold>

            Log.d("Image","The Thread is active: "+Thread.currentThread().getName()+"  That many active: " + Thread.activeCount());
            Log.d("Image","Thread processed picture: "+Thread.currentThread().getName());


            synchronized (imageData) {
                if (!imageData.lastFrameCaptured) {
                    Log.d("Image","Thread saves data: "+Thread.currentThread().getName());
                    imageData.dataY.add(data1Dim);
                    if(imageData.dataY.size() >= 1) {
                        Log.d("Image","Thread is starting new Thread to save everything: "+Thread.currentThread().getName());
                        imageData.lastFrameCaptured = true;
                        recordingData = false;
                        //New Thread to handle saving
                        ThreadSaveData threadSaveData = new ThreadSaveData();
                        threadSaveData.start();
                    }
                } else {
                    Log.d("Image","Thread didnt save data, as after saving was done: "+Thread.currentThread().getName());
                }
            }


        }

    }

    public class ImageData {
        public List<int[]> dataY = new ArrayList<>();
        public boolean lastFrameCaptured;

        ImageData() {
            lastFrameCaptured = false;
        }

    }
}
