//This is the main activity class, where the camera is set up and the recording and processing of the data takes place
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
import android.os.Build;
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
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.abs;

public class MainActivity extends AppCompatActivity {

    //<editor-fold desc="Declaration">
    //The user interface variables
    private TextureView textureView;
    private Button btnCapture;

    //Predefined styles for the button
    private final int BUTTON_COLOR_ON = Color.RED;
    private final int BUTTON_COLOR_OFF = Color.WHITE;
    private final String BUTTON_STRING_OFF = "Start";
    private final String BUTTON_STRING_ON = "Stop";

    //Definitions for orientation of output image
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static{
        ORIENTATIONS.append(Surface.ROTATION_0,90);
        ORIENTATIONS.append(Surface.ROTATION_90,0);
        ORIENTATIONS.append(Surface.ROTATION_180,270);
        ORIENTATIONS.append(Surface.ROTATION_270,180);
    }

    //Camera variables
    private CameraManager manager;
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSessions;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;

    //Permission of Camera identifier
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    //Camera background thread and handler
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    //The object containing the image data and the variables that are accessed across multiple threads
    private final ImageData imageData = new ImageData();
    //The Boolean to check if the recording mode is on or off
    public boolean recordingData;

    //Identifier for the intent
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

        //Set up of the texture view
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
                btnCapture.setClickable(false);     //If clicked disable until proceeded
                boolean recordingDataBuffer = !recordingData;   //Save the state the recordingData should get afterwards
                recordingData = false;     //Disable recording data to stop capturing frames as quickly as possible
                synchronized (imageData) {          //Second if have been recording, stop frames from processing more data; all thread save
                    if (!recordingDataBuffer) {
                        imageData.communicationFinishedCounter = imageData.COMMUNICATION_FINISHED_PARAMETER;
                    }
                }
                //Now distinguish between start and stopped
                if(!recordingDataBuffer) {
                    //Stopped
                    btnCapture.setBackgroundColor(BUTTON_COLOR_OFF);
                    btnCapture.setText(BUTTON_STRING_OFF);
                    while(ThreadManager.getInstance().getmDecoderThreadPool().getActiveCount() != 0) {      //care about sill executing threads, wait until all done
                    }
                    synchronized (imageData) {  //if all done clear all saved data
                        imageData.dataStream.clear();
                        imageData.communicationFinishedCounter = 0;
                    }
                } else {
                    //Started
                    btnCapture.setBackgroundColor(BUTTON_COLOR_ON); //change color
                    btnCapture.setText(BUTTON_STRING_ON);

                    //flashLight();

                    recordingData=true;

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
            //The surface of the image reader
            Surface imageSurface = imageReader.getSurface();

            //Both surfaces are saved together in an array
            List<Surface> outputSurface = new ArrayList<>(2);
            outputSurface.add(imageSurface);
            outputSurface.add(surface);

            long expTime = 1000000000/8000; //in nanoseconds
            int sensitivity = 10000;    //ISO
            long fps = 1000000000/30;   //in nanoseconds

            //Initialize the capture builder by using a template
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            //Disable the automatic exposure mode
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF);
            //Set the exposure time
            captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, expTime);
            //Set the sensitivity
            captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,sensitivity);
            //Set the frame rate
            captureRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION,fps);

            //Add both surfaces
            captureRequestBuilder.addTarget(surface);
            captureRequestBuilder.addTarget(imageSurface);

            //Create the capture session
            cameraDevice.createCaptureSession(outputSurface, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if(cameraDevice == null)
                        return;
                    //The session is created
                    cameraCaptureSessions = cameraCaptureSession;
                    //The preview can be started
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
     * Updates the camera preview, or more it starts capturing the frames and passes them to the surfaces of the session
     */
    private void updatePreview() {
        if(cameraDevice == null)
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
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
        //Opens Camera Manager
        manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try {
            //Gets the ID
            cameraId = manager.getCameraIdList()[0];
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        try{
            //Get characteristics of camera
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

            //Access permission for camera from the android system
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

    /**
     * Setup of the camera
     */
    private void setUpCamera() {
        try{
            //Get characteristics of camera from manager
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
            //Width and height will be overridden
            //Size is from 0(biggest) to length-1(smallest)
            if(yuvSizes != null && yuvSizes.length > 0)
            {
                width = yuvSizes[0].getWidth();
                height = yuvSizes[0].getHeight();
            }
            //Initialize the image reader with the desired width, height, format and image buffer length
            imageReader = ImageReader.newInstance(width, height,ImageFormat.YUV_420_888,5);
            //Disable the recording mode
            recordingData = false;

            //<editor-fold desc="Listener of image reader">
            //Listener is set up by the image reader
            final ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                //Method executed if an image is available
                @Override
                public void onImageAvailable(ImageReader imageReader) {
                    //Acquire image data from the image reader
                    Image image = imageReader.acquireNextImage();

                    //Check if recording mode is on / the transmission has started
                    if (recordingData) {
                        //Initialize byte array to store the image data
                        byte[] data = new byte[image.getWidth() * image.getHeight()];
                        //Acquire the data of the y plane from the image
                        image.getPlanes()[0].getBuffer().get(data);
                        //Close the image
                        image.close();
                        //Pass the image data to the worker thread for further processing
                        try {
                            //Accessing the thread pool and sending the received image data to a worker thread
                            ThreadManager.getInstance().getmDecoderThreadPool().execute(new RunnableProcesingData(data.clone()));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }else{
                        //If recording mode is off, close the image immediately
                        image.close();
                    }
                }
            };
            //</editor-fold>

            //Image reader is linked to the listener
            imageReader.setOnImageAvailableListener(readerListener,mBackgroundHandler);

            //Next step is the setup of the preview
            createCameraPreview();

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }
    //</editor-fold>

    //<editor-fold desc="Sub methods">
    //Request permission from the user to open the camera
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
        //Is called after the the user returns to the application
        super.onResume();
        startBackgroundThread();
        if(textureView.isAvailable())
            openCamera();
        else
            textureView.setSurfaceTextureListener(textureListener);
    }

    @Override
    protected void onPause() {
        //Is called if the application is paused
        stopBackgroundThread();
        super.onPause();
    }

    private void stopBackgroundThread() {
        //The background work is stopped if application is paused
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
        //Creates the new thread
        mBackgroundThread = new HandlerThread("Camera Background");
        //Sets the priority of the new thread to the maximum
        mBackgroundThread.setPriority(Thread.MAX_PRIORITY);
        //Starts the thread
        mBackgroundThread.start();
        //Assigns the new background thread to a handler, which is linked to the camera session
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }


    private void flashLight() {

        try {
            manager.setTorchMode(cameraId, true);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        try {
                Thread.currentThread().wait(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        try {
            manager.setTorchMode(cameraId, false);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }


    }

    //</editor-fold>

    //<editor-fold desc="Threads">
    private class ThreadSaveData extends Thread {

        public void run() {
            //wait until all threads of the thread pool are finished with executing
            while(ThreadManager.getInstance().getmDecoderThreadPool().getActiveCount() != 0) {}

            //set up an intent to send the message to the new activity
            Intent intent = new Intent(MainActivity.this, DisplayMessageActivity.class);
            //Initialize the string for the message
            String message = "";

            //Access the data
            synchronized (imageData) {

                //Access the bytes of the dataStream list and save theses as characters to the message string
                for(int i=0; i<imageData.dataStream.size();i++) {
                    message = message + String.valueOf((char) (byte) imageData.dataStream.get(i));
                }
                //Add an identification to the intent
                intent.putExtra(EXTRA_MESSAGE,message);

                //Finally reset the data, to be ready for a new communication
                imageData.dataStream.clear();
                imageData.communicationFinishedCounter = 0;
            }

            //Activate the capture button
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    btnCapture.setClickable(true);
                }
            });

            //Start the new activity by passing the message with the intent
            startActivity(intent);
        }
    }

    public class ImageData {
        //Initialization of the variables
        //The list where the bytes of the final message are stored
        public List<Byte> dataStream;
        //The variable which is used as a counter to check if the communication is finished
        public int communicationFinishedCounter;
        //The length of the message
        public final int  MESSAGE_LENGTH;
        //The parameter which is used to set to determine the end of the communication
        public final int COMMUNICATION_FINISHED_PARAMETER;

        ImageData() {
            //Initialization of the list
            dataStream = new ArrayList<>();
            //Set the Counter to 0, since no data received yet
            communicationFinishedCounter = 0;
            //Set it to the length of the message in bytes
            MESSAGE_LENGTH = 3;
            //Set this counter
            int buffer = 0;
            for(int n=0;n<=MESSAGE_LENGTH;n++) {
                buffer+=n;
            }
            COMMUNICATION_FINISHED_PARAMETER = buffer;
        }
    }

    private class RunnableProcesingData implements Runnable {
        //Variables
        byte[] data;

        RunnableProcesingData(byte[] data) {
            //Initialization
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
            //The upper border of the ROI
            int upperROIBorder;
            //The lower border of the ROI, preset to -1 to check afterwards if it has been set
            int lowerROIBorder = -1;
            //Saves temporarily the current line
            int currentLine = -1;
            //The highest value of a pixel
            int highestLightIntensity = 0;
            //The lowest value of a pixel
            int lowestLightIntensity = 250;
            //Saves temporarily the value of the current pixel
            int currentLightIntensity;
            //Counter to check if an interval is over
            int counterInterval = 0;
            //Counter which counts consecutive stripes
            int counterConsecutiveStripes = 0;
            //Counter which saves the highest number of consecutive stripes
            int consecutiveStripesHighestRow = 0;
            //Saves the highest occurrence of consecutive stripes of all rows
            int consecutiveStripesHighestAll = 0;

            //Constants
            //The step size for the rows
            int STEP_ROI_ROW = 25;
            //The step size for the pixels in a row or respectively the columns
            int STEP_ROI_PIXEL = 8;
            //The to exceeding difference to count a difference in the light intensity as a stripe
            int DISTINGUISH_VALUE = 50;
            //The length of the maximum interval possible without a stripe
            int INTERVAL_OF_STRIPES = 65;
            //The number of lines we consider around the line with the most consecutive stripes
            int RANGE_AROUND_MOST_STRIPES = 20;
            //The minimum number of consecutive stripes to process further with the data
            int MINIMUM_CONSECUTIVE_STRIPES = 8;

            //<editor-fold desc="ROI Detection">
            //First loop to change the row;
            for(int counterRow=0; counterRow<width; counterRow=counterRow+STEP_ROI_ROW) {
                //Second loop to change the pixel per row or rather the column
                for(int counterPixel=0;counterPixel<height; counterPixel=counterPixel+STEP_ROI_PIXEL) {
                    //Save the current value in a buffer; counterRow+counterPixel*width represents the position in the array
                    currentLightIntensity = (dataPlanes[counterRow+counterPixel*width] & 0xff);
                    //Increment the interval counter
                    counterInterval++;
                    //Check if the current value is higher than the previous highest
                    if(currentLightIntensity>highestLightIntensity) {
                        //Save the new highest value
                        highestLightIntensity = currentLightIntensity;
                    }
                    //Check if the current value is lower than the previous lowest
                    if(currentLightIntensity<lowestLightIntensity) {
                        //Save the new lowest value
                        lowestLightIntensity = currentLightIntensity;
                    }
                    //Check if the difference between the highest and the lowest value is exceeding the threshold
                    if(highestLightIntensity-lowestLightIntensity > DISTINGUISH_VALUE) {
                        //A stripe is detected
                        //Save the position of the line in the buffer; (counterRow+counterPixel*width)%width represents the line in the array
                        currentLine = (counterRow+counterPixel*width)%width;
                        //Increment the stripes counter
                        counterConsecutiveStripes++;

                        //Reset the highest value
                        highestLightIntensity = 0;
                        //Reset the lowest value
                        lowestLightIntensity = 250;
                        //Reset the interval counter to start a new interval
                        counterInterval = 0;
                    }
                    //Check if no stripe has been detected within one interval
                    if(counterInterval>=INTERVAL_OF_STRIPES/STEP_ROI_PIXEL) {
                        //A row of consecutive stripes has ended or multiply intervals with no stripe at all
                        //Check if the counted consecutive stripes are more than the highest count of consecutive stripes in this row
                        if(consecutiveStripesHighestRow<counterConsecutiveStripes) {
                            //Save the the count of most consecutive stripes in this row
                            consecutiveStripesHighestRow=counterConsecutiveStripes;
                        }

                        //Reset the counted consecutive stripes
                        counterConsecutiveStripes = 0;
                        //Reset the highest value
                        highestLightIntensity = 0;
                        //Reset the lowest value
                        lowestLightIntensity = 250;
                        //Reset the interval counter to start a new interval
                        counterInterval = 0;
                    }
                }
                //One row has been processed
                //Check if the highest count of consecutive stripes in this row is more than the highest count of consecutive stripes in all rows
                if (consecutiveStripesHighestAll<consecutiveStripesHighestRow) {
                    //Save the new count as the highest
                    consecutiveStripesHighestAll=consecutiveStripesHighestRow;
                    //Save the position of this line with the most consecutive stripes as the origin
                    lowerROIBorder = currentLine;
                }
                //Reset highest value
                highestLightIntensity = 0;
                //Reset the lowest value
                lowestLightIntensity = 250;
                //Reset the interval counter
                counterInterval = 0;
                //Reset the counted consecutive stripes
                counterConsecutiveStripes = 0;
            }

            //The highest and lowest row are set as the borders of the ROI
            //The lowest border is set according to the number of lines we consider around the origin line
            lowerROIBorder+=RANGE_AROUND_MOST_STRIPES/2;
            //The upper border is set
            upperROIBorder = lowerROIBorder-RANGE_AROUND_MOST_STRIPES;
            //Check if the upper border is within the range of the array
            if(upperROIBorder<0) {
                //Change the values to be within the range of the array
                upperROIBorder=0;
                lowerROIBorder=RANGE_AROUND_MOST_STRIPES;
            }
            //Check if the lower border is within the range of the array
            if(lowerROIBorder>=width) {
                //Change the values to be within the range of the array
                lowerROIBorder=width-1;
                upperROIBorder = lowerROIBorder-RANGE_AROUND_MOST_STRIPES;
            }
            //</editor-fold>
            //</editor-fold>

            //Check if ROI found otherwise discard frame
            if(consecutiveStripesHighestAll>=MINIMUM_CONSECUTIVE_STRIPES) {
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
                    for(int n=upperROIBorder; n<lowerROIBorder; n=n+STEP_1DIM_ROW) {
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
                //The step size
                int STEP_THRESH = 4;
                //The distinguishing value
                int DISTINGUISH_VALUE_THRESH = 20;
                //The factor to automatically adjust the distinguishing value according to the last high
                double DISTINGUISH_FACTOR_THRESH = 0.3;

                //Variables
                //To save the temporarily highest value
                int highestValue = 0;
                //To save the temporarily lowest value
                int lowestValue = 250;
                //To save the position of the temporarily lowest value
                int lowestValuePosition = 0;
                //To save the value of the previous temporarily lowest value
                int lowestValueOld = -1;
                //The current distinguish value
                int currentDistinguishValue = DISTINGUISH_VALUE_THRESH;
                //A buffer for the current value
                int currentValue;
                //A boolean to differentiate between the two search procedures
                boolean searchHigh = true;
                //The final list, where the thresholds and the interval positions of the thresholds are saved
                ArrayList<Integer> threshValues = new ArrayList<>();

                //The loop to access the entries of the data array
                for(int currentPosition=0; currentPosition<height;currentPosition+=STEP_THRESH) {
                    //Save the current value in a buffer
                    currentValue = data1Dim[currentPosition];
                    //Check which search procedure is active - searching for a high or a low
                    if(searchHigh) {
                        //We search for an increase / a high
                        //Check if current value lower than lowest
                        if(currentValue<lowestValue) {
                            //Save the current value as the lowest
                            lowestValue=currentValue;
                            //Save the position of the lowest value
                            lowestValuePosition = currentPosition;
                        }
                        //Check if we have an increase according to the current distinguish value
                        if(currentValue>lowestValue+currentDistinguishValue) {
                            //A high is detected
                            //Check if it is not the first high
                            if(lowestValueOld!=-1) {
                                //It is not the first high and we can save a threshold and the corresponding position
                                //Check if the low before or after the high is higher - use the higher low
                                if(lowestValue>lowestValueOld) {
                                    //Save the threshold according to the mean of the highest and the lowest value after the high
                                    threshValues.add((lowestValue+highestValue)/2);
                                }else {
                                    //Save the threshold according to the mean of the highest and the lowest value before the high
                                    threshValues.add((lowestValueOld+highestValue)/2);
                                }
                                //Save the position or rather the border of the interval with this high and the corresponding threshold
                                threshValues.add(lowestValuePosition);

                                //Set the new distinguish value according to the height of the last rising edge
                                currentDistinguishValue = (int) ((highestValue-lowestValue)* DISTINGUISH_FACTOR_THRESH);
                                //Check that if the minimum is complied
                                if(currentDistinguishValue<DISTINGUISH_VALUE_THRESH) {
                                    currentDistinguishValue = DISTINGUISH_VALUE_THRESH;
                                }
                            }
                            //Change the active search procedure
                            searchHigh = false;
                            //Save the current lowest value as the old one
                            lowestValueOld = lowestValue;
                            //Reset the highest and lowest value
                            lowestValue=250;
                            highestValue=0;
                        }
                    } else {
                        //We search for a decrease / a low
                        //Check if the current value is higher than the previously highest
                        if(currentValue>highestValue) {
                            //Save the current as the highest
                            highestValue = currentValue;
                        }
                        //Check if we have a decrease according to the current thresh value
                        if(currentValue<highestValue-currentDistinguishValue) {
                            //change the active search procedure
                            searchHigh = true;
                            //Set the new distinguish value according to the height of the last falling edge
                            currentDistinguishValue= (int) ((highestValue-lowestValueOld)* DISTINGUISH_FACTOR_THRESH);
                            //Comply with the minimum
                            if(currentDistinguishValue<DISTINGUISH_VALUE_THRESH) {
                                currentDistinguishValue = DISTINGUISH_VALUE_THRESH;
                            }
                        }
                    }
                }
                //</editor-fold>

                if (threshValues.size()>=MINIMUM_CONSECUTIVE_STRIPES) {
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
                    //Buffer to save the current encoded data
                    byte encodedData = 0;
                    //Array, where all the decoded data is saved; one pair per block: block number, data
                    byte[] decodedDataFrame = new byte[12];
                    //buffer
                    byte byteBuffer;
                    //Tracks the block part; there are three parts, since three times six bits in one block
                    int blockPart = 0;
                    //The current position in the final decodedData array
                    int positionDecodedData = 0;
                    //The bit position in the current encoded data buffer
                    int positionEncodedData = 0;
                    //Counts the series of ones or is respectively the length of a high
                    int counterHigh=0;
                    //The end position of a high
                    int endHigh = -1;
                    //The start position of a high
                    int startHigh;
                    //The length of a low
                    int lengthLow;
                    //Saves the value of the last decoded bit; -1 nothing, 0 zero, 1 one, 2 start bit
                    int lastBit = -1;
                    //Is set if an error occurs
                    boolean error = false;
                    //Is set if a start bit is detected but last block not finished
                    boolean startError = false;
                    //Is set if a block is finished
                    boolean blockFinished = true;
                    //Is set to identify if the detection for block number is active, running backwards in the data stream
                    boolean blockNumber = false;
                    //Is used to save the position of the start bit to use when the block number detection is over
                    int positionStartBit = -1;
                    //<editor-fold desc="Algorithm">

                    for (int position = 0; position<height; position++) {
                        //If block number detection, go backward in data stream
                        if(blockNumber && position>=2) {
                            position-=2;
                        } else if(blockNumber) {
                            //Data stream is at minimum, so set error to start new at last start bit
                            error = true;
                        }

                        //check if an error occurred
                        if(error) {
                            //Reset the variables to start new
                            error = false;  //Reset error flag
                            encodedData = 0;    //Reset current buffered data
                            decodedDataFrame[positionDecodedData] = 0; //Reset the data at current position in array
                            //Check if second part of the block is active
                            if(blockPart!=0) {
                                //Reset the corresponding data in the first part of the block
                                decodedDataFrame[positionDecodedData-1] = 0;
                                //Change position to start new
                                positionDecodedData--;
                            }
                            //Start with first block part again
                            blockPart = 0;
                            //Reset the counter for the decoded bits
                            positionEncodedData = 0;
                            //Reset the last captured bit
                            lastBit = -1;
                            //Check if error with start bit
                            if(startError) {
                                //Reset the flag
                                startError = false;
                                //Set start bit, so that the start bit is used
                                lastBit = 2;

                                //If start error occurs while running backward, do not use the block number handler (else if). Otherwise the start bit would not be used
                            } else if(blockNumber) {
                                //If error while running backward
                                //Block number false to start normal at last start bit
                                blockNumber=false;
                                //Get old position back
                                position=positionStartBit;
                                //Do not use the last detected bit
                                lastBit = -1;
                                //Set last high to the detected old start bit
                                endHigh = positionStartBit-1;
                            }
                        }

                        //Check if a one / a high point
                        if(data1Dim[position]>=1) {
                            //Increment counter
                            counterHigh++;

                        //Check if a sequence of ones has ended
                        } else if(counterHigh != 0) {
                            //Check if it is a long high, respectively a start bit
                            if(30<=counterHigh && counterHigh<=50) {
                                //If start bit while running backward
                                if(blockNumber) {
                                    //Set normal error, to start again
                                    //Important to not end in an infinity loop
                                    error = true;

                                    //If detected in normal mode
                                } else {
                                    //set the last bit and the position
                                    lastBit = 2;
                                    //Set position of start bit to remember
                                    positionStartBit = position;
                                    //Save position of high
                                    endHigh = position - counterHigh;
                                    //Set start position to beginning of start bit
                                    position=endHigh;
                                    //Set block number detection active
                                    blockNumber = true;

                                    //Check if it is an error, since a new start bit is detected too early
                                    if(!blockFinished) {
                                        //Set the error flag
                                        error = true;
                                        //Set the flag to not discard the detected start bit
                                        startError = true;
                                    }
                                    //Set flag that a new block has started
                                    blockFinished=false;
                                }

                            //Check if it is a normal high
                            } else if(8<=counterHigh && counterHigh<=29) {
                                //Set the start of the high
                                startHigh = position - counterHigh;
                                //Position if in block number mode
                                if(blockNumber) {
                                    startHigh = position + counterHigh;
                                }

                                //Only process further if the end position of the last high is available - so it is not the first high and no error before
                                if(endHigh!=-1) {
                                    //Calculate the number of zeros (the length of the low) in between the last highs
                                    lengthLow = abs(startHigh-endHigh)-1;

                                    //Save position of the current high for further runs
                                    endHigh = position - 1;
                                    //Position differs if in block number mode
                                    if(blockNumber) {
                                        endHigh = position + 1;
                                    }

                                    //Check the different length:
                                    //Check if it is a start bit
                                    if(1 <= lengthLow && lengthLow <= 8) {
                                        //If start bit while running backward
                                        if(blockNumber) {
                                            //Set normal error, to start again
                                            //Important to not end in an infinity loop
                                            error = true;

                                            //If detected in normal mode
                                        } else {
                                            //set the last bit and the position
                                            lastBit = 2;
                                            //Set position of start bit to remember
                                            positionStartBit = position;
                                            //Save position of high
                                            endHigh = position - counterHigh;
                                            //Set start position to beginning of start bit
                                            position=endHigh;
                                            //Set block number detection active
                                            blockNumber = true;

                                            //Check if it is an error, since a new start bit is detected too early
                                            if(!blockFinished) {
                                                //Set the error flag
                                                error = true;
                                                //Set the flag to not discard the detected start bit
                                                startError = true;
                                            }
                                            //Set flag that a new block has started
                                            blockFinished=false;
                                        }

                                    //Only check the other lows if last bit is available
                                    } else if (lastBit!=-1) {
                                        //Check if it is a 0.2 ms low
                                        if(8 <= lengthLow && lengthLow <= 23){
                                            //Check if block number
                                            if(blockNumber) {
                                                if(lastBit == 2 || lastBit == 1) {
                                                    //It is a 0
                                                    //Do not have to write to buffer, since buffer is initialized with 0
                                                    //Increment position in buffer
                                                    positionEncodedData++;
                                                    //Set the current bit as last bit
                                                    lastBit = 0;

                                                } else {
                                                    //Error, other last bits not possible
                                                    error = true;
                                                }

                                            } else {

                                                if(lastBit == 2 || lastBit == 0) {
                                                    //The new bit has the value 1
                                                    //Save the bit in the temporary buffer
                                                    encodedData = (byte) ((1 << (positionEncodedData) | encodedData));
                                                    //Increment position in buffer
                                                    positionEncodedData++;
                                                    //Set the current bit as last bit
                                                    lastBit = 1;
                                                } else {
                                                    //Error, other last bits not possible
                                                    error = true;
                                                }

                                            }




                                        //Check if it is a 0.4 ms low
                                        } else if(24 <= lengthLow && lengthLow <= 42){
                                            //Differentiate according to the last bit
                                            if(blockNumber) {
                                                if(lastBit == 2 || lastBit == 1) {
                                                    //It is a 1
                                                    encodedData = (byte) ((1 << (positionEncodedData) | encodedData));
                                                    //Increment position in buffer
                                                    positionEncodedData++;
                                                    //Set the current bit as last bit
                                                    lastBit = 1;

                                                } else {
                                                    //It is a 0
                                                    //Increment position in buffer
                                                    positionEncodedData++;
                                                    //Set the current bit as last bit
                                                    lastBit = 0;
                                                }

                                            } else {

                                                if(lastBit == 2 || lastBit == 0) {
                                                    //The new bit has the value 0
                                                    //Do not have to write to buffer, since buffer is initialized with 0
                                                    //Only increment position in buffer
                                                    positionEncodedData++;
                                                    //Set the current bit as last bit
                                                    lastBit = 0;
                                                } else {
                                                    //The new bit has the value 1
                                                    //Save the bit in the temporary buffer
                                                    encodedData = (byte) ((1 << (positionEncodedData) | encodedData));
                                                    //Increment position in buffer
                                                    positionEncodedData++;
                                                    //Set the current bit as last bit
                                                    lastBit = 1;
                                                }

                                            }

                                        //Check if it is a 0.6 ms low
                                        } else if(43 <= lengthLow && lengthLow <= 62){
                                            //Differentiate according to the last bit
                                            if(blockNumber) {
                                                if(lastBit == 0) {
                                                    //It is a 1
                                                    encodedData = (byte) ((1 << (positionEncodedData) | encodedData));
                                                    positionEncodedData++;
                                                    lastBit = 1;

                                                } else {
                                                    //Error, other last bits not possible
                                                    error = true;
                                                }


                                            } else {
                                                if(lastBit == 1) {
                                                    //The new bit has the value 0
                                                    //Do not have to write to buffer, since buffer is initialized with 0
                                                    //Only increment position in buffer
                                                    positionEncodedData++;
                                                    //Set the current bit as last bit
                                                    lastBit = 0;
                                                } else {
                                                    //Error, other last bits not possible
                                                    error = true;
                                                }

                                            }

                                        //The length of the low is not mapped
                                        } else {
                                            // error
                                            error = true;
                                        }

                                        //Every time a high is proceeded, check if a part of the block is finished:
                                        //Check if temporary buffer is filled with 3 bits and it is the first block part
                                        if(positionEncodedData==3 && blockPart==0) {
                                            //Decode the 3 bits - result 3 bits, which represent the block number
                                            if ((byteBuffer = decodeBlockNumber(encodedData)) != -1) {
                                                //Save the 4 bits (block number) in the final data array
                                                decodedDataFrame[positionDecodedData] = byteBuffer;
                                                //Reset the position in the encoded data buffer
                                                positionEncodedData = 0;
                                                //Reset the temporary encoded data buffer
                                                encodedData = 0;
                                                //Increment block part
                                                blockPart = 1;
                                                //Increment the position in the final data array
                                                positionDecodedData++;

                                                //Change from backward mode to forward mode
                                                position=positionStartBit;
                                                blockNumber=false;
                                                lastBit=2;
                                                endHigh=positionStartBit - 1;

                                            } else {
                                                //Error, not possible bit sequence
                                                error = true;
                                            }

                                        //Check if temporary buffer is filled with 6 bits and it is the second block part
                                        } else if(positionEncodedData==6 && blockPart == 1) {
                                            //Decode the 6 bits - result 4 bits, which represent the first 4 bit of the data byte
                                            if ((byteBuffer = decode4Bit6Bit(encodedData)) != -1) {
                                                //Save the first 4 bit of the final data byte in the final data array
                                                //Do not increment position in final data array, since the other 4 bit are added later
                                                decodedDataFrame[positionDecodedData] = byteBuffer;
                                                //Reset the position in the encoded data buffer
                                                positionEncodedData = 0;
                                                //Reset the temporary encoded data buffer
                                                encodedData = 0;
                                                //Increment block part
                                                blockPart = 2;
                                            } else {
                                                //Error, not possible bit sequence
                                                error = true;
                                            }

                                        //Check if temporary buffer is filled with 6 bits and it is the last block part
                                        } else if(positionEncodedData == 6) {
                                            //Decode the 6 bits - result 4 bits, which represent the other 4 bit of the data byte
                                            if ((byteBuffer = decode4Bit6Bit(encodedData)) != -1) {
                                                //Save the other 4 bit of the final data byte in the final data array
                                                decodedDataFrame[positionDecodedData] = (byte) ((byteBuffer << 4) | decodedDataFrame[positionDecodedData]);
                                                //Increment the position in the final data array
                                                positionDecodedData++;
                                                //Set the block part to 0 again to start with a new block
                                                blockPart = 0;
                                                //Set the flag, that a block is finished
                                                blockFinished = true;
                                            }
                                            //This time the error flag is set regardless of the result
                                            //1. If the decoded bit sequence is not possible, we have an error
                                            //2. If the block is finished, the flags and buffers are reset in the error handling to be ready for a new block - the finished block is not lost
                                            error = true;
                                        }
                                    }
                                } else {
                                    //Set the endHigh if it is the first high and no previous high available
                                    endHigh = position - 1;
                                }

                            } else if(counterHigh>=50){
                                //The high is too long
                                //Set error flag
                                error = true;
                                //Do not consider this high for next processing steps
                                endHigh = -1;
                            }
                            //We do not consider a high that is too short, it is discarded and does not interfere
                            //The counter for the highs is set to 0 again
                            counterHigh = 0;
                        }
                    }

                    //The algorithm has ended
                    //Check if data is uncompleted
                    //If the first part of a block is already saved but not finished, discard both
                    if(blockPart==2) {
                        //Both current parts are discarded, since not completed
                        decodedDataFrame[positionDecodedData] = 0;
                        decodedDataFrame[positionDecodedData-1] = 0;
                    }
                    //</editor-fold>

                    //</editor-fold>

                    synchronized (imageData) {
                        //Only save data if the communication is not already finished
                        if (imageData.communicationFinishedCounter != imageData.COMMUNICATION_FINISHED_PARAMETER) {

                            //Check if the block number of the byte is within the range of the message length
                            //Check if the decoded data is not null
                            for(int n=0;decodedDataFrame[n] > 0 && decodedDataFrame[n+1]!=0 && decodedDataFrame[n] <= imageData.MESSAGE_LENGTH;n+=2) {
                                //If the size of the list is too small, increase the size according to the block number
                                while(imageData.dataStream.size()<decodedDataFrame[n]) {
                                    imageData.dataStream.add((byte) 0);
                                }

                                //Check if the same block has not already been detected
                                if(imageData.dataStream.get(decodedDataFrame[n]-1) == 0) {
                                    //Set the data to the position of the block number
                                    imageData.dataStream.set(decodedDataFrame[n]-1,decodedDataFrame[n+1]);
                                    //Add the block number to the counter
                                    imageData.communicationFinishedCounter +=decodedDataFrame[n];
                                }

                                //After every block, check if the message is completed
                                if (imageData.communicationFinishedCounter == imageData.COMMUNICATION_FINISHED_PARAMETER) {
                                    //Stop the recording of new frames
                                    recordingData = false;

                                    //Start a new thread to prepare the displaying of the message
                                    ThreadSaveData threadSaveData = new ThreadSaveData();
                                    threadSaveData.start();

                                    //Execute task on UI thread to change UI elements
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            //Disable capture button and set the color back to non recording mode
                                            btnCapture.setClickable(false);
                                            btnCapture.setBackgroundColor(Color.WHITE);
                                            btnCapture.setText(BUTTON_STRING_OFF);
                                        }
                                    });

                                    //Stop the loop to hinder the processing of more blocks
                                    break;
                                }
                            }
                        }
                    }

                }
            }
        }
        //The mapping for 4Bit6Bit
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

        private byte decodeBlockNumber(byte dataCoded) {
            byte data = -1;
            switch (dataCoded) {
                case 1:
                    data = 1;
                    break;
                case 3:
                    data = 2;
                    break;
                case 5:
                    data = 3;
                    break;
                case 8:
                    data = 4;
            }
            return data;    //returns -1 if error
        }
    }
    //</editor-fold>
}