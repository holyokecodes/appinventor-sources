package haus.orange.StreamLink;

import com.pedro.rtplibrary.rtmp.RtmpCamera1;

import android.content.Context;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import net.ossrs.rtmp.ConnectCheckerRtmp;

public class StreamLinkCamera extends SurfaceView implements Callback, ConnectCheckerRtmp {

	
	private RtmpCamera1 rtmpCamera;
	
	private SurfaceHolder holder;
	
	public StreamLinkCamera(Context context) {
		super(context);
		
		holder = getHolder();
		holder.addCallback(this);
		
		rtmpCamera = new RtmpCamera1(this, this);
		rtmpCamera.setReTries(10);
		
	}
	
	
	public void startStreaming() {
		
		if(!rtmpCamera.isStreaming()) {
			
			if(rtmpCamera.prepareAudio() && rtmpCamera.prepareVideo()) {
				rtmpCamera.startStream("rtmp://ingest-hkg.mixer.com:1935/beam/64808445-3wsfqswd9w5qzovt7nuwdsagldfp6ihf");
			}else {
				System.out.println("This device doesn't support streaming");
			}
		}
	}
	

	@Override
	public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
		
		rtmpCamera.startPreview();
		startStreaming();
		
	}

	@Override
	public void surfaceCreated(SurfaceHolder arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder arg0) {
		
		if(rtmpCamera.isStreaming()) {
			rtmpCamera.stopStream();
		}
		
		rtmpCamera.stopPreview();
		
	}

	@Override
	public void onAuthErrorRtmp() {
	
		System.out.println("STREAMSEND - AUTH ERROR");
		
	}

	@Override
	public void onAuthSuccessRtmp() {
		// TODO Auto-generated method stub
		
		System.out.println("STERAMSEND - AUTH SUCCESS");
		
	}

	@Override
	public void onConnectionFailedRtmp(String arg0) {
		
		System.out.println("STREAMSEND - CONNECTION FAILED: " + arg0);
		
	}

	@Override
	public void onConnectionSuccessRtmp() {
	
		System.out.println("STREAMSEND - CONNECTION SUCCESS");
		
	}

	@Override
	public void onDisconnectRtmp() {
		
		System.out.println("STREAMSEND - DISCONNECTED");
		
	}

}
