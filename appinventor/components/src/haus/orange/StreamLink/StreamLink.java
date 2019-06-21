package haus.orange.StreamLink;

import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.json.JSONException;
import org.json.JSONObject;

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

import android.app.Activity;
import android.telephony.TelephonyManager;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

/*
Jacob Bashista 5/27/19

Link is a component designed to allow
devices to communicate across networks.
*/

@DesignerComponent(version = 1, description = "Allows Streaming Data Across Networks", category = ComponentCategory.EXTENSION, nonVisible = true, iconName = "https://orange.haus/link/icon.png")
@SimpleObject(external = true)
@UsesLibraries(libraries = "okio.jar, okhttp.jar, engineio.jar, socketio.jar")
@UsesPermissions(permissionNames = "android.permission.INTERNET, android.permission.ACCESS_NETWORK_STATE")
public class StreamLink extends AndroidNonvisibleComponent implements Component {

	private ComponentContainer container;
	
	private Socket socket;
	
	private String serverIP;
	
	private String deviceID;
	
	private String linkCode;
	
	private boolean isConnected;
	
	public StreamLink(ComponentContainer container) {
		super(container.$form());

		this.container = container;
		
		serverIP = "http://192.168.86.68:3000";
		deviceID = "0000";
		linkCode = "0000";
		isConnected = false;
		
	}
	
	
	private void InitSocketIO() {
		try {
			socket = IO.socket(serverIP);
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {

			  @Override
			  public void call(Object... args) {
				  // Connected
				  isConnected = true;
			  }

			}).on("linkcreated", new Emitter.Listener() {

			  @Override
			  public void call(Object... args) {
				try {
					final JSONObject obj = new JSONObject((String) args[0]);
					if(obj.has("link_code")) {
						linkCode = obj.getString("link_code");
						container.$context().runOnUiThread(new Runnable() {
							public void run() {
								try {
									OnLinkCreated(obj.getString("link_code"));
								} catch (JSONException e) {
									e.printStackTrace();
								}
							}
						});
					  }
				} catch (JSONException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			  }

			}).on("linkjoined", new Emitter.Listener() {

				  @Override
				  public void call(Object... args) {
					try {
						final JSONObject obj = new JSONObject((String) args[0]);
						if(obj.has("success")) {
							if(obj.getBoolean("success")) {
								linkCode = obj.getString("link_code");
								container.$context().runOnUiThread(new Runnable() {
									public void run() {
										OnLinkConnected();
									}
								});
							}
						  }
					} catch (JSONException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				  }

				}).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {

			  @Override
			  public void call(Object... args) {
				  isConnected = false;
			  }

			});
			socket.connect();
	}
	
	/**
	 * Returns the Server IP as a string.
	 *
	 * @return serverip as string.
	 */
	@SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Server IP for StreamLink")
	public String ServerIP() {
		return serverIP;
	}

	/**
	 * Specifies the Server IP.
	 *
	 * @param ip server ip of the StreamLink
	 */
	@DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "http://192.168.86.68:3000")
	@SimpleProperty(category = PropertyCategory.BEHAVIOR)
	public void ServerIP(String ip) {
		serverIP = ip;
	}
	
	/**
	 * Returns the Device ID as a string.
	 *
	 * @return deviceID as string.
	 */
	@SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Server IP for StreamLink")
	public String DeviceID() {
		return deviceID;
	}

	/**
	 * Specifies the Device ID.
	 *
	 * @param id device id for StreamLink
	 */
	@DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "0000")
	@SimpleProperty(category = PropertyCategory.BEHAVIOR)
	public void DeviceID(String id) {
		deviceID = id;
	}
	
	/**
	 * Creates a link with the password specified
	 * @param password password to apply to the link
	 */
	@SimpleFunction
	public void CreateLink(String password) {
		
		if(!isConnected) {
			InitSocketIO();
		}
		
		try {
			JSONObject obj = new JSONObject();
			obj.put("device_id", DeviceID());
			obj.put("link_password", password);
			socket.emit("createlink", obj.toString());
		} catch(JSONException e) {
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Connects the device to a Link
	 * @param linkCode link code for the specified Link
	 * @param password password for the specified Link
	 */
	@SimpleFunction
	public void ConnectToLink(String linkCode, String password) {
		
		if(!isConnected) {
			InitSocketIO();
		}
		
		try {
			JSONObject obj = new JSONObject();
			obj.put("device_id", DeviceID());
			obj.put("link_code", linkCode);
			obj.put("link_password", password);
			
			socket.emit("joinlink", obj.toString());
			
		} catch(JSONException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Checks if the device is connected to the server
	 * @return connection status
	 */
	@SimpleFunction
	public boolean IsConnected() {
		return isConnected;
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
