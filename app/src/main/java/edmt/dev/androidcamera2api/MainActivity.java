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
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    //<editor-fold desc="Declarations">
    //User interface
    private Button btnCapture;
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
    private File file;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private boolean mFlashSupported;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private int imageCounter;
    private List<byte[]> dataImages = new ArrayList<>();
    private int dataYLength;

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
        btnCapture = (Button)findViewById(R.id.btnCapture);
        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takePicture();
            }
        });
    }
    //</editor-fold>

    //<editor-fold desc="Main methods">
    /**
     * Method takes picture, it sets up the camera session and saves the image to file
     */
    private void takePicture() {
        if(cameraDevice == null)
            return;
        CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try{
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if(characteristics != null)
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        .getOutputSizes(ImageFormat.YUV_420_888);

            //Capture image with custom size
            int width = 640;
            int height = 480;
            //Size is from 0(biggest) to length-1(highest)
            if(jpegSizes != null && jpegSizes.length > 0)
            {
                width = jpegSizes[jpegSizes.length-1].getWidth();
                height = jpegSizes[jpegSizes.length-1].getHeight();
            }
            //Set up image reader with custom size and format
            final ImageReader reader = ImageReader.newInstance(width,height,ImageFormat.YUV_420_888,1);
            List<Surface> outputSurface = new ArrayList<>(2);
            outputSurface.add(reader.getSurface());
            outputSurface.add(new Surface(textureView.getSurfaceTexture()));

            //Set up capture builder with all parameters
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            //Reader surface is passed to capture builder to pass its image in there
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            //Get current manual parameters
            /*
            expLower = captureBuilder.get(CaptureRequest.SENSOR_EXPOSURE_TIME);
            senUpper = captureBuilder.get(CaptureRequest.SENSOR_SENSITIVITY);
            fraUpper = captureBuilder.get(CaptureRequest.SENSOR_FRAME_DURATION);
            */

            //Set manual parameters
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF);
            captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, expLower);
            captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,senUpper);
            captureBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION,fraUpper);


            //Check orientation base on device
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION,ORIENTATIONS.get(rotation));

            //<editor-fold desc="Listener of image reader">
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                //If image is passed to surface by capturing, the image is available in th reader and this method is called
                @Override
                public void onImageAvailable(ImageReader imageReader) {
                    Image image = null;
                    try{
                        //Get image from image reader
                        image = reader.acquireLatestImage();
                        //Get real height and width, if reader has not possible size, image uses the nearest
                        int width = image.getWidth();
                        int height = image.getHeight();
                        //set up the file path
                        file = new File(Environment.getExternalStorageDirectory()+"/yuv/picture_"+width+"_"+height+".yuv");

                        //Create image yuv out of planes
                        Image.Plane Y = image.getPlanes()[0];
                        Image.Plane U = image.getPlanes()[1];
                        Image.Plane V = image.getPlanes()[2];

                        int Yb = Y.getBuffer().remaining();
                        int Ub = U.getBuffer().remaining();
                        int Vb = V.getBuffer().remaining();

                        //The data buffer where the data of the image is stored
                        byte[] data = new byte[Yb + Ub + Vb];

                        //Add data to buffer
                        Y.getBuffer().get(data, 0, Yb);
                        U.getBuffer().get(data, Yb, Ub);
                        V.getBuffer().get(data, Yb + Ub, Vb);


                        //Set up output file of text file with buffer data
                        DataOutputStream dataOutputStream = new DataOutputStream(new FileOutputStream(file.getPath().replace(".yuv",".txt")));
                        //The loop is writing byte after byte to the output stream
                        int i=1;
                        for(int n=0; n<Yb;n++) {
                            dataOutputStream.writeBytes(Integer.toString(data[n])+"; ");
                            if((n+1)%width==0){
                                dataOutputStream.writeBytes("___The line "+i+" and the width "+(n+1)/i+"\n");
                                i++;
                            }
                        }
                        dataOutputStream.close();

                        //Save the image the data buffer
                        save(data);
                    }
                    catch (FileNotFoundException e)
                    {
                        e.printStackTrace();
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                    finally {
                        {
                            if(image != null)
                                image.close();
                        }
                    }
                }
                //Saves the image data in the output stream as a picture
                private void save(byte[] bytes) throws IOException {
                    OutputStream outputStream = null;
                    try{
                        outputStream = new FileOutputStream(file);
                        outputStream.write(bytes);
                    }finally {
                        if(outputStream != null)
                            outputStream.close();
                    }
                }
            };
            //</editor-fold>

            //Image reader is set to image reader listener
            reader.setOnImageAvailableListener(readerListener,mBackgroundHandler);

            //Sets up listener of capture process
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                //When picture captured, shows toast and creates the preview again
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(MainActivity.this, "Saved "+file, Toast.LENGTH_SHORT).show();
                    createCameraPreview();
                }
            };

            //Sets up capture session callback
            cameraDevice.createCaptureSession(outputSurface, new CameraCaptureSession.StateCallback() {
                //When session is configured, session captures(capture builder is built) => image is sent to surface of reader
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    try{
                        //The capture command
                        cameraCaptureSession.capture(captureBuilder.build(),captureListener,mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                }
            },mBackgroundHandler);


        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

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

            //Sets the manual exposure values
            Range expRange  = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            expUpper = (Long) expRange.getUpper();
            expLower = (Long) expRange.getLower();

            Range senRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            senUpper = (Integer) senRange.getUpper();
            senLower = (Integer) senRange.getLower();

            expLower = (Long) (long) (1000000000/16000);
            senUpper = (Integer) (int) 100;
            fraUpper = (Long) (long) 60;


            //New code to initialize
            Size[] jpegSizes = null;
            if(characteristics != null)
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        .getOutputSizes(ImageFormat.YUV_420_888);

            //Capture image with custom size
            width = 640;
            height = 480;
            //Size is from 0(biggest) to length-1(smallest)
            if(jpegSizes != null && jpegSizes.length > 0)
            {
                width = jpegSizes[jpegSizes.length-1].getWidth();
                height = jpegSizes[jpegSizes.length-1].getHeight();
            }
            //Set up image reader with custom size and format
            imageReader = ImageReader.newInstance(width,height,ImageFormat.YUV_420_888,10);
            imageCounter = 0;


            //<editor-fold desc="Listener of image reader">
            final ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                //If image is passed to surface by capturing, the image is available in th reader and this method is called
                @Override
                public void onImageAvailable(ImageReader imageReader) {
                    try {
                        //Get image from image reader
                        Image image = imageReader.acquireLatestImage();

                        //Create image yuv out of planes
                        Image.Plane Y = image.getPlanes()[0];
                        Image.Plane U = image.getPlanes()[1];
                        Image.Plane V = image.getPlanes()[2];

                        int Yb = Y.getBuffer().remaining();
                        int Ub = U.getBuffer().remaining();
                        int Vb = V.getBuffer().remaining();

                        //The data buffer where the data of the image is stored
                        byte[] data = new byte[Yb + Ub + Vb];

                        //Add data to buffer
                        Y.getBuffer().get(data, 0, Yb);
                        U.getBuffer().get(data, Yb, Ub);
                        V.getBuffer().get(data, Yb + Ub, Vb);


                        //Close image
                        image.close();

                        dataImages.add(data);

                        //Check if end recording frames
                        if (imageCounter == 60) {
                            cameraCaptureSessions.stopRepeating();
                            imageReader.close();
                            dataYLength = Yb;
                            captureCompleted();
                        }

                        //Set imageCounter
                        imageCounter++;
                    }
                    catch(CameraAccessException e) {
                            e.printStackTrace();
                    }
                }

            };
            //</editor-fold>

            //Image reader is set to image reader listener
            imageReader.setOnImageAvailableListener(readerListener,mBackgroundHandler);

            createCameraPreview();

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private void captureCompleted() {
        try {

            for(int n=0; n<=imageCounter; n++) {

                save(dataImages.get(n),n);

            }

        } catch (IOException e) {
            e.printStackTrace();
        }



    }

    //Saves the image data in the output stream as a picture
    private void save(byte[] bytes, int imageCounter) throws IOException {
        //set up the file path
        file = new File(Environment.getExternalStorageDirectory()+"/yuv/picture_"+width+"_"+height+"_"+imageCounter+".yuv");
        //Stream of image
        OutputStream outputStream = null;
        //Stream of text file
        DataOutputStream dataOutputStream = null;
        try{
            dataOutputStream = new DataOutputStream(new FileOutputStream(file.getPath().replace(".yuv",".txt")));
            outputStream = new FileOutputStream(file);

            outputStream.write(bytes);

            dataOutputStream.writeBytes("A new picture is captured:\n\n\n");
            int i=1;
            for(int n=0; n<dataYLength;n++) {
                dataOutputStream.writeBytes(Integer.toString(bytes[n])+"; ");
                if((n+1)%width==0){
                    dataOutputStream.writeBytes("___The line "+i+" and the width "+(n+1)/i+"\n");
                    i++;
                }
            }


        }finally {
            if(outputStream != null)
                outputStream.close();
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
}
