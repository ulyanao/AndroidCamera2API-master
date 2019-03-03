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
import java.util.ArrayList;
import java.util.List;

public class DisplayMessageActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Start window
        setContentView(R.layout.activity_display_message);

        //Get Button object
        Button buttonPrint = (Button) findViewById(R.id.buttonPrint);

        //Get the message data
        final List<ArrayList<String>> savedData = StorageManager.getInstance().getData();

        // Get TableLayout object in layout xml
        final TableLayout tableLayout = (TableLayout) findViewById(R.id.table_layout_table);

        //Get context
        Context context = getApplicationContext();

        for (int i = 0; i < savedData.get(0).size(); i++) {

            // Create a new table row.
            TableRow tableRow = new TableRow(context);

            //Get resource for conversion from pix to dip
            float d = context.getResources().getDisplayMetrics().density;

            // Set new table row layout parameters.
            TableLayout.LayoutParams tableRowParams = new TableLayout.LayoutParams(TableLayout.LayoutParams.WRAP_CONTENT,TableLayout.LayoutParams.WRAP_CONTENT);
            tableRow.setLayoutParams(tableRowParams);

            //Set params for text views
            TableRow.LayoutParams params = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT,TableRow.LayoutParams.WRAP_CONTENT);
            params.setMargins((int)(10*d),(int) (1*d),(int)(10*d),(int)(1*d));

            for(int n=0; n<savedData.size(); n++) {
                // Add a TextView in the n column.
                TextView textView = new TextView(context);
                textView.setText(savedData.get(n).get(i));
                tableRow.addView(textView, n, params);
            }

            //Add the view
            tableLayout.addView(tableRow);
        }

        //Set up listener and handler of button click
        buttonPrint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //set up the file path
                File file01 = new File(Environment.getExternalStorageDirectory() + "/"+"VLC_Data_empty"+".txt");
                if(savedData.get(0).size()>0) {
                    file01 = new File(Environment.getExternalStorageDirectory() + "/VLC_Data_"+savedData.get(0).get(savedData.get(0).size()-1)+".txt");
                }
                //Stream of text file
                FileWriter fileWriter01 = null;
                try {
                    fileWriter01 = new FileWriter(file01);

                    fileWriter01.write("Date"+"\t\t"+"Message"+"   "+"TP [ms]"+"   "+"GP [ms]"+"   "+"Time/Image"+"   "+"Thread"+"   "+"ROI"+"   "+"1Dim"+"   "+"Thresh"+"   "+"Down"+"   "+"Decode"+"   "+"Saving"+"\n");

                    for(int i=0; i<savedData.get(0).size(); i++) {
                        for(int n=0; n<savedData.size();n++) {
                            fileWriter01.write(savedData.get(n).get(i)+"\t");
                        }
                        fileWriter01.write("\n");
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
