package de.velcommuta.denul.ui;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

import de.velcommuta.denul.R;

/**
 * Activity containing the flow for adding a new friend
 */
public class FriendAddActivity extends AppCompatActivity implements FriendAddTechSelectionFragment.TechSelectionListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend_add);
        loadTechSelectFragment();
    }


    /**
     * Load the tech selection fragment
     */
    private void loadTechSelectFragment() {
        Fragment fragment = FriendAddTechSelectionFragment.newInstance();
        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.friend_add_container, fragment)
                .commit();
    }


    /**
     * Replace the currently active fragment with the Google Nearby fragment
     */
    private void slideInNearbyFragment() {
        // Get new fragment instance
        FriendAddTechSelectionFragment fr = FriendAddTechSelectionFragment.newInstance();
        // Perform replacement
        slideForwardReplace(fr);
    }

    /**
     * Replace a fragment by sliding the old one out to the left, and the new one in from the right.
     * Based on http://stackoverflow.com/a/4819665/1232833
     * @param fragment The new fragment
     */
    private void slideForwardReplace(Fragment fragment) {
        // Get a fragment transition
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        // Set up the animations to use
        ft.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left);
        // Perform the replacement
        ft.replace(R.id.friend_add_container, fragment);
        // Add to back stack
        // TODO Implement proper backstack behaviour, it's broken at the moment
        ft.addToBackStack(null);
        // Commit transaction
        ft.commit();
    }


    @Override
    public void techSelection(int tech) {
        if (tech == FriendAddTechSelectionFragment.TECH_NEARBY) {
            slideInNearbyFragment();
        }
    }
}
