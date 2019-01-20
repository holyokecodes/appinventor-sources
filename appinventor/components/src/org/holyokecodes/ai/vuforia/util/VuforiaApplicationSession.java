package org.holyokecodes.ai.vuforia.util;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.AsyncTask;
import android.util.Log;
import android.view.WindowManager;

import com.vuforia.CameraDevice;
import com.vuforia.Device;
import com.vuforia.FUSION_PROVIDER_TYPE;
import com.vuforia.INIT_ERRORCODE;
import com.vuforia.INIT_FLAGS;
import com.vuforia.State;
import com.vuforia.Vuforia;
import com.vuforia.Vuforia.UpdateCallbackInterface;

import java.lang.ref.WeakReference;

/**
 * This class handles the Vuforia lifecycle
 */
public class VuforiaApplicationSession implements UpdateCallbackInterface
{
    private static final String LOGTAG = "VuforiaAppSession";

    public WeakReference<Activity> mActivityRef;
    public final WeakReference<VuforiaApplicationControl> mSessionControlRef;

    // Vuforia status flags
    private boolean mStarted = false;
    private boolean mCameraRunning = false;

    private int mVideoMode = CameraDevice.MODE.MODE_DEFAULT;

    // The async tasks that initialize the Vuforia SDK and Trackers:
    private InitVuforiaTask mInitVuforiaTask;
    private LoadTrackerTask mLoadTrackerTask;
    private ResumeVuforiaTask mResumeVuforiaTask;

    // An object used for synchronizing Vuforia initialization, dataset loading
    // and the Android onDestroy() life cycle event. If the application is
    // destroyed while a data set is still being loaded, then we wait for the
    // loading operation to finish before shutting down Vuforia:
    private final Object mLifecycleLock = new Object();

    // Vuforia initialization flags:
    public int mVuforiaFlags = 0;

    // Holds the camera configuration to use upon resuming
    private int mCamera = CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT;


    public VuforiaApplicationSession(VuforiaApplicationControl sessionControl)
    {
        mSessionControlRef = new WeakReference<>(sessionControl);
    }

    public VuforiaApplicationSession(VuforiaApplicationControl sessionControl, int videoMode)
    {
        mSessionControlRef = new WeakReference<>(sessionControl);
        mVideoMode = videoMode;
    }


    // Initializes Vuforia and sets up preferences.
    public void initAR(Activity activity, int screenOrientation)
    {
        VuforiaApplicationException vuforiaException = null;
        mActivityRef = new WeakReference<>(activity);

        if (screenOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR)
        {
            screenOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
        }

        // Apply screen orientation
        mActivityRef.get().setRequestedOrientation(screenOrientation);

        // As long as this window is visible to the user, keep the device's
        // screen turned on and bright:
        mActivityRef.get().getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Determines whether to use OpenGL 2.0,
        // OpenGL 3.0, DirectX (UWP), or Metal (iOS)
        mVuforiaFlags = INIT_FLAGS.GL_20;

        // Initialize Vuforia SDK asynchronously to avoid blocking the
        // main (UI) thread.
        //
        // NOTE: This task instance must be created and invoked on the
        // UI thread and it can be executed only once!
        if (mInitVuforiaTask != null)
        {
            String logMessage = "Cannot initialize SDK twice";
            vuforiaException = new VuforiaApplicationException(
                    VuforiaApplicationException.VUFORIA_ALREADY_INITIALIZATED,
                    logMessage);
            Log.e(LOGTAG, logMessage);
        }

        // Init Vuforia if no exception was thrown
        if (vuforiaException == null)
        {
            try {
                mInitVuforiaTask = new InitVuforiaTask(this);
                mInitVuforiaTask.execute();
            }
            catch (Exception e)
            {
                String logMessage = "Initializing Vuforia SDK failed";
                vuforiaException = new VuforiaApplicationException(
                        VuforiaApplicationException.INITIALIZATION_FAILURE,
                        logMessage);
                Log.e(LOGTAG, logMessage);
            }
        }

        // If an exception was thrown, send it to the application
        // and call onInitARDone to stop the initialization process
        if (vuforiaException != null)
        {
            mSessionControlRef.get().onInitARDone(vuforiaException);
        }
    }


    // Sets the fusion provider type for DeviceTracker optimization
    // This setting only affects the Tracker if the DeviceTracker is used.
    // By default, the provider type is set to FUSION_OPTIMIZE_MODEL_TARGETS_AND_SMART_TERRAIN
    public boolean setFusionProviderType(int providerType)
    {
        int provider =  Vuforia.getActiveFusionProvider();

        if ((provider & ~providerType) != 0)
        {
            if (Vuforia.setAllowedFusionProviders(providerType) == FUSION_PROVIDER_TYPE.FUSION_PROVIDER_INVALID_OPERATION)
            {
                Log.e(LOGTAG,"Failed to set fusion provider type: " + providerType);
                return false;
            }
        }

        Log.d(LOGTAG, "Successfully set fusion provider type: " + providerType);

        return true;
    }


    // Starts Vuforia asynchronously
    public void startAR(int camera)
    {
        mCamera = camera;
        VuforiaApplicationException vuforiaException = null;

        try
        {
            StartVuforiaTask mStartVuforiaTask = new StartVuforiaTask(this);
            mStartVuforiaTask.execute();
        }
        catch (Exception e)
        {
            String logMessage = "Starting Vuforia failed";
            vuforiaException = new VuforiaApplicationException(
                    VuforiaApplicationException.CAMERA_INITIALIZATION_FAILURE,
                    logMessage);
            Log.e(LOGTAG, logMessage);
        }

        // If an exception was thrown, send it to the application
        // and call onInitARDone to stop the initialization process
        if (vuforiaException != null)
        {
            mSessionControlRef.get().onInitARDone(vuforiaException);
        }
    }


    // Stops any ongoing initialization,
    // deinitializes Vuforia, the camera, and trackers
    public void stopAR() throws VuforiaApplicationException
    {
        // Cancel potentially running tasks
        if (mInitVuforiaTask != null
                && mInitVuforiaTask.getStatus() != InitVuforiaTask.Status.FINISHED)
        {
            mInitVuforiaTask.cancel(true);
            mInitVuforiaTask = null;
        }

        if (mLoadTrackerTask != null
                && mLoadTrackerTask.getStatus() != LoadTrackerTask.Status.FINISHED)
        {
            mLoadTrackerTask.cancel(true);
            mLoadTrackerTask = null;
        }

        mInitVuforiaTask = null;
        mLoadTrackerTask = null;

        mStarted = false;

        stopCamera();

        // Ensure that all asynchronous operations to initialize Vuforia
        // and loading the tracker datasets do not overlap:
        synchronized (mLifecycleLock)
        {

            boolean unloadTrackersResult;
            boolean deinitTrackersResult;

            // Destroy the tracking data set:
            unloadTrackersResult = mSessionControlRef.get().doUnloadTrackersData();

            // Deinitialize the trackers:
            deinitTrackersResult = mSessionControlRef.get().doDeinitTrackers();

            // Deinitialize Vuforia SDK:
            Vuforia.deinit();

            if (!unloadTrackersResult)
                throw new VuforiaApplicationException(
                        VuforiaApplicationException.UNLOADING_TRACKERS_FAILURE,
                        "Failed to unload trackers\' data");

            if (!deinitTrackersResult)
                throw new VuforiaApplicationException(
                        VuforiaApplicationException.TRACKERS_DEINITIALIZATION_FAILURE,
                        "Failed to deinitialize trackers");

        }
    }


    // Resumes Vuforia, restarts the trackers and the camera
    private void resumeAR()
    {
        VuforiaApplicationException vuforiaException = null;

        try {
            mResumeVuforiaTask = new ResumeVuforiaTask(this);
            mResumeVuforiaTask.execute();
        }
        catch (Exception e)
        {
            String logMessage = "Resuming Vuforia failed";
            vuforiaException = new VuforiaApplicationException(
                    VuforiaApplicationException.INITIALIZATION_FAILURE,
                    logMessage);
            Log.e(LOGTAG, logMessage);
        }

        // If an exception was thrown, send it to the application
        // and call onInitARDone to stop the initialization process
        if (vuforiaException != null)
        {
            mSessionControlRef.get().onInitARDone(vuforiaException);
        }
    }


    // Pauses Vuforia and stops the camera
    private void pauseAR()
    {
        if (mStarted)
        {
            stopCamera();
        }

        Vuforia.onPause();
    }


    // Initializes, configures, and starts the camera and trackers
    private void startCameraAndTrackers(int camera) throws VuforiaApplicationException
    {
        String error;
        if(mCameraRunning)
        {
            error = "Camera already running, unable to open again";
            Log.e(LOGTAG, error);
            throw new VuforiaApplicationException(
                    VuforiaApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
        }

        mCamera = camera;
        if (!CameraDevice.getInstance().init(camera))
        {
            error = "Unable to open camera device: " + camera;
            Log.e(LOGTAG, error);
            throw new VuforiaApplicationException(
                    VuforiaApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
        }

        if (!CameraDevice.getInstance().selectVideoMode(mVideoMode))
        {
            error = "Unable to set video mode";
            Log.e(LOGTAG, error);
            throw new VuforiaApplicationException(
                    VuforiaApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
        }

        if (!CameraDevice.getInstance().start())
        {
            error = "Unable to start camera device: " + camera;
            Log.e(LOGTAG, error);
            throw new VuforiaApplicationException(
                    VuforiaApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
        }

        mSessionControlRef.get().doStartTrackers();

        mCameraRunning = true;
    }


    private void stopCamera()
    {
        if (mCameraRunning)
        {
            mSessionControlRef.get().doStopTrackers();
            mCameraRunning = false;
            CameraDevice.getInstance().stop();
            CameraDevice.getInstance().deinit();
        }
    }


    // Callback called every cycle
    @Override
    public void Vuforia_onUpdate(State s)
    {
        mSessionControlRef.get().onVuforiaUpdate(s);
    }


    // Called whenever the device orientation or screen resolution changes
    public void onConfigurationChanged()
    {
        if (mStarted)
        {
            Device.getInstance().setConfigurationChanged();
        }
    }


    public void onResume()
    {
        if (mResumeVuforiaTask == null
                || mResumeVuforiaTask.getStatus() == ResumeVuforiaTask.Status.FINISHED)
        {
            // onResume() will sometimes be called twice depending on the screen lock mode
            // This will prevent redundant AsyncTasks from being executed
            resumeAR();
        }
    }


    public void onPause()
    {
        pauseAR();
    }


    public void onSurfaceChanged(int width, int height)
    {
        Vuforia.onSurfaceChanged(width, height);
    }


    public void onSurfaceCreated()
    {
        Vuforia.onSurfaceCreated();
    }


    // An async task to configure and initialize Vuforia asynchronously.
    private static class InitVuforiaTask extends AsyncTask<Void, Integer, Boolean>
    {
        // Initialize with invalid value:
        private int mProgressValue = -1;

        private final WeakReference<VuforiaApplicationSession> appSessionRef;

        InitVuforiaTask(VuforiaApplicationSession session)
        {
            appSessionRef = new WeakReference<>(session);
        }

        protected Boolean doInBackground(Void... params)
        {
            VuforiaApplicationSession session = appSessionRef.get();

            // Prevent the onDestroy() method to overlap with initialization:
            synchronized (session.mLifecycleLock)
            {
                // Configure Vuforia
                // Note: license key goes in the third parameter
                Vuforia.setInitParameters(session.mActivityRef.get(), session.mVuforiaFlags, "AYtvymv/////AAABmcNw6ZzgC0SsmCu/EFCoUs1qP6UKlOpK8XbV6Qn+NYHJw/altw/Cib//l3nRHVJguJ5j3xp3C3vcw5oADSpy8aLLoXapGBQIJjeudB9TmSMnswrrloG+ghh4vOHfFwADu4S85WwkZE6IcuQF6RvsVJo7bTTxWC6UTOAgrYAINHu3ZOkyFl0TMMwSQhf783HlLHfm8ADoHbRB1BTS5YGYwBS54kmkhmxRWvKcAZAOqaRScP+qWhzDp0pXFSG/XtuNvOA4ZBDXAqWvqS9kCX6t55ICBrbKViZcpwW1Bv6PPNZzThud5BqDBtyNOjTdqn0/O0FyOzPoteNFjggxdze0UWj+XotYOKdBj70mQohNMfMN");

                do
                {
                    // Vuforia.init() blocks until an initialization step is
                    // complete, then it proceeds to the next step and reports
                    // progress in percents (0 ... 100%).
                    // If Vuforia.init() returns -1, it indicates an error.
                    // Initialization is done when progress has reached 100%.
                    mProgressValue = Vuforia.init();

                    // Publish the progress value:
                    publishProgress(mProgressValue);

                    // We check whether the task has been canceled in the
                    // meantime (by calling AsyncTask.cancel(true)).
                    // and bail out if it has, thus stopping this thread.
                    // This is necessary as the AsyncTask will run to completion
                    // regardless of the status of the component that
                    // started is.
                } while (!isCancelled() && mProgressValue >= 0
                        && mProgressValue < 100);

                return (mProgressValue > 0);
            }
        }


        protected void onProgressUpdate(Integer... values)
        {
            // Do something with the progress value "values[0]", e.g. update
            // splash screen, progress bar, etc.
        }


        protected void onPostExecute(Boolean result)
        {

            Log.d(LOGTAG, "InitVuforiaTask.onPostExecute: execution "
                    + (result ? "successful" : "failed"));

            VuforiaApplicationException vuforiaException = null;
            VuforiaApplicationSession session = appSessionRef.get();

            // Done initializing Vuforia, next we will try initializing the tracker
            if (result)
            {
                try
                {
                    InitTrackerTask mInitTrackerTask = new InitTrackerTask(session);
                    mInitTrackerTask.execute();
                }
                catch (Exception e)
                {
                    String logMessage = "Failed to initialize tracker.";
                    vuforiaException = new VuforiaApplicationException(
                            VuforiaApplicationException.TRACKERS_INITIALIZATION_FAILURE,
                            logMessage);
                    Log.e(LOGTAG, logMessage);
                }
            }
            else
            {
                String logMessage;

                // NOTE: Check if initialization failed because the device is
                // not supported. At this point the user should be informed
                // with a message.
                logMessage = appSessionRef.get().getInitializationErrorString(mProgressValue);
                Log.e(LOGTAG, "InitVuforiaTask.onPostExecute: " + logMessage + " Exiting.");

                vuforiaException = new VuforiaApplicationException(
                        VuforiaApplicationException.INITIALIZATION_FAILURE,
                        logMessage);
            }

            // If an exception was thrown, send it to the application
            // and call onInitARDone to stop the initialization process
            if (vuforiaException != null)
            {
                session.mSessionControlRef.get().onInitARDone(vuforiaException);
            }
        }
    }

    // An async task to resume Vuforia asynchronously
    private static class ResumeVuforiaTask extends AsyncTask<Void, Void, Void>
    {
        private final WeakReference<VuforiaApplicationSession> appSessionRef;

        ResumeVuforiaTask(VuforiaApplicationSession session)
        {
            appSessionRef = new WeakReference<>(session);
        }

        protected Void doInBackground(Void... params)
        {
            // Prevent the concurrent lifecycle operations:
            synchronized (appSessionRef.get().mLifecycleLock)
            {
                Vuforia.onResume();
            }

            return null;
        }

        protected void onPostExecute(Void result)
        {
            Log.d(LOGTAG, "ResumeVuforiaTask.onPostExecute");

            VuforiaApplicationSession session = appSessionRef.get();

            // We may start the camera only if the Vuforia SDK  has already been
            // initialized and the camera has not already been started
            if (session.mStarted)
            {
                if (!session.mCameraRunning)
                {
                    session.startAR(session.mCamera);
                }
                else
                {
                    session.mSessionControlRef.get().onVuforiaStarted();
                }

                session.mSessionControlRef.get().onVuforiaResumed();
            }
        }
    }

    // An async task to initialize trackers asynchronously
    private static class InitTrackerTask extends AsyncTask<Void, Integer, Boolean>
    {
        private final WeakReference<VuforiaApplicationSession> appSessionRef;

        InitTrackerTask(VuforiaApplicationSession session)
        {
            appSessionRef = new WeakReference<>(session);
        }

        protected  Boolean doInBackground(Void... params)
        {
            synchronized (appSessionRef.get().mLifecycleLock)
            {
                // Load the tracker data set:
                return appSessionRef.get().mSessionControlRef.get().doInitTrackers();
            }
        }

        protected void onPostExecute(Boolean result)
        {
            VuforiaApplicationException vuforiaException = null;
            VuforiaApplicationSession session = appSessionRef.get();

            Log.d(LOGTAG, "InitTrackerTask.onPostExecute: execution "
                    + (result ? "successful" : "failed"));


            // Done initializing the tracker, next we will try loading the tracker
            if (result)
            {
                try
                {
                    session.mLoadTrackerTask = new LoadTrackerTask(session);
                    session.mLoadTrackerTask.execute();
                }
                catch (Exception e)
                {
                    String logMessage = "Failed to load tracker data.";
                    Log.e(LOGTAG, logMessage);

                    vuforiaException = new VuforiaApplicationException(
                            VuforiaApplicationException.LOADING_TRACKERS_FAILURE,
                            logMessage);
                }
            }
            else
            {
                String logMessage = "Failed to initialize trackers.";
                Log.e(LOGTAG, logMessage);

                // Error initializing trackers
                vuforiaException = new VuforiaApplicationException(
                        VuforiaApplicationException.TRACKERS_INITIALIZATION_FAILURE,
                        logMessage);
            }

            // If an exception was thrown, send it to the application
            // and call onInitARDone to stop the initialization process
            if (vuforiaException != null)
            {
                session.mSessionControlRef.get().onInitARDone(vuforiaException);
            }
        }
    }

    // An async task to load the tracker data asynchronously.
    private static class LoadTrackerTask extends AsyncTask<Void, Void, Boolean>
    {
        private final WeakReference<VuforiaApplicationSession> appSessionRef;

        LoadTrackerTask(VuforiaApplicationSession session)
        {
            appSessionRef = new WeakReference<>(session);
        }

        protected Boolean doInBackground(Void... params)
        {
            // Prevent the concurrent lifecycle operations:
            synchronized (appSessionRef.get().mLifecycleLock)
            {
                // Load the tracker data set:
                return appSessionRef.get().mSessionControlRef.get().doLoadTrackersData();
            }
        }

        protected void onPostExecute(Boolean result)
        {
            VuforiaApplicationException vuforiaException = null;
            VuforiaApplicationSession session = appSessionRef.get();

            Log.d(LOGTAG, "LoadTrackerTask.onPostExecute: execution "
                    + (result ? "successful" : "failed"));

            if (!result)
            {
                String logMessage = "Failed to load tracker data.";
                Log.e(LOGTAG, logMessage);

                vuforiaException = new VuforiaApplicationException(
                        VuforiaApplicationException.LOADING_TRACKERS_FAILURE,
                        logMessage);
            }
            else
            {
                // Hint to the virtual machine that it would be a good time to
                // run the garbage collector:
                //
                // NOTE: This is only a hint. There is no guarantee that the
                // garbage collector will actually be run.
                System.gc();

                Vuforia.registerCallback(session);

                session.mStarted = true;
            }

            // Done loading the tracker. Update the application status
            // and pass the exception to check errors
            session.mSessionControlRef.get().onInitARDone(vuforiaException);
        }
    }

    // An async task to start the camera and trackers
    private static class StartVuforiaTask extends AsyncTask<Void, Void, Boolean>
    {
        VuforiaApplicationException vuforiaException = null;

        private final WeakReference<VuforiaApplicationSession> appSessionRef;

        StartVuforiaTask(VuforiaApplicationSession session)
        {
            appSessionRef = new WeakReference<>(session);
        }

        protected Boolean doInBackground(Void... params)
        {
            VuforiaApplicationSession session = appSessionRef.get();

            // Prevent the concurrent lifecycle operations:
            synchronized (session.mLifecycleLock)
            {
                try {
                    session.startCameraAndTrackers(session.mCamera);
                }
                catch (VuforiaApplicationException e)
                {
                    Log.e(LOGTAG, "StartVuforiaTask.doInBackground: Could not start AR with exception: " + e);
                    vuforiaException = e;
                }
            }

            return true;
        }

        protected void onPostExecute(Boolean result)
        {
            Log.d(LOGTAG, "StartVuforiaTask.onPostExecute: execution "
                    + (result ? "successful" : "failed"));

            VuforiaApplicationControl sessionControl = appSessionRef.get().mSessionControlRef.get();

            sessionControl.onVuforiaStarted();

            // If an exception was thrown, send it to the application
            // and call onInitARDone to stop the initialization process
            if (vuforiaException != null)
            {
                sessionControl.onInitARDone(vuforiaException);
            }
        }
    }


    private String getInitializationErrorString(int code)
    {
        if (code == INIT_ERRORCODE.INIT_DEVICE_NOT_SUPPORTED)
            return "Failed to initialize Vuforia. This device is not supported.";
        if (code == INIT_ERRORCODE.INIT_NO_CAMERA_ACCESS)
            return "Failed to initialize Vuforia. Camera not accessible";
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_MISSING_KEY)
            return "License missing.";
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_INVALID_KEY)
            return "License invalid.";
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_NO_NETWORK_TRANSIENT)
            return "Unable to contact server. Please try again later.";
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_NO_NETWORK_PERMANENT)
            return "No network available. Please make sure you are connected to the Internet.";
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_CANCELED_KEY)
            return "This app license key has been canceled and may no longer be used. Please get a new license key.";
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_PRODUCT_TYPE_MISMATCH)
            return "Vuforia App key is not valid for this product. Please get a valid key, by logging into your account at developer.vuforia.com and choosing the right product type during project creation.";
        else
        {
            return "Failed to initialize Vuforia.";
        }
    }


    public int getVideoMode()
    {
        return mVideoMode;
    }
}