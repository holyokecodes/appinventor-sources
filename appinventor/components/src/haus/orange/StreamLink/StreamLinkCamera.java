package haus.orange.StreamLink;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.google.appinventor.components.runtime.util.AsynchUtil;

import android.content.Context;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.ParcelFileDescriptor;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

@SuppressWarnings("deprecation")
public class StreamLinkCamera extends SurfaceView implements SurfaceHolder.Callback {
	
	Camera camera;
	SurfaceHolder holder;
	Surface surface;
	MediaRecorder recorder;
	ByteArrayOutputStream dataStream;

	public StreamLinkCamera(Context context, Camera camera) {
		super(context);
		this.camera = camera;
		holder = getHolder();
		holder.addCallback(this);
		recorder = new MediaRecorder();
		dataStream = new ByteArrayOutputStream();
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
		params.set("orientation", "portrait");
		
		camera.setParameters(params);
	
		
		try {
			camera.setPreviewDisplay(holder);
			
			camera.startPreview();
			
			surface = holder.getSurface();
			
			camera.unlock();
			
			startRecording();
			
		} catch(IOException e) {
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Starts the live recording
	 */
	public void startRecording() {
		
		try {
			
		    final ParcelFileDescriptor[] descriptors = ParcelFileDescriptor.createPipe();
		    final ParcelFileDescriptor parcelRead = new ParcelFileDescriptor(descriptors[0]);
		    final ParcelFileDescriptor parcelWrite = new ParcelFileDescriptor(descriptors[1]);
	
		    final InputStream inputStream = new ParcelFileDescriptor.AutoCloseInputStream(parcelRead);
		    
		    recorder.setPreviewDisplay(surface);
		    recorder.setCamera(camera);
		    
		    //recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
		    recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
		    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
		    //recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
		    recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
		    recorder.setVideoEncodingBitRate(2048000);
		    recorder.setOrientationHint(90);
		    recorder.setOutputFile(parcelWrite.getFileDescriptor());
		    recorder.prepare();
	
		    recorder.start();
			
			AsynchUtil.runAsynchronously(new Runnable() {
				
				public void run() {
					try {
		
					    int read;
					    byte[] data = new byte[16384];
		
					    while ((read = inputStream.read(data, 0, data.length)) != -1) {
					    	dataStream.write(data, 0, read);
					    }
		
					    dataStream.flush();
					    parcelWrite.close();
					    inputStream.close();
					}catch(IOException e) {
						e.printStackTrace();
					}
					
				}
				
			});
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Stops the live recording
	 */
	public void stopRecording() {
		if(recorder != null) {
			recorder.stop();
			recorder.reset();
			recorder.release();
		}
	}
	
	/**
	 * Grabs the most recent chunk from the recording
	 * @return the most recent chunk
	 */
	public byte[] getData() {
		
		if(dataStream != null) {
			return dataStream.toByteArray();
		}
		
		return null;
	}
	

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// TODO Auto-generated method stub
		
	}
	
}
