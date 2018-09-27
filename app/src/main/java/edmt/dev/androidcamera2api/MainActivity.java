package edmt.dev.androidcamera2api;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    //<editor-fold desc="Declaration">
    private TextureView textureView;
    private Button btnCapture;

    private final int BUTTON_COLOR_ON = Color.RED;
    private final int BUTTON_COLOR_OFF = Color.WHITE;

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

    //Save to FILE
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    //Image processing
    private final ImageData imageData = new ImageData();
    public boolean recordingData;
    //through and good put
    private long startTimePut;
    private long throughPut;
    private long goodPut;
    private int counterPut = 0;
    //Middle time
    private long startTimeMiddle;
    private long middleTime = 0;
    private long framesMiddleTime = 0;



    //Manual camera settings
    private Long expUpper;
    private Long expLower;
    private Integer senUpper;
    private Integer senLower;
    private Long fraUpper;
    private Long fraLower;

    //Intent
    public static final String EXTRA_MESSAGE = "com.example.androidCamera2API-master.MESSAGE";

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
        //Get Button object
        btnCapture = (Button) findViewById(R.id.btnCapture);
        //Set up listener and handler of button click
        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                btnCapture.setClickable(false);     //if clicked disable until proceeded
                recordingData = !recordingData;     //first disable recording data to stop capturing frames
                synchronized (imageData) {          //second if have been recording, stop frames from processing more data; all thread save
                    if (!recordingData) {
                        imageData.lastFrameCaptured = true;
                    }
                }
                //Now distinguish between start and stopped
                if(recordingData) {
                    startTimePut = System.nanoTime();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() { //print out start message
                            Toast.makeText(MainActivity.this, "Image recording has started!", Toast.LENGTH_SHORT).show();
                        }
                    });
                    btnCapture.setBackgroundColor(BUTTON_COLOR_ON); //change color
                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Image recording has stopped!", Toast.LENGTH_SHORT).show();
                        }
                    });
                    btnCapture.setBackgroundColor(BUTTON_COLOR_OFF);
                    while(ThreadManager.getInstance().getmDecoderThreadPool().getActiveCount() != 0) {      //care about sill executing threads, wait until all done
                        Log.d("Threads","Waiting for threads to finish, before resetting to capture available again; active threads: "+ThreadManager.getInstance().getmDecoderThreadPool().getActiveCount());
                    }
                    synchronized (imageData) {  //if all done clear all saved stuff; thread save
                        imageData.dataTest.clear();
                        imageData.dataStream.clear();
                        imageData.lastFrameCaptured=false;
                        imageData.dataCheck = 0;
                        framesMiddleTime =0;
                        middleTime = 0;
                        counterPut = 0;
                        imageData.counterErrorRate = 0;
                    }
                }
                btnCapture.setClickable(true);  //let the user click again
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
            int width = 640;
            int height = 480;
            //Size is from 0(biggest) to length-1(smallest)
            if(yuvSizes != null && yuvSizes.length > 0)
            {
                width = yuvSizes[0].getWidth();
                height = yuvSizes[0].getHeight();
            }
            //Set up image reader with custom size and format, image buffer set to 5, recommended for vlc
            imageReader = ImageReader.newInstance(width, height,ImageFormat.YUV_420_888,5);
            recordingData = false;

            //<editor-fold desc="Listener of image reader">
            final ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                //If image is passed to surface by capturing, the image is available in the reader and this method is called
                @Override
                public void onImageAvailable(ImageReader imageReader) {
                    startTimeMiddle=System.nanoTime();
                    //Get image from image reader
                    Image image = imageReader.acquireNextImage();

                    if (recordingData) {    //check variable if button pressed to start recording
                        framesMiddleTime++;
                        //Set up the data which stores the data of the image plane
                        byte[] data = new byte[image.getWidth() * image.getHeight()];   //get byte to save image data
                        //Get y plane of image and path buffer to data
                        image.getPlanes()[0].getBuffer().get(data); //get data out of image
                        image.close();
                        try {
                            ThreadManager.getInstance().getmDecoderThreadPool().execute(new RunnableImage(data.clone()));   //start thread to proceed the data
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        middleTime = middleTime + (System.nanoTime()-startTimeMiddle)/1000000;

                    }else{
                        image.close();
                    }
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

            expLower = (Long) (long) (1000000000/8000);    //22000 to 100000000
            senUpper = (Integer) (int) 10000;               //64 to 1600 //but higher somehow possible
            fraUpper = (Long) (long) 1000000000/30;


            createCameraPreview();

        } catch (CameraAccessException e) {
            e.printStackTrace();
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
            while(ThreadManager.getInstance().getmDecoderThreadPool().getActiveCount() != 0) {      //wait until all threads are finished
                Log.d("Threads","Waiting for threads to finish, before saving; active threads: "+ThreadManager.getInstance().getmDecoderThreadPool().getActiveCount());
            }
            //set up new activity to display output
            Intent intent = new Intent(MainActivity.this, DisplayMessageActivity.class);
            //get all data to the message string
            String message = "";

            //Now get all the data out of imageData
            synchronized (imageData) {  //be thread save, double secure
                try {
                    for(int i = 0; i<imageData.dataTest.size(); i++) {  //loop through image data test, and do what want to do
                        //set up the file path
                        File file = new File(Environment.getExternalStorageDirectory()+"/yuv/E"+expLower+"_S"+senUpper+"_H"+imageData.dataTest.get(i).length+".csv");
                        //Stream of text file
                        FileWriter fileWriter = null;
                        try{
                            fileWriter = new FileWriter(file);

                            for(int n = 0; n<(imageData.dataTest.get(i).length); n++) {
                                fileWriter.write(Integer.toString(imageData.dataTest.get(i)[n])+"\n");
                            }
                        }finally {
                            if(fileWriter != null)
                                fileWriter.close();
                        }

                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //Set up the times:
                middleTime=middleTime/framesMiddleTime;


                //Set up the message for new activity out of imageData
                for(int i=0; i<imageData.dataStream.size();i++) {
                    message = message + String.valueOf((char) (byte) imageData.dataStream.get(i));  //get decoded bytes out of dataStream
                }
                message=message+"; through: " + throughPut + "; good: " + goodPut + "; time: " + middleTime;
                intent.putExtra(EXTRA_MESSAGE,message);

                //Reset the data for next recording
                imageData.dataTest.clear();
                imageData.dataStream.clear();
                imageData.lastFrameCaptured=false;
                imageData.dataCheck = 0;
                imageData.counterErrorRate = 0;
                framesMiddleTime =0;
                middleTime=0;
                counterPut = 0;
            }

            //Now Reset Button and output message
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    btnCapture.setClickable(true);
                }
            });
            //Start the display activity
            startActivity(intent);
        }
    }

    public class ImageData {
        public List<int[]> dataTest = new ArrayList<>();
        public List<Byte> dataStream = new ArrayList<>();
        public boolean lastFrameCaptured;
        public int dataCheck;
        public int counterErrorRate;

        ImageData() {
            lastFrameCaptured = false;
            dataCheck = 0;
            counterErrorRate = 0;
        }

    }

    private class RunnableImage implements Runnable {
        //Initialization
        byte[] data;

        RunnableImage(byte[] data) {
            this.data = data;
        }

        @Override
        public void run() {
            //Initialization
            byte[] dataPlanes = this.data;
            int width = 4032;
            int height = 3024;

            //<editor-fold desc="ROI">
            //Variables
            int upROI;     //data array starts top right corner and first columns than rows, ends left bottom
            int lowROI = -1;    //0 is first position, 1 is second
            int borderROIBuffer = -1;
            int highestInRow = 0;
            int lowestInRow = 250;
            int byteToIntBuffer;
            int counterInterval = 0;
            int counterStripes = 0;
            int counterStripesHighest = 0;
            int mostStripes = 0;
            //Constants
            int STEP_ROI_ROW = 25;
            int STEP_ROI_PIXEL = 8;         //min low is 8
            int DISTINGUISH_VALUE = 50;     //from 0 to 255
            int INTERVAL_OF_STRIPES = 65;   //in pixels, 70 as longest time without change is 0.6 low with around these pixels
            int RANGE_AROUND_MOST_STRIPES = 20;
            int COUNT_OF_STRIPES = 8;  //depends on bits per sequence, at least a sequence per row; COUNT_OF_STRIPES dark/bright stripes per row

            //<editor-fold desc="ROI Detection">
            //Loops
            for(int i=0; i<width; i=i+STEP_ROI_ROW) {   //A column
                //i is offset of Row
                for(int n=0;n<height; n=n+STEP_ROI_PIXEL) { //A line
                    // n*width + i is pixel
                    byteToIntBuffer = (dataPlanes[i+n*width] & 0xff);
                    counterInterval++;
                    if(byteToIntBuffer>highestInRow) {
                        highestInRow = byteToIntBuffer;
                    }
                    if(byteToIntBuffer<lowestInRow) {
                        lowestInRow = byteToIntBuffer;
                    }
                    if(highestInRow-lowestInRow > DISTINGUISH_VALUE) {  //Check if bright an dark stripes can be distinguished
                        borderROIBuffer = i+n*width;
                        counterStripes++;       //counter++ stripes

                        //Reset interval values to start new interval
                        highestInRow = 0;
                        lowestInRow = 250;
                        counterInterval = 0;
                    }
                    //Check the interval
                    if(counterInterval>=INTERVAL_OF_STRIPES/STEP_ROI_PIXEL) {   //Check if interval ended
                        if(counterStripesHighest<counterStripes) {
                            counterStripesHighest=counterStripes;
                        }
                        //Reset interval values if ended
                        counterStripes = 0;
                        highestInRow = 0;
                        lowestInRow = 250;
                        counterInterval = 0;
                    }
                }
                //Stuff before next Row starts
                if (mostStripes<counterStripesHighest) { //check if most stripes in this line
                    mostStripes=counterStripesHighest;
                    //Set the left and low ROI Border
                    lowROI = borderROIBuffer%width;
                }
                //Reset highest and lowest and reset row
                highestInRow = 0;
                lowestInRow = 250;
                counterInterval = 0;
                counterStripes = 0;
            }
            //Set Borders
            lowROI+=RANGE_AROUND_MOST_STRIPES/2;
            upROI = lowROI-RANGE_AROUND_MOST_STRIPES;
            if(upROI<0) {
                upROI=0;
                lowROI=RANGE_AROUND_MOST_STRIPES;
            }
            if(lowROI>=width) {
                lowROI=width-1;
                upROI = lowROI-RANGE_AROUND_MOST_STRIPES;
            }
            //</editor-fold>
            //</editor-fold>

            //Check if ROI found otherwise discard frame
            if(mostStripes>=COUNT_OF_STRIPES) {
                //New dimensions of array

                //<editor-fold desc="1 dim array">
                //1 dim array by calculating mean of column
                //Variables
                int[] data1Dim = new int[height];
                int sumOfLine = 0;
                int counterSamples = 0;
                int counterLines=0;

                //Constants
                int STEP_1DIM_ROW = 1;

                //two loops to get only needed lines and columns
                for(int i=0; i<height; i++) {
                    for(int n=upROI; n<lowROI; n=n+STEP_1DIM_ROW) {
                        sumOfLine += (dataPlanes[i*width+n] & 0xff);    //save byte data as int and sum up and create mean
                        counterSamples++;
                    }
                    data1Dim[counterLines] = sumOfLine/counterSamples;  //get the mean
                    counterLines++;
                    sumOfLine = 0;
                    counterSamples = 0;
                }
                //</editor-fold>


                //<editor-fold desc="Thresholding">
                //Constants
                int THRESH_STEP = 4;    //not too big to recognize small peeks
                int DISTINGUISH_VALUE_THRESH = 20;
                double DISTINGUISH_FACTOR_THRESH = 0.3;

                //Variables
                int highestThresh = 0;
                int lowestThresh = 250;
                int lowestThreshPosition = 0;
                int lowestThreshOld = -1;
                int currentDistinguishThresh = DISTINGUISH_VALUE_THRESH;
                int currentData;
                boolean goesUp = true;
                ArrayList<Integer> threshValues = new ArrayList<>();    //where the new borders and thresh values of thresh's are saved

                for(int i=0; i<height;i+=THRESH_STEP) {  //loop of data 1 dim
                    currentData = data1Dim[i];  //buffer of current data
                    if(goesUp) {    //if goes Up is true, search for a increase of specific amount to recognize a peek
                        if(currentData<lowestThresh) {  //get lowest
                            lowestThresh=currentData;
                            lowestThreshPosition = i;
                        }
                        if(currentData>lowestThresh+currentDistinguishThresh) { //look for increase
                            if(lowestThreshOld!=-1) {   //only do if it was at least second increase
                                //now do everything to save the high
                                //save mean and borders of thresholding with the highest and lowest
                                if(lowestThresh>lowestThreshOld) {  //take higher low value, as normally better
                                    threshValues.add((lowestThresh+highestThresh)/2);
                                }else {
                                    threshValues.add((lowestThreshOld+highestThresh)/2);
                                }
                                threshValues.add(lowestThreshPosition);

                                //set the distinguish value according to the current last peek to low value
                                currentDistinguishThresh= (int) ((highestThresh-lowestThresh)* DISTINGUISH_FACTOR_THRESH);
                                if(currentDistinguishThresh<DISTINGUISH_VALUE_THRESH) {
                                    currentDistinguishThresh = DISTINGUISH_VALUE_THRESH;
                                }

                            }
                            goesUp = false;
                            lowestThreshOld = lowestThresh;
                            lowestThresh=250;
                            highestThresh=0;
                        }
                    } else {    //if goes down, looking for a low
                        if(currentData>highestThresh) { //save highest
                            highestThresh = currentData;
                        }
                        if(currentData<highestThresh-currentDistinguishThresh) {    //look if decrease is recognized
                            goesUp = true;
                            currentDistinguishThresh= (int) ((highestThresh-lowestThreshOld)* DISTINGUISH_FACTOR_THRESH);   //set new distinguish value
                            if(currentDistinguishThresh<DISTINGUISH_VALUE_THRESH) {
                                currentDistinguishThresh = DISTINGUISH_VALUE_THRESH;
                            }
                        }
                    }
                }
                //</editor-fold>

                if (threshValues.size()>=COUNT_OF_STRIPES) {
                    //Beta; will be implemented in decoding for faster processing
                    //<editor-fold desc="Downsampling">
                    //Variables
                    int counterThreshIntervals = 0;
                    int threshValueBuffer;
                    int threshIntervalPosition;
                    threshValueBuffer = 255;
                    threshIntervalPosition=threshValues.get(counterThreshIntervals+1);
                    //now loop and set 1 or 0 according to the saved thresh values
                    for(int i=0;i<height;i++) {
                        if(data1Dim[i]>threshValueBuffer){
                            data1Dim[i] = 1;
                        } else {
                            data1Dim[i] = 0;
                        }
                        //set the new thresh value if interval is exceeded,but care about ou of bound exception
                        if(i>=threshIntervalPosition && threshValues.size() > counterThreshIntervals + 2) {
                            counterThreshIntervals+=2;
                            threshValueBuffer = threshValues.get(counterThreshIntervals);
                            threshIntervalPosition = threshValues.get(counterThreshIntervals+1);
                        }
                    }
                    //</editor-fold>


                    //<editor-fold desc="Decoding algorithm">
                    //Variables
                    byte data6Bit = 0;         //The encoded data in 6bit
                    byte[] data4Bit = new byte[12];         //the byte array where to save the 4 bit data bytes decoded form the 6 bit data; 1 block number 1 byte repeated
                    byte dataByteBuffer;        //buffer
                    int bytePart = 0;           //checks if already first 6bit captured of the 12
                    int counterBytes = 0;      //counts the bytes
                    int counterBits = 0;       //counter of captured bits
                    int counterHigh=0;        //counts how many highs in a row
                    int endHigh = -1;         //saves end pixel of a high
                    int startHigh;            //saves start pixel of a high
                    int counterLow;
                    int lastBit = -1;          //-1 nothing, 0 zero last, 1 one last, 2 start bit
                    boolean error = false;
                    boolean startError = false;
                    boolean sequenceFinished = true;

                    //<editor-fold desc="Algorithm">

                    for (int i = 0; i<height; i++) {
                        if(error) {
                            error = false;  //reset error flag
                            data6Bit = 0;    //the current buffered data is reset
                            data4Bit[counterBytes] = 0; //the already saved data at this position is reset
                            if(bytePart!=0) {   //if first part of data has already been saved so not part 0 anymore
                                data4Bit[counterBytes-1] = 0;   //than reset las data
                                counterBytes--; //and change counter again
                            }
                            bytePart = 0;   //set part to zero again
                            counterBits = 0;    //counter of captured bits is reset
                            lastBit = -1;   //the last bit is not available any longer
                            if(startError) {
                                lastBit=2;  //if start high too early, process with this further
                                startError = false;
                            }

                        }

                        if(data1Dim[i]>=1) {   //high point recognized
                            counterHigh++;
                        } else if(counterHigh != 0) {   //two times low after some highs
                            if(30<=counterHigh && counterHigh<=50) {    //check if high was startBit without low parts
                                lastBit = 2;
                                endHigh = i - 1;
                                if(!sequenceFinished) {
                                    error = true;
                                    startError = true;
                                }
                                sequenceFinished=false;
                            } else if(8<=counterHigh && counterHigh<=29) { //check if it was a normal high
                                startHigh = i - counterHigh;  //set new start of this normal high
                                //Only if start bit called
                                if(endHigh!=-1) {   //only do more if it was not the first high
                                    counterLow = startHigh-endHigh-1;   //set the zeros between start and end; -1 as want to get zeros in between and not the distance
                                    if(1 <= counterLow && counterLow <= 8) {  //check if two start highs
                                        //start bit
                                        lastBit = 2;
                                        if(!sequenceFinished) {
                                            error = true;
                                            startError = true;
                                        }
                                        sequenceFinished=false;
                                    } else if (lastBit!=-1) {                               //Check if start bit called ones
                                        if(8 <= counterLow && counterLow <= 23){  //check if 0.2 in between to highs
                                            //0,2
                                            if(lastBit == 2 || lastBit == 0) {
                                                //its a 1
                                                data6Bit = (byte) ((1 << (5-counterBits) | data6Bit));
                                                counterBits++;
                                                lastBit = 1;
                                            } else {
                                                //error not possible to have this bit followed by this lows
                                                error = true;
                                                Log.d("DataTest", "Error last Bit at 0.2; and at pixel: "+i);
                                            }
                                        } else if(24 <= counterLow && counterLow <= 42){  //check if 0.4 in between to highs
                                            //0,4
                                            if(lastBit == 2 || lastBit == 0) {
                                                //its a 0
                                                counterBits++;
                                                lastBit = 0;
                                            } else {
                                                //its a 1
                                                data6Bit = (byte) ((1 << (5-counterBits) | data6Bit));
                                                counterBits++;
                                                lastBit = 1;
                                            }
                                        } else if(43 <= counterLow && counterLow <= 62){  //check if 0.6 in between to highs
                                            //0,6
                                            if(lastBit == 1) {
                                                //its a 0
                                                counterBits++;
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

                                        if(counterBits==6 && bytePart==0) {    //first 6 bit to 4bit
                                            if ((dataByteBuffer = decode4Bit6Bit(data6Bit)) != -1) {
                                                data4Bit[counterBytes] = dataByteBuffer;
                                                counterBits = 0;    //reset the counter of how many bits
                                                data6Bit = 0;    //reset the data buffer
                                                bytePart = 1;           //set to new part
                                                counterBytes++; //set counterBytes higher...
                                            } else {
                                                error = true;
                                            }
                                        } else if(counterBits==6 && bytePart == 1) {    //first 6 bit to 4bit
                                            if ((dataByteBuffer = decode4Bit6Bit(data6Bit)) != -1) {
                                                data4Bit[counterBytes] = (byte) (dataByteBuffer << 4);
                                                counterBits = 0;
                                                data6Bit = 0;
                                                bytePart = 2;
                                            } else {
                                                error = true;
                                            }
                                        } else if(counterBits == 6) { //last 6 bit to last 4 bit
                                            if ((dataByteBuffer = decode4Bit6Bit(data6Bit)) != -1) {
                                                data4Bit[counterBytes] = (byte) (dataByteBuffer | data4Bit[counterBytes]);
                                                counterBytes++; //to get new bytes of data
                                                bytePart = 0; //to care about the if case in the error handling
                                                sequenceFinished = true;
                                            }
                                            //reset to capture new byte resp. error if = -1
                                            error = true;
                                        }
                                    }
                                }
                                endHigh = i - 1;  // a normal high and was processed and now set the end
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
                    if(bytePart==2) {           //Check if sequence ended, but block byte and first part of byte are already written
                        //I have to discard both as not completed
                        data4Bit[counterBytes] = 0;
                        data4Bit[counterBytes-1] = 0;
                    }
                    //</editor-fold>

                    //</editor-fold>

                    synchronized (imageData) {
                        if (!imageData.lastFrameCaptured) { //stops still executing threads from interacting during proceeding the final message
                            for(int n=0;data4Bit[n] > 0 && data4Bit[n+1]!=0 && data4Bit[n] <= 3;n+=2) {   //check if at least one byte of frame readable, than process this byte
                                while(imageData.dataStream.size()<data4Bit[n] + imageData.counterErrorRate) {
                                    imageData.dataStream.add((byte) 0);
                                }
                                if(imageData.dataStream.get(data4Bit[n]-1+imageData.counterErrorRate) == 0) {
                                    imageData.dataStream.set(data4Bit[n]-1+imageData.counterErrorRate,data4Bit[n+1]);
                                    imageData.dataCheck+=data4Bit[n];
                                }
                                if(counterPut<102) {
                                    counterPut++;
                                }
                                if(counterPut==102) {
                                    counterPut++;
                                    throughPut = (System.nanoTime()-startTimePut)/1000000;
                                }
                                if(imageData.dataCheck == 6) {
                                    imageData.dataCheck = 0;
                                    imageData.counterErrorRate+=3;
                                }

                                if (imageData.counterErrorRate >= 100) {  //my condition to stop
                                    goodPut = (System.nanoTime()-startTimePut)/1000000;
                                    Log.d("TimeCheck", "End and time in middle: " + (middleTime)/ framesMiddleTime);
                                    //UI thread to display saving and change button status
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(MainActivity.this, "Message is captured, saving started!", Toast.LENGTH_SHORT).show();
                                            btnCapture.setClickable(false);
                                            btnCapture.setBackgroundColor(Color.WHITE);
                                        }
                                    });
                                    //Now cancel processing new frames and set last frame captured
                                    recordingData = false;  //stop recording in image reader
                                    imageData.lastFrameCaptured = true; //stop still executing threads from writing more data
                                    //For debugging
                                    imageData.dataTest.add(data1Dim);  //add data to be saved

                                    //New Thread to handle saving
                                    ThreadSaveData threadSaveData = new ThreadSaveData();
                                    threadSaveData.start();
                                    break;  //break from loop as enough bytes captured
                                }
                            }
                        }
                    }
                }
            }
        }
        private byte decode4Bit6Bit(byte dataCoded) {
            byte data = -1;
            switch (dataCoded) {
                case 14:
                    data = 0;
                    break;
                case 13:
                    data = 1;
                    break;
                case 19:
                    data = 2;
                    break;
                case 22:
                    data = 3;
                    break;
                case 21:
                    data = 4;
                    break;
                case 35:
                    data = 5;
                    break;
                case 38:
                    data = 6;
                    break;
                case 37:
                    data = 7;
                    break;
                case 25:
                    data = 8;
                    break;
                case 26:
                    data = 9;
                    break;
                case 28:
                    data = 10;
                    break;
                case 49:
                    data = 11;
                    break;
                case 50:
                    data = 12;
                    break;
                case 41:
                    data = 13;
                    break;
                case 42:
                    data = 14;
                    break;
                case 44:
                    data = 15;
                    break;
            }
            return data;    //returns -1 if error
        }
    }
    //</editor-fold>
}