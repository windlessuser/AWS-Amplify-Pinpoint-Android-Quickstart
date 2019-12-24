package com.google.firebase.quickstart.analytics.kotlin

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.amazonaws.mobile.client.AWSMobileClient
import com.amazonaws.mobile.client.Callback
import com.amazonaws.mobile.client.UserStateDetails
import com.amazonaws.mobile.config.AWSConfiguration
import com.amazonaws.mobileconnectors.pinpoint.PinpointConfiguration
import com.amazonaws.mobileconnectors.pinpoint.PinpointManager
import com.google.firebase.quickstart.analytics.R
import com.google.firebase.quickstart.analytics.java.MainActivity
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

/**
 * Activity which displays numerous background images that may be viewed. These background images
 * are shown via {@link ImageFragment}.
 */
class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val KEY_FAVORITE_FOOD = "favorite_food"

        private val IMAGE_INFOS = arrayOf(
            ImageInfo(R.drawable.favorite, R.string.pattern1_title, R.string.pattern1_id),
            ImageInfo(R.drawable.flash, R.string.pattern2_title, R.string.pattern2_id),
            ImageInfo(R.drawable.face, R.string.pattern3_title, R.string.pattern3_id),
            ImageInfo(R.drawable.whitebalance, R.string.pattern4_title, R.string.pattern4_id)
        )
    }

    /**
     * The [androidx.viewpager.widget.PagerAdapter] that will provide fragments for each image.
     * This uses a [FragmentPagerAdapter], which keeps every loaded fragment in memory.
     */
    private lateinit var imagePagerAdapter: ImagePagerAdapter

    /**
     * The `FirebaseAnalytics` used to record screen views.
     */
    // [START declare_analytics]
    private lateinit var pinpointManager: PinpointManager
    // [END declare_analytics]

    fun getPinpointManager(applicationContext: Context?): PinpointManager? {
        if (MainActivity.pinpointManager == null) { // Initialize the AWS Mobile Client
            val awsConfig = AWSConfiguration(applicationContext)
            AWSMobileClient.getInstance().initialize(applicationContext, awsConfig, object : Callback<UserStateDetails> {
                override fun onResult(userStateDetails: UserStateDetails) {
                    Log.i("INIT", userStateDetails.userState.toString())
                }

                override fun onError(e: Exception) {
                    Log.e("INIT", "Initialization error.", e)
                }
            })
            val pinpointConfig = PinpointConfiguration(
                    applicationContext,
                    AWSMobileClient.getInstance(),
                    awsConfig)
            MainActivity.pinpointManager = PinpointManager(pinpointConfig)
        }
        return MainActivity.pinpointManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // [START shared_app_measurement]
        // Obtain the Pinpoint instance.
        val pinpointManager = MainActivity.getPinpointManager(applicationContext)
        pinpointManager.sessionClient.startSession()
        // [END shared_app_measurement]

        // On first app open, ask the user his/her favorite food. Then set this as a user property
        // on all subsequent opens.
        val userFavoriteFood = getUserFavoriteFood()
        if (userFavoriteFood == null) {
            askFavoriteFood()
        } else {
            setUserFavoriteFood(userFavoriteFood)
        }

        // Create the adapter that will return a fragment for each image.
        imagePagerAdapter = ImagePagerAdapter(supportFragmentManager, IMAGE_INFOS)

        // Set up the ViewPager with the pattern adapter.
        viewPager.adapter = imagePagerAdapter

        // Workaround for AppCompat issue not showing ViewPager titles
        val params = pagerTabStrip.layoutParams as ViewPager.LayoutParams
        params.isDecor = true

        // When the visible image changes, send a screen view hit.
        viewPager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                recordImageView()
                recordScreenView()
            }
        })

        // Send initial screen screen view hit.
        recordImageView()
    }

    override fun onDestroy() {
        super.onDestroy()
        MainActivity.pinpointManager.sessionClient.stopSession()
        MainActivity.pinpointManager.analyticsClient.submitEvents()
    }

    public override fun onResume() {
        super.onResume()
        recordScreenView()
    }

    /**
     * Display a dialog prompting the user to pick a favorite food from a list, then record
     * the answer.
     */
    private fun askFavoriteFood() {
        val choices = resources.getStringArray(R.array.food_items)
        val ad = AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle(R.string.food_dialog_title)
                .setItems(choices) { _, which ->
                    val food = choices[which]
                    setUserFavoriteFood(food)
                }.create()

        ad.show()
    }

    /**
     * Get the user's favorite food from shared preferences.
     * @return favorite food, as a string.
     */
    private fun getUserFavoriteFood(): String? {
        return PreferenceManager.getDefaultSharedPreferences(this)
                .getString(KEY_FAVORITE_FOOD, null)
    }

    /**
     * Set the user's favorite food as an app measurement user property and in shared preferences.
     * @param food the user's favorite food.
     */
    private fun setUserFavoriteFood(food: String) {
        Log.d(TAG, "setFavoriteFood: $food")

        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putString(KEY_FAVORITE_FOOD, food)
                .apply()

        // [START user_property]
        val event = MainActivity.pinpointManager.analyticsClient
                .createEvent("user_property-event")
                .withAttribute("favorite_food", food)
        MainActivity.pinpointManager.analyticsClient.recordEvent(event)
        // [END user_property]
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val i = item.itemId
        if (i == R.id.menu_share) {
            val name = getCurrentImageTitle()
            val text = "I'd love you to hear about $name"

            val sendIntent = Intent()
            sendIntent.action = Intent.ACTION_SEND
            sendIntent.putExtra(Intent.EXTRA_TEXT, text)
            sendIntent.type = "text/plain"
            startActivity(sendIntent)

            // [START custom_event]
            // [START custom_event]
            val event = MainActivity.pinpointManager.analyticsClient
                    .createEvent("share_image-event")
                    .withAttribute("image_name", name)
                    .withAttribute("full_text", text)
            MainActivity.pinpointManager.analyticsClient.recordEvent(event)
            // [END custom_event]
        }
        return false
    }

    /**
     * Return the title of the currently displayed image.
     *
     * @return title of image
     */
    private fun getCurrentImageTitle(): String {
        val position = viewPager.currentItem
        val info = IMAGE_INFOS[position]
        return getString(info.title)
    }

    /**
     * Return the id of the currently displayed image.
     *
     * @return id of image
     */
    private fun getCurrentImageId(): String {
        val position = viewPager.currentItem
        val info = IMAGE_INFOS[position]
        return getString(info.id)
    }

    /**
     * Record a screen view for the visible [ImageFragment] displayed
     * inside [FragmentPagerAdapter].
     */
    private fun recordImageView() {
        val id = getCurrentImageId()
        val name = getCurrentImageTitle()

        // [START image_view_event]
        // [START image_view_event]
        val event = MainActivity.pinpointManager.analyticsClient
                .createEvent("image_view_event")
                .withAttribute("Item_ID", id)
                .withAttribute("Item_Name", name)
                .withAttribute("Content_Type", "image")
        MainActivity.pinpointManager.analyticsClient.recordEvent(event)
        // [END image_view_event]
    }

    /**
     * This sample has a single Activity, so we need to manually record "screen views" as
     * we change fragments.
     */
    private fun recordScreenView() {
        // This string must be <= 36 characters long in order for setCurrentScreen to succeed.
        val screenName = "${getCurrentImageId()}-${getCurrentImageTitle()}"

        // [START set_current_screen]
        // [START set_current_screen]
        val event = MainActivity.pinpointManager.analyticsClient
                .createEvent("current-screen-event")
                .withAttribute("Screen_name", screenName)
                .withAttribute("Activity", this.localClassName)
        MainActivity.pinpointManager.analyticsClient.recordEvent(event)
        // [END set_current_screen]
    }

    /**
     * A [FragmentPagerAdapter] that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    @SuppressLint("WrongConstant")
    inner class ImagePagerAdapter(
        fm: FragmentManager,
        private val infos: Array<ImageInfo>
    ) : FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        override fun getItem(position: Int): Fragment {
            val info = infos[position]
            return ImageFragment.newInstance(info.image)
        }

        override fun getCount() = infos.size

        override fun getPageTitle(position: Int): CharSequence? {
            if (position < 0 || position >= infos.size) {
                return null
            }
            val l = Locale.getDefault()
            val info = infos[position]
            return getString(info.title).toUpperCase(l)
        }
    }
}
