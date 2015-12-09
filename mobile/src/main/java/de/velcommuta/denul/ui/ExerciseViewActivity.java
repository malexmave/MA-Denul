package de.velcommuta.denul.ui;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;

import java.util.List;

import de.velcommuta.denul.R;
import de.velcommuta.denul.data.GPSTrack;
import de.velcommuta.denul.service.DatabaseService;
import de.velcommuta.denul.service.DatabaseServiceBinder;

/**
 * Activity to show details about a specific track
 */
public class ExerciseViewActivity extends AppCompatActivity implements ServiceConnection {
    private static final String TAG = "ExerciseViewActivity";

    private DatabaseServiceBinder mDbBinder;
    private GPSTrack mTrack;
    private int mTrackId;

    private TextView mTrackTitle;
    private TextView mTrackDate;
    private TextView mTrackDistance;
    private ImageView mTrackMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exercise_show);
        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        ActionBar ab = getSupportActionBar();
        ab.setDisplayHomeAsUpEnabled(true);

        requestDatabaseBinder();
        Bundle b = getIntent().getExtras();
        if (b != null) {
            mTrackId = b.getInt("track-id");
        } else {
            Log.e(TAG, "onCreate: No Bundle passed, returning");
            finish();
        }
        mTrackTitle = (TextView) findViewById(R.id.exc_view_title);
        mTrackDate = (TextView) findViewById(R.id.exc_view_date);
        mTrackDistance = (TextView) findViewById(R.id.exc_view_distance);
        mTrackMode = (ImageView) findViewById(R.id.exc_view_mode);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_exercise_view, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.action_delete:
                askDeleteConfirm();
                return true;
            case R.id.action_rename:
                // TODO
                return true;
            case R.id.action_share:
                // TODO
                return true;
        }
        return false;
    }


    /**
     * Ask the user to confirm the deletion request, and perform the deletion if it was confirmed
     */
    private void askDeleteConfirm() {
        // Prepare a builder
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // Set the values, build and show the dialog
        builder.setMessage("Delete this exercise?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        mDbBinder.deleteGPSTrack(mTrack);
                        Toast.makeText(ExerciseViewActivity.this, "Exercise deleted", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .setNegativeButton("No", null)
                .create().show();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(this);
    }

    /**
     * Request a binder to the database service
     */
    private void requestDatabaseBinder() {
        if (!DatabaseService.isRunning(this)) {
            Log.w(TAG, "bindDbService: Trying to bind to a non-running database service. Aborting");
        }
        Intent intent = new Intent(this, DatabaseService.class);
        if (!bindService(intent, this, 0)) {
            Log.e(TAG, "bindDbService: An error occured during binding :(");
        } else {
            Log.d(TAG, "bindDbService: Database service binding request sent");
        }
    }


    /**
     * Load the track information and display it
     */
    private void loadTrackInformation() {
        // Load track
        mTrack = mDbBinder.getGPSTrackById(mTrackId);
        // Set title
        mTrackTitle.setText(mTrack.getSessionName());
        // Set date
        mTrackDate.setText(DateTimeFormat.shortDateTime().print(new LocalDateTime(mTrack.getTimestamp(), DateTimeZone.forID(mTrack.getTimezone()))));
        float distance = 0;
        List<Location> locList = mTrack.getPosition();
        // TODO This seems to not be working right now :(
        for (int i = 1; i < locList.size(); i++) {
            distance = distance + locList.get(i).distanceTo(locList.get(i-1));
        }
        if (distance < 1000.0f) {
            mTrackDistance.setText(String.format(getString(R.string.distance_m), (int) distance));
        } else {
            mTrackDistance.setText(String.format(getString(R.string.distance_km), (int) distance / 1000.0f));
        }
        switch (mTrack.getModeOfTransportation()) {
            case GPSTrack.VALUE_RUNNING:
                mTrackMode.setImageDrawable(getResources().getDrawable(R.drawable.ic_running));
                break;
            case GPSTrack.VALUE_CYCLING:
                mTrackMode.setImageDrawable(getResources().getDrawable(R.drawable.ic_cycling));
                break;
            default:
                Log.w(TAG, "loadTrackInformation: Unknown Mode of transportation");
                mTrackMode.setImageDrawable(getResources().getDrawable(R.drawable.ic_running));
        }
        // TODO Load google map, plot track
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        Log.d(TAG, "onServiceConnected: New service connection received");
        mDbBinder = (DatabaseServiceBinder) iBinder;
        // TODO Debugging code, move to passphrase activity once it is added
        if (!mDbBinder.isDatabaseOpen()) {
            mDbBinder.openDatabase("VerySecureHardcodedPasswordOlolol123");
        }
        loadTrackInformation();
    }


    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        Log.d(TAG, "onServiceDisconnected: Lost DB service");
        mDbBinder = null;
    }
}