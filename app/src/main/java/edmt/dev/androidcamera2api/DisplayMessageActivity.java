//This is the class for the display message activity, which is created with the receiving of the message
package edmt.dev.androidcamera2api;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import java.util.List;

public class DisplayMessageActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Start window
        setContentView(R.layout.activity_display_message);

        //Get the message data
        List<String> savedDates = StorageManager.getInstance().returnListDates();
        List<String> savedMessages = StorageManager.getInstance().returnListMessages();


        // Get TableLayout object in layout xml
        final TableLayout tableLayout = (TableLayout)findViewById(R.id.table_layout_table);

        Context context = getApplicationContext();


        for (int i=0; i<savedMessages.size() && i<savedDates.size(); i++) {

            // Create a new table row.
            TableRow tableRow = new TableRow(context);

            // Set new table row layout parameters.
            TableRow.LayoutParams layoutParams = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT);
            layoutParams.setMargins(10,10,10,10);
            tableRow.setLayoutParams(layoutParams);

            // Add a TextView in the first column.
            TextView textView = new TextView(context);
            textView.setText(savedDates.get(i));
            tableRow.addView(textView, 0);

            // Add a button in the second column
            TextView textView2 = new TextView(context);
            textView2.setText(savedMessages.get(i));
            tableRow.addView(textView2, 1);

            tableLayout.addView(tableRow);

        }


    }
}
