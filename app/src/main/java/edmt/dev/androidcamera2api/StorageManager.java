package edmt.dev.androidcamera2api;
import java.util.ArrayList;
import java.util.List;

public class StorageManager {
    //Static instance of the Storage Manager
    private static StorageManager sInstance = new StorageManager();

    //Variables
    private List<String> savedMessages;
    private List<String> savedDates;

    private StorageManager() {
        savedMessages = new ArrayList<>();
        savedDates = new ArrayList<>();
    }


    //The static method to access the singleton of the class
    public static StorageManager getInstance() {
        return sInstance;
    }

    //Method to add Data
    public void addData(String date, String message) {
        savedMessages.add(message);
        savedDates.add(date);
    }

    //Method to get saved messages
    public List<String> returnListMessages() {
        return savedMessages;
    }

    //Method to get savedDates
    public List<String> returnListDates() {
        return savedDates;
    }


}
