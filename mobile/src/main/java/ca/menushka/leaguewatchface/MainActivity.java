package ca.menushka.leaguewatchface;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity {
    EditText username;
    EditText champion;
    Button saveButton;

    private String USERNAME_TAG = "username";
    private String CHAMPION_OVERRIDE_TAG = "champion_override";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        username = (EditText) findViewById(R.id.usernameField);
        champion = (EditText) findViewById(R.id.champOverrideField);
        saveButton = (Button) findViewById(R.id.saveButton);

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        username.setText(pref.getString(USERNAME_TAG, ""));
        champion.setText(pref.getString(CHAMPION_OVERRIDE_TAG, ""));

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                SharedPreferences.Editor editor = pref.edit();

                editor.putString(USERNAME_TAG, String.valueOf(username.getText()));
                editor.putString(CHAMPION_OVERRIDE_TAG, String.valueOf(champion.getText()));

                editor.apply();
            }
        });
    }
}
