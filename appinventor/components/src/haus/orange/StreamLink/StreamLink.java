package haus.orange.StreamLink;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon.ScalingType;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFileRenderer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.annotations.UsesLibraries;
import com.google.appinventor.components.annotations.UsesNativeLibraries;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Canvas;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;
import com.google.appinventor.components.runtime.PermissionResultHandler;

import android.support.annotation.Nullable;
import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.support.annotation.UiThread;
import android.view.ViewGroup;
import android.widget.Toast;
import haus.orange.StreamLink.socketio.SocketIOClient;
import haus.orange.StreamLink.socketio.SocketIOEvents;
import haus.orange.webrtc.AppRTCAudioManager;
import haus.orange.webrtc.AppRTCAudioManager.AudioDevice;
import haus.orange.webrtc.AppRTCAudioManager.AudioManagerEvents;
import haus.orange.webrtc.AppRTCClient;
import haus.orange.webrtc.AppRTCClient.RoomConnectionParameters;
import haus.orange.webrtc.AppRTCClient.SignalingParameters;
import haus.orange.webrtc.DirectRTCClient;
import haus.orange.webrtc.PeerConnectionClient;
import haus.orange.webrtc.PeerConnectionClient.PeerConnectionEvents;
import haus.orange.webrtc.PeerConnectionClient.PeerConnectionParameters;
import haus.orange.webrtc.WebSocketRTCClient;

/*
Jacob Bashista 5/27/19

Link is a component designed to allow
devices to communicate across networks.
*/

@DesignerComponent(version = 6, description = "Allows Streaming Data Across Networks", category = ComponentCategory.MEDIA, nonVisible = true, iconName = "https://orange.haus/link/icon5.png")
@SimpleObject(external = false)
@UsesLibraries(libraries = "okio.jar, okhttp.jar, engineio.jar, socketio.jar, autobahn.jar, webrtc.jar")
@UsesNativeLibraries(v7aLibraries = "libjingle_peerconnection_so.so", v8aLibraries = "libjingle_peerconnection_so.so", x86_64Libraries = "libjingle_peerconnection_so.so")
@UsesPermissions(permissionNames = "android.permission.RECORD_AUDIO, android.permission.INTERNET, android.permission.WRITE_EXTERNAL_STORAGE, android.permission.CAMERA")
public class StreamLink extends AndroidNonvisibleComponent
		implements Component, SocketIOEvents, AppRTCClient.SignalingEvents, PeerConnectionEvents {

	public String socketServerAddress;
	public String apprtcServerAddress;
	private String deviceID;
	public String linkCode;
	public boolean alwaysShowPreview;
	public String defaultCamera;

	private SocketIOClient socketClient;

	private ComponentContainer container;

	private SurfaceViewRenderer videoView;
	private SurfaceViewRenderer hiddenView;

	private static final int STAT_CALLBACK_PERIOD = 1000;
	private final ProxyVideoSink remoteProxyRenderer = new ProxyVideoSink();
	private final ProxyVideoSink localProxyVideoSink = new ProxyVideoSink();
	@Nullable
	private PeerConnectionClient peerConnectionClient;
	@Nullable
	private AppRTCClient appRtcClient;
	@Nullable
	private SignalingParameters signalingParameters;
	@Nullable
	private AppRTCAudioManager audioManager;
	@Nullable
	private SurfaceViewRenderer localRenderer;
	@Nullable
	private SurfaceViewRenderer remoteRenderer;
	@Nullable
	private VideoFileRenderer videoFileRenderer;
	private final List<VideoSink> remoteSinks = new ArrayList<>();
	private RoomConnectionParameters roomConnectionParameters;
	@Nullable
	private PeerConnectionParameters peerConnectionParameters;
	private Toast logToast;
	private boolean commandLineRun;
	private boolean activityRunning;
	private boolean connected;
	private boolean isError;
	private boolean callControlFragmentVisible = true;
	private long callStartedTimeMs;
	private boolean micEnabled = true;
	private boolean screencaptureEnabled;

	private ViewGroup parent;
	private Canvas canvas;

	Timer timer;

	private boolean hasStoragePerm = false;
	private boolean hasAudioPerm = false;
	private boolean hasCameraPerm = false;
	private boolean hasInternetPerm = false;

	public StreamLink(ComponentContainer container) {
		super(container.$form());

		this.container = container;

		checkStoragePermission();

		socketServerAddress = "https://stream-link.herokuapp.com/";
		apprtcServerAddress = "https://streamlink-255116.appspot.com";
		alwaysShowPreview = false;
		if (!hasStoragePerm) {
			deviceID = getDeviceID(this.container.$context());
		}
		linkCode = "0000";
		defaultCamera = "FRONT";
		socketClient = new SocketIOClient(container, this, socketServerAddress);
		timer = new Timer();
		timer.schedule(new CheckInView(), 0, 500);
	}

	private void checkStoragePermission() {
		if (!hasStoragePerm) {
			final StreamLink me = this;
			form.askPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, new PermissionResultHandler() {
				@Override
				public void HandlePermissionResponse(String permission, boolean granted) {
					if (granted) {
						me.hasStoragePerm = true;
						me.checkAudioPermission();
					} else {
						me.logAndToast("Failed to get Storage Permission");
					}
				}
			});
		}
	}

	private void checkAudioPermission() {
		if (!hasAudioPerm) {
			final StreamLink me = this;
			form.askPermission(Manifest.permission.RECORD_AUDIO, new PermissionResultHandler() {
				@Override
				public void HandlePermissionResponse(String permission, boolean granted) {
					if (granted) {
						me.hasAudioPerm = true;
						checkCameraPermission();
					} else {
						me.logAndToast("Failed to get Audio Permission");
					}
				}
			});
		}
	}

	private void checkCameraPermission() {
		if (!hasCameraPerm) {
			final StreamLink me = this;
			form.askPermission(Manifest.permission.CAMERA, new PermissionResultHandler() {
				@Override
				public void HandlePermissionResponse(String permission, boolean granted) {
					if (granted) {
						me.hasCameraPerm = true;
						me.checkInternetPermission();
					} else {
						me.logAndToast("Failed to get Camera Permission");
					}
				}
			});
		}
	}

	private void checkInternetPermission() {
		if (!hasInternetPerm) {
			final StreamLink me = this;
			form.askPermission(Manifest.permission.INTERNET, new PermissionResultHandler() {
				@Override
				public void HandlePermissionResponse(String permission, boolean granted) {
					if (granted) {
						me.hasInternetPerm = true;
					} else {
						me.logAndToast("Failed to get Internet Permission");
					}
				}
			});
		}
	}

	private void initWebRTC(String roomID, Canvas canvas) {

		final EglBase eglBase = EglBase.create();

		connected = false;
		signalingParameters = null;

		if (videoView == null) {
			videoView = new SurfaceViewRenderer(this.container.$context());
		}

		if (hiddenView == null) {
			hiddenView = new SurfaceViewRenderer(this.container.$context());
		}

		replaceViewWithCamera(canvas, videoView);

		remoteSinks.add(remoteProxyRenderer);

		videoView.init(eglBase.getEglBaseContext(), null);
		videoView.setScalingType(ScalingType.SCALE_ASPECT_FIT);

		videoView.setEnableHardwareScaler(true);

		hiddenView.init(eglBase.getEglBaseContext(), null);
		hiddenView.setScalingType(ScalingType.SCALE_ASPECT_FIT);

		hiddenView.setEnableHardwareScaler(true);

		localProxyVideoSink.setTarget(videoView);
		remoteProxyRenderer.setTarget(hiddenView);

		connectVideoCall(eglBase, roomID);
	}

	// If you want to send video
	private void connectVideoCall(EglBase eglBase, String roomID) {

		Uri roomUri = Uri.parse(apprtcServerAddress);

		int videoWidth = 0;
		int videoHeight = 0;
		int videoFrameRate = 0;

		peerConnectionParameters = new PeerConnectionParameters(true, false, false, videoWidth, videoHeight,
				videoFrameRate, 1700, "VP8", true, false, 32, "OPUS", false, false, false, false, false, false, false,
				false, false, null);
		commandLineRun = false;
		int runTimeMs = 0;

		if (false || !DirectRTCClient.IP_PATTERN.matcher(roomID).matches()) {
			appRtcClient = new WebSocketRTCClient(this);
		} else {
			logAndToast("Using DirectRTCClient because room name looks like an IP.");
			appRtcClient = new DirectRTCClient(this);
		}
		roomConnectionParameters = new AppRTCClient.RoomConnectionParameters(roomUri.toString(), roomID, false, null);

		peerConnectionClient = new PeerConnectionClient(this.container.$context(), eglBase, peerConnectionParameters,
				this);

		PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
		peerConnectionClient.createPeerConnectionFactory(options);

		startCall();
	}

	private void startCall() {
		if (appRtcClient == null) {
			logAndToast("AppRTC client is not allocated for a call.");
			return;
		}
		callStartedTimeMs = System.currentTimeMillis();

		// Start room connection.
		logAndToast("Connecting To: " + roomConnectionParameters.roomUrl);
		appRtcClient.connectToRoom(roomConnectionParameters);
		audioManager = AppRTCAudioManager.create(this.container.$context());
		audioManager.start(new AudioManagerEvents() {
			// This method will be called each time the number of available audio
			// devices has changed.
			@Override
			public void onAudioDeviceChanged(AudioDevice audioDevice, Set<AudioDevice> availableAudioDevices) {
				onAudioManagerDevicesChanged(audioDevice, availableAudioDevices);
			}
		});

	}

	@UiThread
	private void callConnected() {
		final long delta = System.currentTimeMillis() - callStartedTimeMs;
		System.out.println("Call connected: delay=" + delta + "ms");
		if (peerConnectionClient == null || isError) {
			System.out.println("Call is connected in closed or error state");
			return;
		}
		// Enable statistics callback.
		peerConnectionClient.enableStatsEvents(true, STAT_CALLBACK_PERIOD);

		if (!alwaysShowPreview) {
			localProxyVideoSink.setTarget(hiddenView);
			remoteProxyRenderer.setTarget(videoView);
		}
	}

	private void onAudioManagerDevicesChanged(final AudioDevice device, final Set<AudioDevice> availableDevices) {
		logAndToast("onAudioManagerDevicesChanged: " + availableDevices + ", " + "selected: " + device);
		// TODO(henrika): add callback handler.
	}

	private void disconnect() {
		activityRunning = false;
		remoteProxyRenderer.setTarget(null);
		localProxyVideoSink.setTarget(null);
		if (appRtcClient != null) {
			appRtcClient.disconnectFromRoom();
			appRtcClient = null;
		}

		if (videoView != null) {
			if (videoView.getParent() == this.parent) {
				this.parent.removeView(videoView);
				this.parent.addView(this.canvas.getView());
			}
			videoView.release();
			videoView = null;
		}

		if (hiddenView != null) {
			hiddenView.release();
			hiddenView = null;
		}

		if (peerConnectionClient != null) {
			peerConnectionClient.close();
			peerConnectionClient = null;
		}
		if (audioManager != null) {
			audioManager.stop();
			audioManager = null;
		}
		if (connected && !isError) {
			// setResult(RESULT_OK);
		} else {
			// setResult(RESULT_CANCELED);
		}
		// finish();
	}

	private void replaceViewWithCamera(Canvas canvas, SurfaceViewRenderer video) {

		video.setLayoutParams(canvas.getView().getLayoutParams());

		this.parent = (ViewGroup) canvas.getView().getParent();

		if (parent != null) {

			this.canvas = canvas;

			parent.removeView(video);
			parent.addView(video);

			parent.removeView(canvas.getView());
		}
	}

	private String getDeviceID(Context context) {

		SharedPreferences prefs = context.getSharedPreferences("haus.orange.streamlink", Context.MODE_PRIVATE);

		String foundID = prefs.getString("deviceID", "none");

		if (foundID.equals("none")) {
			foundID = UUID.randomUUID().toString();

			Editor editor = prefs.edit();

			editor.putString("deviceID", foundID);
			editor.commit();
		}

		return foundID;
	}

	/**
	 * Returns the socket servers address
	 *
	 * @return the server ip address as a string
	 */
	@SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Server IP for StreamLink")
	public String SocketServerAddress() {
		return socketServerAddress;
	}

	/**
	 * Sets the socket servers address
	 *
	 * @param
	 */
	@DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "https://stream-link.herokuapp.com/")
	@SimpleProperty(category = PropertyCategory.BEHAVIOR)
	public void SocketServerAddress(String address) {
		socketServerAddress = address;
	}

	/**
	 * Returns if the app should always show the preview, instead of switching to
	 * the remote view
	 *
	 * @return alwaysShowPreview boolean
	 */
	@SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Should the app always show the preview")
	public boolean AlwaysShowLocalVideo() {
		return alwaysShowPreview;
	}

	/**
	 * Tells the app if it should always show the preview window
	 *
	 * @param
	 */
	@DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN, defaultValue = "false")
	@SimpleProperty(category = PropertyCategory.BEHAVIOR)
	public void AlwaysShowLocalVideo(boolean showPreview) {
		alwaysShowPreview = showPreview;
	}

	/**
	 * Returns the default camera the app should use
	 *
	 * @return default camera string
	 */
	@SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Default camera to use (FRONT OR REAR)")
	public String DefaultCamera() {
		return defaultCamera;
	}

	/**
	 * Tells the app what camera is should use by default
	 *
	 * @param FRONT OR REAR
	 */
	@DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "FRONT")
	@SimpleProperty(category = PropertyCategory.BEHAVIOR)
	public void DefaultCamera(String defaultCamera) {
		this.defaultCamera = defaultCamera;
	}

	/**
	 * Returns the apprtc servers address
	 *
	 * @return the server url address as a string
	 */
	@SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Server IP for AppRTC Instance")
	public String AppRTCServerAddress() {
		return apprtcServerAddress;
	}

	/**
	 * Sets the apprtc servers address
	 *
	 * @param
	 */
	@DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "https://streamlink-255116.appspot.com")
	@SimpleProperty(category = PropertyCategory.BEHAVIOR)
	public void AppRTCServerAddress(String address) {
		apprtcServerAddress = address;
	}

	@SimpleFunction
	public void CreateVideoCall(String roomID, Canvas canvas) {

		checkStoragePermission();
		if (this.hasAudioPerm && this.hasCameraPerm && this.hasStoragePerm && this.hasInternetPerm) {
			initWebRTC(roomID, canvas);
		} else {
			logAndToast("Missing required permissions (Audio, Camera, Storage, Internet)");
		}
	}

	@SimpleFunction
	public void DisconnectVideoCall() {
		disconnect();
	}

	@SimpleFunction
	public void SwitchCamera() {
		if (peerConnectionClient != null) {
			peerConnectionClient.switchCamera();
		}
	}

	/**
	 * Creates a link with the password specified
	 * 
	 * @param password    password to apply to the link
	 * @param description description for the link
	 */
	@SimpleFunction
	public void CreateLink(String password, String description) {
		checkStoragePermission();
		if (this.hasAudioPerm && this.hasCameraPerm && this.hasStoragePerm && this.hasInternetPerm) {
			socketClient.createLink(this.deviceID, password, description);
		} else {
			logAndToast("Missing required permissions (Audio, Camera, Storage, Internet)");
		}

	}

	/**
	 * Connects the device to a Link
	 * 
	 * @param linkCode link code for the specified Link
	 * @param password password for the specified Link
	 */
	@SimpleFunction
	public void ConnectToLink(String linkCode, String password) {
		checkStoragePermission();
		if (this.hasAudioPerm && this.hasCameraPerm && this.hasStoragePerm && this.hasInternetPerm) {
			socketClient.connectToLink(this.deviceID, linkCode, password);
		} else {
			logAndToast("Missing required permissions (Audio, Camera, Storage, Internet)");
		}

	}

	/**
	 * Checks if the device is connected to the server
	 * 
	 * @return connection status
	 */
	@SimpleFunction
	public boolean IsSocketConnected() {
		return socketClient.isConnected();
	}

	/**
	 * Sends a Message to the Link
	 * 
	 * @param name    name of the message to identify it
	 * @param message message to be sent
	 */
	@SimpleFunction
	public void SendMessage(String name, String message) {
		socketClient.sendMessage(name, message);
	}

	/**
	 * Sends a Image to the Link
	 * 
	 * @param name  name of the image to identify it
	 * @param image path to image file
	 */
	@SimpleFunction
	public void SendImage(String name, String image) {

		socketClient.sendImage(name, image);

	}

	/**
	 * Runs after ConnectToLink is successful
	 */
	@SimpleEvent
	public void OnLinkConnected(String linkCode, String description) {
		EventDispatcher.dispatchEvent(this, "OnLinkConnected", linkCode, description);
	}

	/**
	 * Runs when a message is received
	 * 
	 * @param name    identifier for message
	 * @param message message that was sent
	 */
	@SimpleEvent
	public void OnMessageReceived(String name, String message) {
		EventDispatcher.dispatchEvent(this, "OnMessageReceived", name, message);
	}

	/**
	 * Runs when a image is received
	 * 
	 * @param name  identifier for image
	 * @param image path to the image file
	 */
	@SimpleEvent
	public void OnImageReceived(String name, String image) {
		EventDispatcher.dispatchEvent(this, "OnImageReceived", name, image);
	}

	@Override
	public void LinkConnected(String linkCode, String description) {
		OnLinkConnected(linkCode, description);
	}

	@Override
	public void MessageReceived(String name, String message) {
		OnMessageReceived(name, message);

	}

	@Override
	public void ImageReceived(String name, String image) {
		OnImageReceived(name, image);
	}

	@Override
	public void onLocalDescription(final SessionDescription sdp) {
		final long delta = System.currentTimeMillis() - callStartedTimeMs;
		this.container.$context().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (appRtcClient != null) {
					logAndToast("Sending " + sdp.type + ", delay=" + delta + "ms");
					if (signalingParameters.initiator) {
						appRtcClient.sendOfferSdp(sdp);
					} else {
						appRtcClient.sendAnswerSdp(sdp);
					}
				}
				if (peerConnectionParameters.videoMaxBitrate > 0) {
					logAndToast("Set video maximum bitrate: " + peerConnectionParameters.videoMaxBitrate);
					peerConnectionClient.setVideoMaxBitrate(peerConnectionParameters.videoMaxBitrate);
				}
			}
		});
	}

	@Override
	public void onIceCandidate(final IceCandidate candidate) {
		this.container.$context().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (appRtcClient != null) {
					appRtcClient.sendLocalIceCandidate(candidate);
				}
			}
		});
	}

	@Override
	public void onIceCandidatesRemoved(final IceCandidate[] candidates) {
		this.container.$context().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (appRtcClient != null) {
					appRtcClient.sendLocalIceCandidateRemovals(candidates);
				}
			}
		});
	}

	@Override
	public void onIceConnected() {
		final long delta = System.currentTimeMillis() - callStartedTimeMs;
		this.container.$context().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				logAndToast("ICE connected, delay=" + delta + "ms");
			}
		});
	}

	@Override
	  public void onIceDisconnected() {
	    this.container.$context().runOnUiThread(new Runnable() {
	      @Override
	      public void run() {
	        logAndToast("ICE disconnected");
	      }
	    });
	  }

	@Override
	public void onPeerConnectionClosed() {
		// DO NOTHING
	}

	@Override
	public void onPeerConnectionStatsReady(StatsReport[] reports) {
		// DO NOTHING
	}

	@Override
	public void onPeerConnectionError(String description) {
		// DO NOTHING
	}

	private boolean useCamera2() {
		return Camera2Enumerator.isSupported(this.container.$context());
	}

	private @Nullable VideoCapturer createVideoCapturer() {
		final VideoCapturer videoCapturer;
		if (useCamera2()) {
			logAndToast("Creating capturer using camera2 API.");
			videoCapturer = createCameraCapturer(new Camera2Enumerator(this.container.$context()));
		} else {
			logAndToast("Creating capturer using camera1 API.");
			videoCapturer = createCameraCapturer(new Camera1Enumerator(false));
		}
		if (videoCapturer == null) {
			reportError("Failed to open camera");
			return null;
		}
		return videoCapturer;
	}

	private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
		final String[] deviceNames = enumerator.getDeviceNames();
		// First, try to find front facing camera
		logAndToast("Looking for front facing cameras.");
		for (String deviceName : deviceNames) {
			if (enumerator.isFrontFacing(deviceName)) {
				logAndToast("Creating front facing camera capturer.");
				VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
				if (videoCapturer != null) {
					return videoCapturer;
				}
			}
		}
		// Front facing camera not found, try something else
		logAndToast("Looking for other cameras.");
		for (String deviceName : deviceNames) {
			if (!enumerator.isFrontFacing(deviceName)) {
				logAndToast("Creating other camera capturer.");
				VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
				if (videoCapturer != null) {
					return videoCapturer;
				}
			}
		}
		return null;
	}

	private void onConnectedToRoomInternal(final SignalingParameters params) {
		final long delta = System.currentTimeMillis() - callStartedTimeMs;
		signalingParameters = params;
		logAndToast("Creating peer connection, delay=" + delta + "ms");
		VideoCapturer videoCapturer = null;
		if (peerConnectionParameters.videoCallEnabled) {
			videoCapturer = createVideoCapturer();
		}
		peerConnectionClient.createPeerConnection(localProxyVideoSink, remoteSinks, videoCapturer, signalingParameters);
		if (signalingParameters.initiator) {
			logAndToast("Creating OFFER...");
			// Create offer. Offer SDP will be sent to answering client in
			// PeerConnectionEvents.onLocalDescription event.
			peerConnectionClient.createOffer();
		} else {
			if (params.offerSdp != null) {
				peerConnectionClient.setRemoteDescription(params.offerSdp);
				logAndToast("Creating ANSWER...");
				// Create answer. Answer SDP will be sent to offering client in
				// PeerConnectionEvents.onLocalDescription event.
				peerConnectionClient.createAnswer();
			}
			if (params.iceCandidates != null) {
				// Add remote ICE candidates from room.
				for (IceCandidate iceCandidate : params.iceCandidates) {
					peerConnectionClient.addRemoteIceCandidate(iceCandidate);
				}
			}
		}
	}

	@Override
	public void onConnectedToRoom(final SignalingParameters params) {
		this.container.$context().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				onConnectedToRoomInternal(params);
			}
		});
	}

	@Override
	public void onRemoteDescription(final SessionDescription sdp) {
		final long delta = System.currentTimeMillis() - callStartedTimeMs;
		this.container.$context().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (peerConnectionClient == null) {
					logAndToast("Received remote SDP for non-initilized peer connection.");
					return;
				}
				logAndToast("Received remote " + sdp.type + ", delay=" + delta + "ms");
				peerConnectionClient.setRemoteDescription(sdp);
				if (!signalingParameters.initiator) {
					logAndToast("Creating ANSWER...");
					// Create answer. Answer SDP will be sent to offering client in
					// PeerConnectionEvents.onLocalDescription event.
					peerConnectionClient.createAnswer();
				}
			}
		});
	}

	@Override
	public void onRemoteIceCandidate(final IceCandidate candidate) {
		this.container.$context().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (peerConnectionClient == null) {
					logAndToast("Received ICE candidate for a non-initialized peer connection.");
					return;
				}
				peerConnectionClient.addRemoteIceCandidate(candidate);
			}
		});
	}

	@Override
	public void onRemoteIceCandidatesRemoved(final IceCandidate[] candidates) {
		this.container.$context().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (peerConnectionClient == null) {
					logAndToast("Received ICE candidate removals for a non-initialized peer connection.");
					return;
				}
				peerConnectionClient.removeRemoteIceCandidates(candidates);
			}
		});
	}

	@Override
	public void onChannelClose() {
		this.container.$context().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				logAndToast("Remote end hung up; dropping PeerConnection");
				disconnect();
			}
		});
	}

	@Override
	public void onChannelError(final String description) {
		reportError(description);
	}

	private static class ProxyVideoSink implements VideoSink {
		private VideoSink target;

		@Override
		synchronized public void onFrame(VideoFrame frame) {
			if (target == null) {
				System.out.println("Dropping frame in proxy because target is null.");
				return;
			}
			target.onFrame(frame);
		}

		synchronized public void setTarget(VideoSink target) {
			this.target = target;
		}
	}

	private void disconnectWithErrorMessage(final String errorMessage) {
		if (!activityRunning) {
			System.out.println("Critical error: " + errorMessage);
			disconnect();
		} else {
			new AlertDialog.Builder(this.container.$context()).setTitle("Error").setMessage(errorMessage)
					.setCancelable(false).setNeutralButton("OK", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int id) {
							dialog.cancel();
							disconnect();
						}
					}).create().show();
		}
	}

	private void logAndToast(String msg) {
		System.out.println(msg);
		if (logToast != null) {
			logToast.cancel();
		}
		logToast = Toast.makeText(this.container.$context(), msg, Toast.LENGTH_SHORT);
		logToast.show();
	}

	private void reportError(final String description) {
		this.container.$context().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (!isError) {
					isError = true;
					disconnectWithErrorMessage(description);
				}
			}
		});
	}

	class CheckInView extends TimerTask {
		@Override
		public void run() {

			if (videoView != null && videoView.getParent() != parent) {
				disconnect();
			}
		}
	}

	@Override
	public void onConnected() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onDisconnected() {
		// TODO Auto-generated method stub

	}

}
