package edmt.dev.androidcamera2api;
import java.util.ArrayList;
import java.util.List;

public class StorageManager {
    //Static instance of the Storage Manager
    private static StorageManager sInstance = new StorageManager();

    //Variables
    private List<String> savedMessages;
    private List<String> savedDates;
    private List<String> savedThroughPut;
    private List<String> savedGoodPut;

    private StorageManager() {
        savedDates = new ArrayList<>();
        savedMessages = new ArrayList<>();
        savedThroughPut = new ArrayList<>();
        savedGoodPut = new ArrayList<>();
    }


    //The static method to access the singleton of the class
    public static StorageManager getInstance() {
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


}
