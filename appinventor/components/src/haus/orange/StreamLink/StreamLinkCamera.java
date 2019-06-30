package haus.orange.StreamLink;

import java.io.IOException;

import android.content.Context;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class StreamLinkCamera extends SurfaceView implements SurfaceHolder.Callback {
	
	Camera camera;
	SurfaceHolder holder;

	public StreamLinkCamera(Context context, Camera camera) {
		super(context);
		this.camera = camera;
		holder = getHolder();
		holder.addCallback(this);
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int arg1, int arg2, int arg3) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		
		Camera.Parameters params = camera.getParameters();
		
		camera.setDisplayOrientation(90);
		params.setRotation(90);
		params.setPreviewFrameRate(30);
	
		
		try {
			camera.setPreviewDisplay(holder);
			
			
			camera.startPreview();
		} catch(IOException e) {
			e.printStackTrace();
		}
		
		
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// TODO Auto-generated method stub
		
	}
	
}
