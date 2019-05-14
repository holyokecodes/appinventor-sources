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
import com.google.appinventor.components.annotations.UsesNativeLibraries;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.common.YaVersion;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.ActivityResultListener;
import com.google.appinventor.components.runtime.util.ErrorMessages;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.Manifest;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Date;

import org.holyokecodes.ai.vuforia.util.SampleApplicationControl;
import org.holyokecodes.ai.vuforia.util.SampleApplicationException;
import org.holyokecodes.ai.vuforia.util.SampleApplicationSession;

import com.vuforia.State;
import com.vuforia.Vuforia;

/**
 * Uses the Vuforia Engine for object recognition.
 * Returns relative x, y, z coordinates of object if recognized.
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
@UsesNativeLibraries(libraries="libVuforia.so")
@UsesLibraries(libraries = "Vuforia.jar")
public class ObjectTracker extends AndroidNonvisibleComponent
        implements ActivityResultListener, Component, SampleApplicationControl {
	private static final String LOG_TAG = ObjectTracker.class.getSimpleName();
    private final ComponentContainer container;

    /* Used to identify the call to startActivityForResult. Will be passed back
    into the resultReturned() callback method. */
    private int requestCode;
    
    
    private SampleApplicationSession vuforiaAppSession;
    
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
    	vuforiaAppSession = new SampleApplicationSession(this, LicenseKey());
    	vuforiaAppSession.initAR(container.$context(), ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    /**
	 * Triggers when a key in the table was updated
	 */
	@SimpleEvent
	public void VuforiaInitialized() {
		EventDispatcher.dispatchEvent(this, "VuforiaInitialized");
	}
	
	/**
	 * Triggers when Vuforia fails to Init
	 */
	@SimpleEvent
	public void VuforiaFailed() {
		EventDispatcher.dispatchEvent(this, "VuforiaFailed");
	}

	@Override
	public boolean doInitTrackers() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean doLoadTrackersData() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean doStartTrackers() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean doStopTrackers() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean doUnloadTrackersData() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean doDeinitTrackers() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void onInitARDone(SampleApplicationException e) {
		if(e != null) {
			VuforiaInitialized();
		}else {
			VuforiaFailed();
		}
		
	}

	@Override
	public void onVuforiaUpdate(State state) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onVuforiaResumed() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onVuforiaStarted() {
		// TODO Auto-generated method stub
		
	}
    
}




