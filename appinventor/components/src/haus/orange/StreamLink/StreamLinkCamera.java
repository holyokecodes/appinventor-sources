package haus.orange.StreamLink;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.google.appinventor.components.runtime.util.AsynchUtil;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.media.CameraProfile;
import android.media.MediaRecorder;
import android.os.ParcelFileDescriptor;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

@SuppressWarnings("deprecation")
public class StreamLinkCamera extends SurfaceView implements SurfaceHolder.Callback {

	SurfaceHolder holder;
	Surface surface;
	MediaRecorder recorder;
	ByteArrayOutputStream dataStream;
	boolean hasStarted;

	public StreamLinkCamera(Context context) {
		super(context);
		holder = getHolder();
		holder.addCallback(this);
		dataStream = new ByteArrayOutputStream();
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int arg1, int arg2, int arg3) {
		// TODO Auto-generated method stub

	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {

		surface = holder.getSurface();

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
			
			/**
			 * 
			 * Problem is unique to devices, not a single issue
			 * 
			 * 
			 * PROBLEM BETWEEN HERE
			 */
			
			recorder = new MediaRecorder();
			
			recorder.setPreviewDisplay(surface);
			recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
			recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
			recorder.setOutputFile(parcelWrite.getFileDescriptor());

			CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
			recorder.setProfile(profile);
			
			/**
			 * AND HERE
			 */

			if (!hasStarted) {
				recorder.prepare();
				recorder.start();
				hasStarted = true;
			}

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
					} catch (IOException e) {
						e.printStackTrace();
					}

				}

			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Stops the live recording
	 */
	public void stopRecording() {
		if (recorder != null) {
			recorder.stop();
			recorder.reset();
			recorder.release();
			hasStarted = false;
		}
	}

	/**
	 * Grabs the most recent chunk from the recording
	 * 
	 * @return the most recent chunk
	 */
	public byte[] getData() {

		if (dataStream != null) {
			return dataStream.toByteArray();
		}

		return null;
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// TODO Auto-generated method stub

	}

}
