package edmt.dev.androidcamera2api;
import java.util.ArrayList;
import java.util.List;

public class StorageManager {
    //Storage Manager is a singleton
    //Static instance of the Storage Manager
    private static StorageManager sInstance;

    //Variables
    private List<String> savedMessages = new ArrayList<>();
    private List<String> savedDates  = new ArrayList<>();
    private List<String> savedThroughPut  = new ArrayList<>();
    private List<String> savedGoodPut  = new ArrayList<>();

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
    //Processing time
    public long timeAcquireEnd;
    public List<Integer> timeAcquireImage = new ArrayList<>();
    public List<Integer> timeAcquireIdle = new ArrayList<>();
    //Counters
    public int counterImages = 0;
    public int counterThreadsStarted = 0;
    public int counterThreadsFinished =0;


    private StorageManager() {
        //Set the finished parameter according to the message length
        int buffer = 0;
        for(int n=0;n<=MESSAGE_LENGTH;n++) {
            buffer+=n;
        }
        COMMUNICATION_FINISHED_PARAMETER = buffer;
    }


    //The static method to access the singleton of the class
    public static synchronized StorageManager getInstance() {
        if(sInstance==null) {
            sInstance = new StorageManager();
        }
        return sInstance;
    }

    //Method to add Data
    public void addData(String date, String message, String through, String good) {
        savedDates.add(date);
        savedMessages.add(message);
        savedThroughPut.add(through);
        savedGoodPut.add(good);
    }

    //Method to get saved messages
    public List<String> returnListMessages() {
        return savedMessages;
    }

    //Method to get savedDates
    public List<String> returnListDates() {
        return savedDates;
    }

    //Method to get saved through
    public List<String> returnListThroughPut() {
        return savedThroughPut;
    }

    //Method to get saved good
    public List<String> returnListGoodPut() {
        return savedGoodPut;
    }

    public void resetTempData() {
        dataStream.clear();
        timeAcquireImage.clear();
        timeAcquireIdle.clear();
        counterCommunicationFinished = 0;
        counterPut=0;
        counterImages=0;
        counterThreadsFinished=0;
        counterThreadsStarted=0;
    }


}
