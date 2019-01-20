// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2018 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package org.holyokecodes.ai.vuforia;

import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.annotations.UsesAssets;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.annotations.UsesLibraries;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.common.YaVersion;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.ActivityResultListener;
import com.google.appinventor.components.runtime.util.ErrorMessages;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.Manifest;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Date;

import org.holyokecodes.ai.vuforia.util.VuforiaApplicationSession;
import com.vuforia.Vuforia;

/**
 * Uses the Vuforia Engine for object recognition.
 * Returns relative x, y, z coordinates of object if recogized.
 */
@DesignerComponent(version = 1,
        description = "Object Recognition with Vuforia",
        category = ComponentCategory.EXTENSION,
        nonVisible = true,
        iconName = "images/extension.png")
@SimpleObject(external=true)
@UsesPermissions(permissionNames = "android.permission.WRITE_EXTERNAL_STORAGE, android.permission.READ_EXTERNAL_STORAGE," +
        "android.permission.CAMERA")
@UsesAssets(fileNames = "Test2_OT.dat, Test2_OT.xml")
@UsesLibraries(libraries = "Vuforia.jar")
public class ObjectTracker extends AndroidNonvisibleComponent
        implements ActivityResultListener, Component {
	private static final String LOG_TAG = ObjectTracker.class.getSimpleName();
	private final WeakReference<VuforiaApplicationSession> appSessionRef;
    private final ComponentContainer container;

    /* Used to identify the call to startActivityForResult. Will be passed back
    into the resultReturned() callback method. */
    private int requestCode;
    
    /**
     * Creates a Vuforia component.
     *
     * @param container container, component will be placed in
     */
    
    public ObjectTracker(ComponentContainer container) {
        super(container.$form());
        this.container = container;
    }
    
    private String licenseKey;
    private String modelPath = "";

    /**
     * Returns the Vuforia License Key
     *
     * @return license key string
     */
    @SimpleProperty(
            description = "Vuforia License Key",
            category = PropertyCategory.APPEARANCE)
    public String LicenseKey() {
        return licenseKey;
    }

    /**
     * Specifies the Vuforia License Key
     *
     * @param key
     */
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING)
    @SimpleProperty
    public void LicenseKey(String key) {
        licenseKey = key;
    }

    /**
     * Returns the path of the Vuforia object model.
     *
     * @return  the path of the Vuforia object model
     */
    @SimpleProperty(
    		description = "The Vuforia Object Model",
            category = PropertyCategory.APPEARANCE)
    public String Model() {
        return modelPath;
    }

    /**
     * Specifies the path of the Vuforia object model.
     *
     * @param path  the path of the object model
     */
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_ASSET,
            defaultValue = "")
    @SimpleProperty
    public void Model(String path) {
        modelPath = (path == null) ? "" : path;
    }

    @Override
    public void resultReturned(int requestCode, int resultCode, Intent data) {
        Log.i(LOG_TAG,
                "Returning result. Request code = " + requestCode + ", result code = " + resultCode);
    }

    @SimpleFunction
    public void InitVuforia() {
    	int mProgressValue = -1;
    	
    	VuforiaApplicationSession session = appSessionRef.get();
    	
        Vuforia.setInitParameters(session.mActivityRef.get(), session.mVuforiaFlags, "AYtvymv/////AAABmcNw6ZzgC0SsmCu/EFCoUs1qP6UKlOpK8XbV6Qn+NYHJw/altw/Cib//l3nRHVJguJ5j3xp3C3vcw5oADSpy8aLLoXapGBQIJjeudB9TmSMnswrrloG+ghh4vOHfFwADu4S85WwkZE6IcuQF6RvsVJo7bTTxWC6UTOAgrYAINHu3ZOkyFl0TMMwSQhf783HlLHfm8ADoHbRB1BTS5YGYwBS54kmkhmxRWvKcAZAOqaRScP+qWhzDp0pXFSG/XtuNvOA4ZBDXAqWvqS9kCX6t55ICBrbKViZcpwW1Bv6PPNZzThud5BqDBtyNOjTdqn0/O0FyOzPoteNFjggxdze0UWj+XotYOKdBj70mQohNMfMN");
       
        do
        {
            // Vuforia.init() blocks until an initialization step is
            // complete, then it proceeds to the next step and reports
            // progress in percents (0 ... 100%).
            // If Vuforia.init() returns -1, it indicates an error.
            // Initialization is done when progress has reached 100%.
            mProgressValue = Vuforia.init();
                                  
            // We check whether the task has been canceled in the
            // meantime (by calling AsyncTask.cancel(true)).
            // and bail out if it has, thus stopping this thread.
            // This is necessary as the AsyncTask will run to completion
            // regardless of the status of the component that
            // started is.
        } while (mProgressValue >= 0 && mProgressValue < 100);
  
        return;

    }

    @SimpleEvent
    public void ObjectRecognized() {
    }
    
}




