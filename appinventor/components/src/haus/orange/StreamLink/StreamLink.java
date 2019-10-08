package haus.orange.StreamLink;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.IceCandidate;
import org.webrtc.RendererCommon.ScalingType;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSink;
 
import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.annotations.UsesLibraries;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Canvas;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;

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
import haus.orange.StreamLink.webrtc.AppRTCClient;
import haus.orange.StreamLink.webrtc.AppRTCClient.SignalingParameters;
import haus.orange.StreamLink.webrtc.PeerConnectionClient;
import haus.orange.StreamLink.webrtc.WebSocketRTCClient;

/*
Jacob Bashista 5/27/19

Link is a component designed to allow
devices to communicate across networks.
*/

@DesignerComponent(version = 6, description = "Allows Streaming Data Across Networks", category = ComponentCategory.EXTENSION, nonVisible = true, iconName = "https://orange.haus/link/icon5.png")
@SimpleObject(external = true)
@UsesLibraries(libraries = "okio.jar, okhttp.jar, engineio.jar, socketio.jar, autobahn.jar")
@UsesPermissions(permissionNames = "android.permission.RECORD_AUDIO, android.permission.INTERNET, android.permission.WRITE_EXTERNAL_STORAGE, android.permission.CAMERA")
public class StreamLink extends AndroidNonvisibleComponent implements Component, SocketIOEvents, AppRTCClient.SignalingEvents,
PeerConnectionClient.PeerConnectionEvents {

	public String socketServerAddress;
	public String webRTCAddress;
	private String deviceID;
	public String linkCode;
	
	private SocketIOClient socketClient;
	
	private ComponentContainer container;
	
	private SurfaceViewRenderer videoView;
	
	private static final int STAT_CALLBACK_PERIOD = 1000;
	private PeerConnectionClient peerConnectionClient = null;
    private AppRTCClient appRtcClient;
	private AppRTCClient.SignalingParameters signalingParameters;
	private boolean iceConnected;
	private AppRTCClient.RoomConnectionParameters roomConnectionParameters;
	private final List<VideoRenderer.Callbacks> remoteRenderers = new ArrayList<>();
	private final ProxyRenderer remoteProxyRenderer = new ProxyRenderer();
	private PeerConnectionClient.PeerConnectionParameters peerConnectionParameters;
	private long callStartedTimeMs = 0;
	private boolean isError;
	private final ProxyVideoSink localProxyVideoSink = new ProxyVideoSink();
	private boolean activityRunning;
	private Toast logToast;

	private ViewGroup parent;
	private Canvas canvas;
	
	public StreamLink(ComponentContainer container) {
		super(container.$form());

		this.container = container;

		socketServerAddress = "https://stream-link.herokuapp.com/";
		deviceID = getDeviceID();
		linkCode = "0000";
		
		socketClient = new SocketIOClient(container, this, socketServerAddress);
	}
	
	
	private void initWebRTC(String roomID, Canvas canvas, boolean sendVideo) {
		
		iceConnected = false;
        signalingParameters = null;
		
        if(videoView == null) {
        	videoView = new SurfaceViewRenderer(this.container.$context());
        }
		
		replaceViewWithCamera(canvas, videoView);
		
		
		remoteRenderers.add(remoteProxyRenderer);
		
		peerConnectionClient = new PeerConnectionClient();
		
		videoView.init(peerConnectionClient.getRenderContext(), null);
		videoView.setScalingType(ScalingType.SCALE_ASPECT_FILL);
		
		videoView.setEnableHardwareScaler(true);
		
		connectVideoCall(roomID, sendVideo);
	}
	
	// If you want to send video
	private void connectVideoCall(String roomID, boolean sendVideo) {
		Uri roomUri = Uri.parse("https://appr.tc");
		
		int videoWidth = 0;
        int videoHeight = 0;
        int videoFrameRate = 0;
        
        if(!sendVideo) {
        	videoWidth = 320;
        	videoHeight = 240;
        	videoFrameRate = 10;
        }
        
        
        peerConnectionParameters =
                new PeerConnectionClient.PeerConnectionParameters(true,
                        false,
                        false,
                        videoWidth,
                        videoHeight,
                        videoFrameRate,
                        1700,
                        "VP8",
                        true,
                        false,
                        32,
                        "OPUS",
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        null);      
        
        appRtcClient = new WebSocketRTCClient(this);
        roomConnectionParameters =
                new AppRTCClient.RoomConnectionParameters(
                        roomUri.toString(),
                        roomID,
                        false,
                        null);
        
        peerConnectionClient.createPeerConnectionFactory(
                this.container.$context(), peerConnectionParameters, this);
        
        startCall();   
	}

	private void startCall() {
        if (appRtcClient == null) {
            System.out.println("AppRTC client is not allocated for a call.");
            return;
        }
        callStartedTimeMs = System.currentTimeMillis();

        // Start room connection.
        logAndToast("Connecting To: " + roomConnectionParameters.roomUrl);
        appRtcClient.connectToRoom(roomConnectionParameters);
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
        
        remoteProxyRenderer.setTarget(videoView);
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
        	this.parent.removeView(videoView);
        	this.parent.addView(this.canvas.getView());
        	videoView.release();
        	videoView = null;
        }
        if (peerConnectionClient != null) {
            peerConnectionClient.close();
            peerConnectionClient = null;
        }
        if (iceConnected && !isError) {
            //setResult("OK");
        } else {
            //setResult(RESULT_CANCELED);
        }
        //finish();
    }

	
	private void replaceViewWithCamera(Canvas canvas, SurfaceViewRenderer video) {
		
		video.setLayoutParams(canvas.getView().getLayoutParams());

		this.parent = (ViewGroup)canvas.getView().getParent();
		
		if(parent != null) {
			
			this.canvas = canvas;
			
			parent.removeView(video);
			parent.addView(video);
			
			parent.removeView(canvas.getView());
		}
	}
	
	private String getDeviceID() {
		
		SharedPreferences prefs = this.container.$context().getSharedPreferences("haus.orange.streamlink", Context.MODE_PRIVATE);
		
		String foundID = prefs.getString("deviceID", "none");
		
		if(foundID.equals("none")) {
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
	 * Returns the WebRTC address
	 *
	 * @return the WebRTC address as a string
	 */
	@SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Server Address for StreamLink")
	public String WebRTCAddress() {
		return webRTCAddress;
	}

	/**
	 * Sets the WebRTC address
	 *
	 * @param
	 */
	@DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "https://appr.tc")
	@SimpleProperty(category = PropertyCategory.BEHAVIOR)
	public void WebRTCAddress(String address) {
		webRTCAddress = address;
	}
	
	@SimpleFunction
	public void CreateVideoCall(String roomID, Canvas canvas, boolean sendVideo) {
		initWebRTC(roomID, canvas, sendVideo);
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
	 * @param password password to apply to the link
	 * @param description description for the link
	 */
	@SimpleFunction
	public void CreateLink(String password, String description) {

		socketClient.createLink(this.deviceID, password, description);
		
	}

	/**
	 * Connects the device to a Link
	 * 
	 * @param linkCode link code for the specified Link
	 * @param password password for the specified Link
	 */
	@SimpleFunction
	public void ConnectToLink(String linkCode, String password) {

		socketClient.connectToLink(this.deviceID, linkCode, password);
		
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
	public void onLocalDescription(SessionDescription sdp) {
		
		final SessionDescription sdp1 = sdp;
		final long delta = System.currentTimeMillis() - callStartedTimeMs;
        this.container.$context().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (appRtcClient != null) {
                    logAndToast("Sending " + sdp1.type + ", delay=" + delta + "ms");
                    if (signalingParameters.initiator) {
                        appRtcClient.sendOfferSdp(sdp1);
                    } else {
                        appRtcClient.sendAnswerSdp(sdp1);
                    }
                }
                if (peerConnectionParameters.videoMaxBitrate > 0) {
                    System.out.println("Set video maximum bitrate: " + peerConnectionParameters.videoMaxBitrate);
                    peerConnectionClient.setVideoMaxBitrate(peerConnectionParameters.videoMaxBitrate);
                }
            }
        });
	}


	@Override
	public void onIceCandidate(IceCandidate candidate) {
		final IceCandidate candidate1 = candidate;
		this.container.$context().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (appRtcClient != null) {
                    appRtcClient.sendLocalIceCandidate(candidate1);
                }
            }
        });
	}


	@Override
	public void onIceCandidatesRemoved(IceCandidate[] candidates) {
		final IceCandidate[] candidates1 = candidates;
		this.container.$context().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (appRtcClient != null) {
                    appRtcClient.sendLocalIceCandidateRemovals(candidates1);
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
                iceConnected = true;
                callConnected();
            }
        });
	}


	@Override
	public void onIceDisconnected() {
		this.container.$context().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logAndToast("ICE disconnected");
                iceConnected = false;
                disconnect();
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
	
	private VideoCapturer createVideoCapturer() {
        final VideoCapturer videoCapturer;
        System.out.println("Creating capturer using camera2 API.");
        videoCapturer = createCameraCapturer(new Camera2Enumerator(this.container.$context()));
        if (videoCapturer == null) {
            reportError("Failed to open camera");
            return null;
        }
        return videoCapturer;
    }
	
	private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        System.out.println("Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                System.out.println("Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        System.out.println("Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                System.out.println("Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

	private void onConnectedToRoomInternal(final AppRTCClient.SignalingParameters params) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;

        signalingParameters = params;
        logAndToast("Creating peer connection, delay=" + delta + "ms");
        VideoCapturer videoCapturer = null;
        if (peerConnectionParameters.videoCallEnabled) {
            videoCapturer = createVideoCapturer();
        }
        peerConnectionClient.createPeerConnection(
                localProxyVideoSink, remoteRenderers, videoCapturer, signalingParameters);

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
	public void onConnectedToRoom(SignalingParameters params) {
		final SignalingParameters params1 = params;
		this.container.$context().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onConnectedToRoomInternal(params1);
            }
        });
	}


	@Override
	public void onRemoteDescription(SessionDescription sdp) {
		final SessionDescription sdp1 = sdp;
		final long delta = System.currentTimeMillis() - callStartedTimeMs;
        this.container.$context().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    System.out.println("Received remote SDP for non-initilized peer connection.");
                    return;
                }
                logAndToast("Received remote " + sdp1.type + ", delay=" + delta + "ms");
                peerConnectionClient.setRemoteDescription(sdp1);
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
	public void onRemoteIceCandidate(IceCandidate candidate) {
		final IceCandidate candidate1 = candidate;
		this.container.$context().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    System.out.println("Received ICE candidate for a non-initialized peer connection.");
                    return;
                }
                peerConnectionClient.addRemoteIceCandidate(candidate1);
            }
        });	
	}


	@Override
	public void onRemoteIceCandidatesRemoved(IceCandidate[] candidates) {
		
		final IceCandidate[] candidates1 = candidates;
		
		this.container.$context().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    System.out.println("Received ICE candidate removals for a non-initialized peer connection.");
                    return;
                }
                peerConnectionClient.removeRemoteIceCandidates(candidates1);
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
	public void onChannelError(String description) {
		// DO NOTHING
	}
	
	private static class ProxyRenderer implements VideoRenderer.Callbacks {
        private VideoRenderer.Callbacks target;

        @Override
        synchronized public void renderFrame(VideoRenderer.I420Frame frame) {
            if (target == null) {
                System.out.println("Dropping frame in proxy because target is null.");
                VideoRenderer.renderFrameDone(frame);
                return;
            }

            target.renderFrame(frame);
        }

        synchronized public void setTarget(VideoRenderer.Callbacks target) {
            this.target = target;
        }
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
            new AlertDialog.Builder(this.container.$context())
                    .setTitle("Error")
                    .setMessage(errorMessage)
                    .setCancelable(false)
                    .setNeutralButton("OK",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                    disconnect();
                                }
                            })
                    .create()
                    .show();
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
}
