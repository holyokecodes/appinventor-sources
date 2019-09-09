package haus.orange.StreamLink;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

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
import com.google.appinventor.components.runtime.PermissionResultHandler;
import com.google.appinventor.components.runtime.util.MediaUtil;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Environment;
import android.util.Base64;
import haus.orange.StreamLink.webrtc.AppRTCClient;
import haus.orange.StreamLink.webrtc.AppRTCClient.SignalingParameters;
import haus.orange.StreamLink.webrtc.WebSocketRTCClient;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

/*
Jacob Bashista 5/27/19

Link is a component designed to allow
devices to communicate across networks.
*/

@DesignerComponent(version = 6, description = "Allows Streaming Data Across Networks", category = ComponentCategory.EXTENSION, nonVisible = true, iconName = "https://orange.haus/link/icon5.png")
@SimpleObject(external = true)
@UsesLibraries(libraries = "okio.jar, okhttp.jar, engineio.jar, socketio.jar, autobahn.jar")
@UsesPermissions(permissionNames = "android.permission.RECORD_AUDIO, android.permission.INTERNET, android.permission.WRITE_EXTERNAL_STORAGE, android.permission.CAMERA")
public class StreamLink extends AndroidNonvisibleComponent implements Component, AppRTCClient.SignalingEvents {

	public static Socket socket;
	public static String socketServerAddress;
	public static String webRTCAddress;
	private static String deviceID;
	public static String linkCode;
	
	
	private AppRTCClient appRtcClient;
	private ComponentContainer container;
	private boolean isConnected;
	private boolean havePermission;

	public StreamLink(ComponentContainer container) {
		super(container.$form());

		this.container = container;

		socketServerAddress = "https://stream-link.herokuapp.com/";
		deviceID = getDeviceID();
		linkCode = "0000";
		isConnected = false;
		havePermission = false;
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

	private void InitSocketIO() {
		try {
			socket = IO.socket(socketServerAddress);
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
					if (obj.has("link_code")) {
						linkCode = obj.getString("link_code");
						container.$context().runOnUiThread(new Runnable() {
							public void run() {
								try {
									OnSocketLinkConnected(obj.getString("link_code"), obj.getString("link_description"));
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
					if (obj.has("success")) {
						if (obj.getBoolean("success")) {
							linkCode = obj.getString("link_code");
							container.$context().runOnUiThread(new Runnable() {
								public void run() {
									try {
										OnSocketLinkConnected(obj.getString("link_code"), obj.getString("link_description"));
									} catch(JSONException e) {
										e.printStackTrace();
									}
								}
							});
						}
					}
				} catch (JSONException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}

		}).on("newmessage", new Emitter.Listener() {

			@Override
			public void call(Object... args) {
				try {
					final JSONObject obj = new JSONObject((String) args[0]);
					if (obj.has("link_code")) {
						if (obj.getString("type").equals("string")) {
							final String name = obj.getString("name");
							final String message = obj.getString("message");
							container.$context().runOnUiThread(new Runnable() {
								public void run() {
									OnSocketMessageReceived(name, message);
								}
							});
						} else if (obj.getString("type").equals("image")) {
							final String name = obj.getString("name");
							final String image = obj.getString("message");
							container.$context().runOnUiThread(new Runnable() {
								public void run() {
									ProcessReceivedImage(name, image);
								}
							});
						}
					}
				} catch (JSONException e1) {
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

	private void ProcessReceivedImage(String name, String base64Image) {
		Date date = new Date();

		byte[] byteImage = Base64.decode(base64Image, Base64.DEFAULT);

		Bitmap bitmap = BitmapFactory.decodeByteArray(byteImage, 0, byteImage.length);

		OutputStream fOut = null;

		File file = new File(Environment.getExternalStorageDirectory(),
				"/Pictures/app_inventor_" + date.getTime() + ".jpg");

		try {
			fOut = new FileOutputStream(file);

			bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fOut);
			fOut.flush();
			fOut.close();

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		OnSocketImageReceived(name, file.getPath());

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

	/**
	 * Creates a link with the password specified
	 * 
	 * @param password password to apply to the link
	 * @param description description for the link
	 */
	@SimpleFunction
	public void CreateSocketLink(String password, String description) {

		if (!isConnected) {
			InitSocketIO();
		}

		try {
			JSONObject obj = new JSONObject();
			obj.put("device_id", deviceID);
			obj.put("link_password", password);
			obj.put("link_description", description);
			
			socket.emit("createlink", obj.toString());
		} catch (JSONException e) {
			e.printStackTrace();
		}

	}
	
	public void CreateWebRTCLink(String linkCode) {
		
		Uri roomUri = Uri.parse(webRTCAddress);
		
		appRtcClient = new WebSocketRTCClient(this);
		
		
	}

	/**
	 * Connects the device to a Link
	 * 
	 * @param linkCode link code for the specified Link
	 * @param password password for the specified Link
	 */
	@SimpleFunction
	public void ConnectToSocketLink(String linkCode, String password) {

		if (!isConnected) {
			InitSocketIO();
		}

		try {
			JSONObject obj = new JSONObject();
			obj.put("device_id", deviceID);
			obj.put("link_code", linkCode);
			obj.put("link_password", password);

			socket.emit("joinlink", obj.toString());

		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Checks if the device is connected to the server
	 * 
	 * @return connection status
	 */
	@SimpleFunction
	public boolean IsSocketConnected() {
		return isConnected;
	}

	/**
	 * Sends a Message to the Link
	 * 
	 * @param name    name of the message to identify it
	 * @param message message to be sent
	 */
	@SimpleFunction
	public void SendSocketMessage(String name, String message) {
		if (!isConnected) {
			// Can't Run Yet
		} else {
			try {
				JSONObject obj = new JSONObject();
				obj.put("link_code", linkCode);
				obj.put("name", name);
				obj.put("type", "string");
				obj.put("message", message);

				socket.emit("message", obj.toString());
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Sends a Image to the Link
	 * 
	 * @param name  name of the image to identify it
	 * @param image path to image file
	 */
	@SimpleFunction
	public void SendSocketImage(String name, String image) {

		// Only for rerunning function on permission granted
		final String fName = name;
		final String fImage = image;

		if (!havePermission) {
			form.runOnUiThread(new Runnable() {
				@Override
				public void run() {

					form.askPermission(Manifest.permission.CAMERA, new PermissionResultHandler() {
						@Override
						public void HandlePermissionResponse(String permission, boolean granted) {
							if (granted) {
								havePermission = true;
								SendSocketImage(fName, fImage);
							} else {
								form.dispatchPermissionDeniedEvent(form, "TakePicture", Manifest.permission.CAMERA);
							}
						}
					});
				}
			});
			return;
		}

		if (!isConnected) {
			// Can't Run Yet
		} else {

			try {

				BitmapDrawable bitmapDraw = MediaUtil.getBitmapDrawable(container.$form(), image);

				Bitmap bitmap = bitmapDraw.getBitmap();

				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

				bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream);

				byte[] imageBytes = outputStream.toByteArray();

				String image64 = Base64.encodeToString(imageBytes, Base64.DEFAULT);

				JSONObject obj = new JSONObject();
				obj.put("link_code", linkCode);
				obj.put("name", name);
				obj.put("type", "image");
				obj.put("message", image64);

				socket.emit("message", obj.toString());

			} catch (JSONException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Runs after ConnectToLink is successful
	 */
	@SimpleEvent
	public void OnSocketLinkConnected(String linkCode, String description) {
		EventDispatcher.dispatchEvent(this, "OnSocketLinkConnected", linkCode, description);
	}

	/**
	 * Runs when a message is received
	 * 
	 * @param name    identifier for message
	 * @param message message that was sent
	 */
	@SimpleEvent
	public void OnSocketMessageReceived(String name, String message) {
		EventDispatcher.dispatchEvent(this, "OnSocketMessageReceived", name, message);
	}

	/**
	 * Runs when a image is received
	 * 
	 * @param name  identifier for image
	 * @param image path to the image file
	 */
	@SimpleEvent
	public void OnSocketImageReceived(String name, String image) {
		EventDispatcher.dispatchEvent(this, "OnSocketImageReceived", name, image);
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
