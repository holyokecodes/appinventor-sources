package haus.orange.StreamLink;

import java.net.URISyntaxException;

import org.json.JSONException;
import org.json.JSONObject;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.UsesLibraries;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;

import io.socket.client.IO;
import io.socket.client.Socket;

/*
Jacob Bashista 5/27/19

Link is a component designed to allow
devices to communicate across networks.
*/

@DesignerComponent(version = 1, description = "Allows Streaming Data Across Networks", category = ComponentCategory.EXTENSION, nonVisible = true, iconName = "https://orange.haus/link/icon.png")
@SimpleObject(external = true)
@UsesLibraries(libraries = "engineio-client.jar," + "socketio-client.jar")
@UsesPermissions(permissionNames = "android.permission.INTERNET, android.permission.ACCESS_NETWORK_STATE")
public class StreamLink extends AndroidNonvisibleComponent implements Component {

	private ComponentContainer container;
	
	private Socket socket;
	
	private String serverIP = "orange.haus";
	
	public StreamLink(ComponentContainer container) {
		super(container.$form());

		this.container = container;
	}
	
	/**
	 * Creates a link with the password specified
	 * @param password password to apply to the link
	 */
	@SimpleFunction
	public void CreateLink(String password) {
		
	}
	
	/**
	 * Connects the device to a Link
	 * @param linkCode link code for the specified Link
	 * @param password password for the specified Link
	 */
	@SimpleFunction
	public void ConnectToLink(String linkCode, String password) {
		
	}
	
	/**
	 * Checks if the device is connected to a link
	 * @return connection status
	 */
	@SimpleFunction
	public boolean IsLinked() {
		return false;
	}
	
	/**
	 * Starts video streaming from the current device to the Link
	 * @param name of video stream
	 */
	@SimpleFunction
	public void StartVideoStream(String name) {
		
	}
	
	/**
	 * Gets the Video Stream for a given name
	 * @param name name of the video stream
	 */
	@SimpleFunction
	public void GetRemoteVideoStream(String name) {
		
	}
	
	/**
	 * Sends a TextMessage to the Link
	 * @param name name of the message to identify it
	 * @param message message to be sent
	 */
	@SimpleFunction
	public void SendTextMessage(String name, String message) {
		
	}
	
	/**
	 * Runs after CreateLink is successful
	 * @param linkCode
	 */
	@SimpleEvent
	public void OnLinkCreated(String linkCode) {
		EventDispatcher.dispatchEvent(this, "OnLinkCreated", linkCode);
	}
	
	/**
	 * Runs after ConnectToLink is successful
	 */
	@SimpleEvent
	public void OnLinkConnected() {
		EventDispatcher.dispatchEvent(this, "OnLinkConnected");
	}
	
	/**
	 * Runs after StartVideoStream is successful
	 * @param videoStream location of video file
	 */
	@SimpleEvent
	public void OnVideoStreamStarted(String name, String videoStream) {
		EventDispatcher.dispatchEvent(this, "OnVideoStreamStarted", name, videoStream);
	}
	
	/**
	 * Runs after GetRemoteVideoStream is successful
	 * @param videoStream location of video file
	 */
	@SimpleEvent
	public void OnRemoteVideoStreamStarted(String name, String videoStream) {
		EventDispatcher.dispatchEvent(this, "OnRemoteVideoStream", name, videoStream);
	}
	
	/**
	 * Runs when a text based message is received
	 * @param message message that was sent
	 */
	@SimpleEvent
	public void OnTextMessageRecieved(String name, String message) {
		EventDispatcher.dispatchEvent(this, "OnTextMessageRecieved", name, message);
	}
}
