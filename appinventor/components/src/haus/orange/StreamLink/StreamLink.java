package haus.orange.StreamLink;


import java.util.UUID;

import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;

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
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
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
	
	private PeerConnectionClient peerConnectionClient = null;
    private AppRTCClient appRtcClient;
	private AppRTCClient.SignalingParameters signalingParameters;
	private boolean iceConnected;
	private AppRTCClient.RoomConnectionParameters roomConnectionParameters;

	public StreamLink(ComponentContainer container) {
		super(container.$form());

		this.container = container;

		socketServerAddress = "https://stream-link.herokuapp.com/";
		deviceID = getDeviceID();
		linkCode = "0000";
		
		socketClient = new SocketIOClient(container, this, socketServerAddress);
		
		
		iceConnected = false;
        signalingParameters = null;
		
		
        peerConnectionClient = new PeerConnectionClient();
	}
	
	
	public void connectVideoCall(String roomID) {
		Uri roomUri = Uri.parse("https://appr.tc");
		
		appRtcClient = new WebSocketRTCClient(this);
		
		roomConnectionParameters =
                new AppRTCClient.RoomConnectionParameters(
                        roomUri.toString(),
                        roomID,
                        false,
                        null);
		
		appRtcClient.connectToRoom(roomConnectionParameters);
		
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
	public void CreateVideoCall(String roomID) {
		connectVideoCall("657619640");
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
		// TODO Auto-generated method stub
		
	}


	@Override
	public void onIceCandidate(IceCandidate candidate) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void onIceCandidatesRemoved(IceCandidate[] candidates) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void onIceConnected() {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void onIceDisconnected() {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void onPeerConnectionClosed() {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void onPeerConnectionStatsReady(StatsReport[] reports) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void onPeerConnectionError(String description) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void onConnectedToRoom(SignalingParameters params) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void onRemoteDescription(SessionDescription sdp) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void onRemoteIceCandidate(IceCandidate candidate) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void onRemoteIceCandidatesRemoved(IceCandidate[] candidates) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void onChannelClose() {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void onChannelError(String description) {
		// TODO Auto-generated method stub
		
	}
}
