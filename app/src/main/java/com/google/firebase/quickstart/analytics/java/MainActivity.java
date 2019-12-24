/*
 * Copyright Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * For more information on setting up and running this sample code, see
 * https://firebase.google.com/docs/analytics/android
 */

package com.google.firebase.quickstart.analytics.java;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.amazonaws.mobile.client.Callback;
import com.amazonaws.mobile.client.UserStateDetails;
import com.amazonaws.mobile.config.AWSConfiguration;
import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobileconnectors.pinpoint.PinpointConfiguration;
import com.amazonaws.mobileconnectors.pinpoint.PinpointManager;
import com.amazonaws.mobileconnectors.pinpoint.analytics.AnalyticsEvent;
import com.google.firebase.quickstart.analytics.R;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Activity which displays numerous background images that may be viewed. These background images
 * are shown via {@link ImageFragment}.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String KEY_FAVORITE_FOOD = "favorite_food";

    private static final ImageInfo[] IMAGE_INFOS = {
            new ImageInfo(R.drawable.favorite, R.string.pattern1_title, R.string.pattern1_id),
            new ImageInfo(R.drawable.flash, R.string.pattern2_title, R.string.pattern2_id),
            new ImageInfo(R.drawable.face, R.string.pattern3_title, R.string.pattern3_id),
            new ImageInfo(R.drawable.whitebalance, R.string.pattern4_title, R.string.pattern4_id),
    };

    /**
     * The {@link androidx.viewpager.widget.PagerAdapter} that will provide fragments for each image.
     * This uses a {@link FragmentPagerAdapter}, which keeps every loaded fragment in memory.
     */
    private ImagePagerAdapter mImagePagerAdapter;

    /**
     * The {@link ViewPager} that will host the patterns.
     */
    private ViewPager mViewPager;
    /**
     * The user's favorite food, chosen from a dialog.
     */
    private String mFavoriteFood;

    public static PinpointManager pinpointManager;

    public static PinpointManager getPinpointManager(final Context applicationContext) {
        if (pinpointManager == null) {
            // Initialize the AWS Mobile Client
            final AWSConfiguration awsConfig = new AWSConfiguration(applicationContext);
            AWSMobileClient.getInstance().initialize(applicationContext, awsConfig, new Callback<UserStateDetails>() {
                @Override
                public void onResult(UserStateDetails userStateDetails) {
                    Log.i("INIT", userStateDetails.getUserState().toString());
                }

                @Override
                public void onError(Exception e) {
                    Log.e("INIT", "Initialization error.", e);
                }
            });

            PinpointConfiguration pinpointConfig = new PinpointConfiguration(
                    applicationContext,
                    AWSMobileClient.getInstance(),
                    awsConfig);

            pinpointManager = new PinpointManager(pinpointConfig);
        }
        return pinpointManager;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final PinpointManager pinpointManager = getPinpointManager(getApplicationContext());
        pinpointManager.getSessionClient().startSession();

        // On first app open, ask the user his/her favorite food. Then set this as a user property
        // on all subsequent opens.
        String userFavoriteFood = getUserFavoriteFood();
        if (userFavoriteFood == null) {
            askFavoriteFood();
        } else {
            setUserFavoriteFood(userFavoriteFood);
        }


        // Create the adapter that will return a fragment for each image.
        mImagePagerAdapter = new ImagePagerAdapter(getSupportFragmentManager(), IMAGE_INFOS);

        // Set up the ViewPager with the pattern adapter.
        mViewPager = findViewById(R.id.viewPager);
        mViewPager.setAdapter(mImagePagerAdapter);

        // Workaround for AppCompat issue not showing ViewPager titles
        ViewPager.LayoutParams params = (ViewPager.LayoutParams)
                findViewById(R.id.pagerTabStrip).getLayoutParams();
        params.isDecor = true;

        // When the visible image changes, send a screen view hit.
        mViewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                recordImageView();
                recordScreenView();
            }
        });

        // Send initial screen screen view hit.
        recordImageView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        pinpointManager.getSessionClient().stopSession();
        pinpointManager.getAnalyticsClient().submitEvents();
    }

    @Override
    public void onResume() {
        super.onResume();
        recordScreenView();
    }

    /**
     * Display a dialog prompting the user to pick a favorite food from a list, then record
     * the answer.
     */
    private void askFavoriteFood() {
        final String[] choices = getResources().getStringArray(R.array.food_items);
        AlertDialog ad = new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle(R.string.food_dialog_title)
                .setItems(choices, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String food = choices[which];
                        setUserFavoriteFood(food);
                    }
                }).create();

        ad.show();
    }

    /**
     * Get the user's favorite food from shared preferences.
     * @return favorite food, as a string.
     */
    private String getUserFavoriteFood() {
        return PreferenceManager.getDefaultSharedPreferences(this)
                .getString(KEY_FAVORITE_FOOD, null);
    }

    /**
     * Set the user's favorite food as an app measurement user property and in shared preferences.
     * @param food the user's favorite food.
     */
    private void setUserFavoriteFood(String food) {
        Log.d(TAG, "setFavoriteFood: " + food);
        mFavoriteFood = food;

        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putString(KEY_FAVORITE_FOOD, food)
                .apply();

        // [START user_property]
        final AnalyticsEvent event = pinpointManager.getAnalyticsClient()
                .createEvent("user_property-event")
                .withAttribute("favorite_food", mFavoriteFood);
        pinpointManager.getAnalyticsClient().recordEvent(event);
        // [END user_property]
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int i = item.getItemId();
        if (i == R.id.menu_share) {
            String name = getCurrentImageTitle();
            String text = "I'd love you to hear about " + name;

            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, text);
            sendIntent.setType("text/plain");
            startActivity(sendIntent);

            // [START custom_event]
            final AnalyticsEvent event = pinpointManager.getAnalyticsClient()
                    .createEvent("share_image-event")
                    .withAttribute("image_name", name)
                    .withAttribute("full_text", text);
            pinpointManager.getAnalyticsClient().recordEvent(event);
            // [END custom_event]
        }
        return false;
    }

    /**
     * Return the title of the currently displayed image.
     *
     * @return title of image
     */
    private String getCurrentImageTitle() {
        int position = mViewPager.getCurrentItem();
        ImageInfo info = IMAGE_INFOS[position];
        return getString(info.title);
    }

    /**
     * Return the id of the currently displayed image.
     *
     * @return id of image
     */
    private String getCurrentImageId() {
        int position = mViewPager.getCurrentItem();
        ImageInfo info = IMAGE_INFOS[position];
        return getString(info.id);
    }

    /**
     * Record a screen view for the visible {@link ImageFragment} displayed
     * inside {@link FragmentPagerAdapter}.
     */
    private void recordImageView() {
        String id =  getCurrentImageId();
        String name = getCurrentImageTitle();

        // [START image_view_event]
        final AnalyticsEvent event = pinpointManager.getAnalyticsClient()
                .createEvent("image_view_event")
                .withAttribute("Item_ID", id)
                .withAttribute("Item_Name", name)
                .withAttribute("Content_Type", "image");
        pinpointManager.getAnalyticsClient().recordEvent(event);
        // [END image_view_event]
    }

    /**
     * This sample has a single Activity, so we need to manually record "screen views" as
     * we change fragments.
     */
    private void recordScreenView() {
        // This string must be <= 36 characters long in order for setCurrentScreen to succeed.
        String screenName = getCurrentImageId() + "-" + getCurrentImageTitle();

        // [START set_current_screen]
        final AnalyticsEvent event = pinpointManager.getAnalyticsClient()
                .createEvent("current-screen-event")
                .withAttribute("Screen_name", screenName)
                .withAttribute("Activity", this.getLocalClassName());
        pinpointManager.getAnalyticsClient().recordEvent(event);
        // [END set_current_screen]
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class ImagePagerAdapter extends FragmentPagerAdapter {

        private final ImageInfo[] infos;

        @SuppressLint("WrongConstant")
        public ImagePagerAdapter(FragmentManager fm, ImageInfo[] infos) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            this.infos = infos;
        }

        @Override
        public Fragment getItem(int position) {
            ImageInfo info = infos[position];
            return ImageFragment.newInstance(info.image);
        }

        @Override
        public int getCount() {
            return infos.length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if (position < 0 || position >= infos.length) {
                return null;
            }
            Locale l = Locale.getDefault();
            ImageInfo info = infos[position];
            return getString(info.title).toUpperCase(l);
        }
    }
}
