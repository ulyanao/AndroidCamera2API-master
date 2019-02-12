//This is the class for the display message activity, which is created with the receiving of the message
package edmt.dev.androidcamera2api;

import android.content.Context;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class DisplayMessageActivity extends AppCompatActivity {

    private List<String> savedDates;
    private List<String> savedMessages;
    private List<String> savedThrough;
    private List<String> savedGood;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Start window
        setContentView(R.layout.activity_display_message);

        //Get Button object
        Button buttonPrint = (Button) findViewById(R.id.buttonPrint);

        //Get the message data
        savedDates = StorageManager.getInstance().returnListDates();
        savedMessages = StorageManager.getInstance().returnListMessages();
        savedThrough = StorageManager.getInstance().returnListThroughPut();
        savedGood = StorageManager.getInstance().returnListGoodPut();


        // Get TableLayout object in layout xml
        final TableLayout tableLayout = (TableLayout) findViewById(R.id.table_layout_table);

        //Get context
        Context context = getApplicationContext();


        for (int i = 0; i < savedMessages.size() && i < savedDates.size(); i++) {

            // Create a new table row.
            TableRow tableRow = new TableRow(context);

            // Set new table row layout parameters.
            TableRow.LayoutParams layoutParams = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT);
            layoutParams.setMargins(10, 10, 10, 10);
            tableRow.setLayoutParams(layoutParams);

            // Add a TextView in the first column.
            TextView textView = new TextView(context);
            textView.setText(savedDates.get(i));
            tableRow.addView(textView, 0);

            // Add a button in the second column
            TextView textView2 = new TextView(context);
            textView2.setText(savedMessages.get(i));
            tableRow.addView(textView2, 1);

            // Add a TextView in the third column.
            TextView textView3 = new TextView(context);
            textView3.setText(savedThrough.get(i));
            tableRow.addView(textView3, 2);

            // Add a button in the fourth column
            TextView textView4 = new TextView(context);
            textView4.setText(savedGood.get(i));
            tableRow.addView(textView4, 3);

            //Add the view
            tableLayout.addView(tableRow);

        }

        //Set up listener and handler of button click
        buttonPrint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //set up the file path
                File file01 = new File(Environment.getExternalStorageDirectory() + "/"+savedDates.get(savedDates.size()-1)+".txt");
                //Stream of text file
                FileWriter fileWriter01 = null;
                try {
                    fileWriter01 = new FileWriter(file01);

                    fileWriter01.write("Date"+",\t"+"Message"+",\t"+"TP [ms]"+",\t"+"GP [ms]"+"\n");

                    for(int i=0; i<savedDates.size() && i<savedMessages.size(); i++) {
                        fileWriter01.write(savedDates.get(i)+",\t"+savedMessages.get(i)+",\t"+savedThrough.get(i)+",\t"+savedGood.get(i)+"\n");
                    }


                } catch (IOException e) {
                    e.printStackTrace();

                } finally {
                    try {
                        if (fileWriter01 != null) {
                            fileWriter01.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                Toast.makeText(getApplicationContext(),"Data saved as .txt file on internal storage!",Toast.LENGTH_SHORT).show();
            }

        });


    }

}
