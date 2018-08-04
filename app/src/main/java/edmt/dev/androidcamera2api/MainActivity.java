package edmt.dev.androidcamera2api;

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
import android.hardware.camera2.CaptureRequest;
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
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    //<editor-fold desc="Declaration">
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

    //Image processing
    private final ImageData imageData = new ImageData();
    public boolean recordingData;
    long startTime;
    long endTime;
    long middleTime =330;
    short test = 0;


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
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Image saving has started!", Toast.LENGTH_SHORT).show();
                    }
                });
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
            recordingData = false;

            //<editor-fold desc="Listener of image reader">
            final ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                //If image is passed to surface by capturing, the image is available in the reader and this method is called
                @Override
                public void onImageAvailable(ImageReader imageReader) {
                    //Get image from image reader
                    Image image = imageReader.acquireNextImage();

                    if (recordingData) {
                        startTime = System.nanoTime();
                        //Set up the data which stores the data of the image plane
                        byte[] data = new byte[image.getWidth()*image.getHeight()];
                        //Get y plane of image and path buffer to data
                        image.getPlanes()[0].getBuffer().get(data);

                        try {
                            ThreadManager.getInstance().getmDecoderThreadPool().execute(new RunnableImage(data.clone(),test, image.getHeight(), image.getWidth()));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        test++;

                        endTime = System.nanoTime();
                        middleTime=(middleTime+(endTime-startTime)/100000)/2;

                    } else if(test==-1){
                        Log.d("Image", "End and time in middle: " + middleTime);
                        test=101;
                    }
                    image.close();
                }

            };
            //</editor-fold>

            //Image reader is set to image reader listener
            imageReader.setOnImageAvailableListener(readerListener,mBackgroundHandler);

            //Sets the manual exposure values
            /*
            Range expRange  = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            expUpper = (Long) expRange.getUpper();
            expLower = (Long) expRange.getLower();

            Range senRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            senUpper = (Integer) senRange.getUpper();
            senLower = (Integer) senRange.getLower();
            */

            expLower = (Long) (long) (1000000000/16000);    //22000 to 100000000
            senUpper = (Integer) (int) 100;               //64 to 1600 //but higher somehow possible
            fraUpper = (Long) (long) 1000000000/30;


            createCameraPreview();

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }
    private void saveYData(short[] data, int currentImage) throws IOException{
        //set up the file path
        File file = new File(Environment.getExternalStorageDirectory()+"/yuv/picture_"+width+"_"+height+"_"+currentImage+"_YData.csv");
        //Stream of text file
        FileWriter fileWriter = null;
        try{
            fileWriter = new FileWriter(file);

            for(int n=0; n<(height);n++) {
                fileWriter.write(Integer.toString(n+1)+", ");
                fileWriter.write(Integer.toString(data[n])+"\n");
            }

        }finally {
            if(fileWriter != null)
                fileWriter.close();
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
        mBackgroundThread.setPriority(Thread.MAX_PRIORITY);
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    //</editor-fold>

    //<editor-fold desc="Threads">
    private class ThreadSaveData extends Thread {


        public void run() {
            try {
                Thread.currentThread().sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Log.d("Image","The saving Thread is started: "+Thread.currentThread().getName());
            synchronized (imageData) {
                Log.d("Image","The saving Thread accesses the data: "+Thread.currentThread().getName());
                try {

                    for(int n=0; n<imageData.dataY.size(); n++) {

                        saveYData(imageData.dataY.get(n),n);

                    }
                    imageData.dataY.clear();
                    imageData.lastFrameCaptured=false;
                    Log.d("Image","The saving Thread has ended: "+Thread.currentThread().getName());

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Saved the images!", Toast.LENGTH_SHORT).show();
                        }
                    });

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public class ImageData {
        public List<short[]> dataY = new ArrayList<>();
        public boolean lastFrameCaptured;

        ImageData() {
            lastFrameCaptured = false;
        }

    }

    private class RunnableImage implements Runnable {
        //Initialization
        byte[] data;
        short test;
        int imageHeight;
        int imageWidth;

        RunnableImage(byte[] data, short test, int imageHeight, int imageWidth) {
            this.data = data;
            this.test = test;
            this.imageHeight = imageHeight;
            this.imageWidth = imageWidth;
        }

        @Override
        public void run() {
            Log.d("Image","New: "+Thread.currentThread().getName());

            short[] data1Dim = new short[imageHeight];
            short counterLines = -1;
            int sumOfLine = 0;
            for(int i=0;i<imageHeight * imageWidth;i++) {
                sumOfLine += (data[i] & 0xff);
                if((i+1)%imageWidth==0) {
                    counterLines++;
                    data1Dim[counterLines] = (short) (sumOfLine/imageWidth);
                    sumOfLine = 0;
                }
            }

            //Final byte array

            data = new byte[12];         //the byte to capture
            byte counterData = 0;       //position counter in byte array
            boolean lastHigh = false;   //cares about possible that one low and high again to stay in a row
            short counterHigh=0;    //counts how many highs in a row
            short endHigh = -1;     //saves end pixel of a high
            short startHigh = 0;    //saves start pixel of a high
            byte lastBit = -1; //-1 nothing, 0 zero last, 1 one last, 2 start bit
            boolean error = false;
            for (int i = 0; i<imageHeight; i++) {
                if(error) {
                    error = false;
                    data = new byte[12];
                    counterData = 0;
                    lastBit = -1;
                }
                if(data1Dim[i]>=70) {   //high point recognized
                    lastHigh = true;
                    counterHigh++;
                } else if(lastHigh) {   //this low but last was high
                    lastHigh=false;
                    counterHigh++;
                } else if(counterHigh != 0) {   //two times low after some highs
                    counterHigh--;  //counter adjust two last high pixel
                    if(36<=counterHigh && counterHigh<=40) {    //check if high was startBit without low parts
                        lastBit = 2;
                        endHigh = (short) (i - 2);
                    } else if(13<=counterHigh && counterHigh<=23) { //check if it was a normal high
                        startHigh = (short) (i - 1 - counterHigh);  //set new start of this normal high
                        //Only if start bit called
                        if(endHigh!=-1) {   //only do more if it was not the first high
                            if(2 <= startHigh-endHigh && startHigh-endHigh <= 8) {  //check if two start highs
                                //start bit
                                lastBit = 2;
                            } else if (lastBit!=-1) {                               //Check if start bit called ones
                                if(10 <= startHigh-endHigh && startHigh-endHigh <= 20){  //check if 0.2 in between to highs
                                    //0,2
                                    if(lastBit == 2 || lastBit == 0) {
                                        //its a 1
                                        data[counterData] = 1;
                                        counterData++;
                                        lastBit = 1;
                                    } else {
                                        //error not possible to have this bit followed by this lows
                                        error = true;
                                        Log.d("DataTest", "Error last Bit at 0.2; and at pixel: "+i);
                                    }
                                } else if(29 <= startHigh-endHigh && startHigh-endHigh <= 40){  //check if 0.4 in between to highs
                                    //0,4
                                    if(lastBit == 2 || lastBit == 0) {
                                        //its a 0
                                        data[counterData] = 0;
                                        counterData++;
                                        lastBit = 0;
                                    } else {
                                        //its a 1
                                        data[counterData] = 1;
                                        counterData++;
                                        lastBit = 1;
                                    }
                                } else if(44 <= startHigh-endHigh && startHigh-endHigh <= 60){  //check if 0.6 in between to highs
                                    //0,6
                                    if(lastBit == 1) {
                                        //its a 0
                                        data[counterData] = 0;
                                        counterData++;
                                        lastBit = 0;
                                    } else {
                                        //error
                                        Log.d("DataTest", "Error last Bit at 0.6; and at pixel: "+i);
                                        error = true;
                                    }
                                } else {    //some else number of lows in between two highs => sequence is interrupted
                                    // error
                                    Log.d("DataTest", "Error strange number of lows; and at pixel: "+i);
                                    error = true;
                                }

                                if(counterData==12) {
                                    //end of byte save it and decode it with 4Bit 6Bit
                                    Log.d("DataTest", "YEAHHH:  The data is: "+data[0]+data[1]+data[2]+data[3]+data[4]+data[5]+data[6]+data[7]+data[8]+data[9]+data[10]+data[11]);
                                    //than reset to go new
                                    error = true;
                                }
                            }
                        }
                        endHigh = (short) (i - 2);  // a normal high and was processed and now set the end
                    } else if(counterHigh>=13){
                        //1. error as sequence is interrupted - too many high values
                        Log.d("DataTest", "Error to many high; highs: "+counterHigh+"; and at pixel: "+i);
                        error = true;
                        endHigh = -1;   //not a normal high so set back last high value
                    }
                        //2. just some strange highs (small ones maybe only) in between highs does not matter

                    //after end of high processed go to 0 again
                    counterHigh = 0;
                }
                //if no high has been - nothing happens in loop and go further in data
            }


            synchronized (imageData) {
                if (!imageData.lastFrameCaptured) {
                    Log.d("Image","Thread processed picture: "+Thread.currentThread().getName() +";  And it was the frame: "+test);
                    imageData.dataY.add(data1Dim);
                    if(imageData.dataY.size() >= 1) {
                        Log.d("Image","Thread is starting new Thread to save everything: "+Thread.currentThread().getName());
                        recordingData = false;
                        imageData.lastFrameCaptured = true;
                        //New Thread to handle saving
                        ThreadSaveData threadSaveData = new ThreadSaveData();
                        threadSaveData.start();

                    }
                } else {
                    Log.d("Image","Thread didn't save data, as after saving was done: "+Thread.currentThread().getName());
                }
            }
        }
    }
    //</editor-fold>
}