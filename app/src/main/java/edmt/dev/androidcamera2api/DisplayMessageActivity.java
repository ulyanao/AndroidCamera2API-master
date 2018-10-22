package edmt.dev.androidcamera2api;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

public class DisplayMessageActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Start window
        setContentView(R.layout.activity_display_message);
        //Get the intent
        Intent intent = getIntent();
        //Get the message
        String message = intent.getStringExtra(MainActivity.EXTRA_MESSAGE);
        //Display the message
        TextView textView = findViewById(R.id.textView);
        textView.setText(message);
    }
}
