package edmt.dev.androidcamera2api;
import java.util.ArrayList;
import java.util.List;

public class StorageManager {
    //Storage Manager is a singleton
    //Static instance of the Storage Manager
    private static StorageManager sInstance;

    //Long Time Data
    //List saves every table row
    private List<ArrayList<String>> savedData = new ArrayList<>();

    //Temp Data
    //The list where the bytes of the final message are stored
    public List<Byte> dataStream = new ArrayList<>();
    //The variable which is used as a counter to check if the communication is finished
    public int counterCommunicationFinished = 0;
    //The length of the message
    public final int  MESSAGE_LENGTH = 3;
    //The parameter which is used to set to determine the end of the communication
    public final int COMMUNICATION_FINISHED_PARAMETER;

    //The time management
    //through and good put
    public long timeStart;
    public long timeThroughPut;
    public long timeGoodPut;
    public int counterPut = 0;
    public int counterImages = 0;
    //Processing time List
    private List<ArrayList<Integer>> timeData = new ArrayList<>();

    private StorageManager() {
        //Set the finished parameter according to the message length
        int buffer = 0;
        for(int n=0;n<=MESSAGE_LENGTH;n++) {
            buffer+=n;
        }
        COMMUNICATION_FINISHED_PARAMETER = buffer;

        //Adds the initial Integer Lists to the time list
        for(int i=0;i<7;i++) {
            timeData.add(new ArrayList<Integer>());
        }

        //Adds the initial String lists to the savedData list
        for(int i=0;i<5+timeData.size();i++) {
            savedData.add(new ArrayList<String>());
        }
    }


    //The static method to access the singleton of the class
    public static synchronized StorageManager getInstance() {
        if(sInstance==null) {
            sInstance = new StorageManager();
        }
        return sInstance;
    }

    //Method to add Data
    public void setData(String[] data) {
        for(int i = 0; i < data.length && i < savedData.size();i++) {
            savedData.get(i).add(data[i]);
        }
    }

    public List<ArrayList<String>> getData() {
        return savedData;
    }

    public void resetTempData() {
        dataStream.clear();

        //Empty the time lists
        for (ArrayList<Integer> data :timeData
             ) {
            data.clear();
        }

        counterCommunicationFinished = 0;
        counterPut=0;
        counterImages=0;

    }

    public void setTimeLists(int[] data) {
        for(int i = 0; i < data.length && i < timeData.size();i++) {
            timeData.get(i).add(data[i]);
        }
    }

    public List<ArrayList<Integer>> getTimeLists() {
        return timeData;
    }


}
