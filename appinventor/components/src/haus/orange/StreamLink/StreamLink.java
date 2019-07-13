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

import com.firebase.tubesock.Base64;
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
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Environment;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewManager;
import android.widget.Button;
import android.widget.LinearLayout;
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
@UsesLibraries(libraries = "okio.jar, okhttp.jar, engineio.jar, socketio.jar, encoder.jar, rtmp.jar, rtplibrary.jar, rtsp.jar")
@UsesPermissions(permissionNames = "android.permission.RECORD_AUDIO, android.permission.INTERNET, android.permission.WRITE_EXTERNAL_STORAGE, android.permission.CAMERA")
public class StreamLink extends AndroidNonvisibleComponent implements Component {

	public static Socket socket;
	public static String customServerIP;
	public static String serverIP;
	private static String deviceID;
	public static String linkCode;
	public static boolean useCustomServer;
	public static String rtmpServerURL;
	public static String streamKey;
	
	
	
	private ComponentContainer container;
	private boolean isConnected;
	private boolean havePermission;

	public StreamLink(ComponentContainer container) {
		super(container.$form());

		this.container = container;

		serverIP = "http://streamlink.orange.haus:3000";
		customServerIP = "";
		rtmpServerURL = "rtmp://a.rtmp.youtube.com/live2";
		deviceID = getDeviceID();
		linkCode = "0000";
		isConnected = false;
		havePermission = false;
		useCustomServer = false;

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
			
			if(!useCustomServer) {
				socket = IO.socket(serverIP);
			}else {
				socket = IO.socket(customServerIP);
			}
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
									OnLinkCreated(obj.getString("link_code"), obj.getString("link_description"));
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
										OnLinkConnected(obj.getString("link_code"), obj.getString("link_description"));
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
									OnTextMessageRecieved(name, message);
								}
							});
						} else if (obj.getString("type").equals("image")) {
							final String name = obj.getString("name");
							final String image = obj.getString("message");
							container.$context().runOnUiThread(new Runnable() {
								public void run() {
									ProcessRecievedImage(name, image);
								}
							});
						} else if (obj.getString("type").equals("math")) {

							final String name = obj.getString("name");
							final long message = (long) obj.getDouble("message");
							container.$context().runOnUiThread(new Runnable() {
								public void run() {
									OnMathMessageRecieved(name, message);
								}
							});

						} else if (obj.getString("type").equals("logic")) {

							final String name = obj.getString("name");
							final boolean message = obj.getBoolean("message");
							container.$context().runOnUiThread(new Runnable() {
								public void run() {
									OnLogicMessageRecieved(name, message);
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

	private void ProcessRecievedImage(String name, String base64Image) {
		Date date = new Date();

		byte[] byteImage = Base64.decode(base64Image);

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

		OnImageRecieved(name, file.getPath());

	}
	
	/**
	 * Should StreamLink use a custom server?
	 * 
	 * @return true if a custom server should be used
	 */
	@SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Use a custom StreamLink Server")
	public boolean UseCustomServer() {
		return useCustomServer;
	}
	
	/**
	 * Should StreamLink use a customer server?
	 * 
	 * @param useCustom set to true if a custom server should be used
	 */
	@DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN, defaultValue = "false")
	@SimpleProperty(category = PropertyCategory.BEHAVIOR)
	public void UseCustomServer(boolean useCustom) {
		useCustomServer = useCustom;
	}
	
	
	/**
	 * Returns the custom servers IP address
	 *
	 * @return the server ip address as a string
	 */
	@SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Server IP for StreamLink")
	public String CustomServerIP() {
		return customServerIP;
	}

	/**
	 * Sets the custom servers IP address
	 *
	 * @param
	 */
	@DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "")
	@SimpleProperty(category = PropertyCategory.BEHAVIOR)
	public void CustomServerIP(String ip) {
		customServerIP = ip;
	}
	
	/**
	 * Returns the RTMP Server IP as a string.
	 *
	 * @return serverip as string.
	 */
	@SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Server IP for RTMP")
	public String RTMPServerURL() {
		return rtmpServerURL;
	}

	/**
	 * Specifies the RTMP Server IP.
	 *
	 * @param
	 */
	@DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "rtmp://a.rtmp.youtube.com/live2")
	@SimpleProperty(category = PropertyCategory.BEHAVIOR)
	public void RTMPServerURL(String url) {
		rtmpServerURL = url;
	}
	
	/**
	 * Returns the RTMP Stream Key as a string.
	 *
	 * @return serverip as string.
	 */
	@SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Server IP for RTMP")
	public String StreamKey() {
		return streamKey;
	}

	/**
	 * Specifies the RTMP Stream Key.
	 *
	 * @param
	 */
	@DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "")
	@SimpleProperty(category = PropertyCategory.BEHAVIOR)
	public void StreamKey(String key) {
		streamKey = key;
	}

	/**
	 * Creates a link with the password specified
	 * 
	 * @param password password to apply to the link
	 * @param description description for the link
	 */
	@SimpleFunction
	public void CreateLink(String password, String description) {

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

	/**
	 * Connects the device to a Link
	 * 
	 * @param linkCode link code for the specified Link
	 * @param password password for the specified Link
	 */
	@SimpleFunction
	public void ConnectToLink(String linkCode, String password) {

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
	public boolean IsConnected() {
		return isConnected;
	}

	/**
	 * Sends a TextMessage to the Link
	 * 
	 * @param name    name of the message to identify it
	 * @param message message to be sent
	 */
	@SimpleFunction
	public void SendTextMessage(String name, String message) {
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
	 * Sends a MathMessage to the Link
	 * 
	 * @param name    name of the message to identify it
	 * @param message message to be sent
	 */
	@SimpleFunction
	public void SendMathMessage(String name, long message) {
		if (!isConnected) {
			// Can't Run Yet
		} else {
			try {
				JSONObject obj = new JSONObject();
				obj.put("link_code", linkCode);
				obj.put("name", name);
				obj.put("type", "math");
				obj.put("message", message);

				socket.emit("message", obj.toString());
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Sends a LogicMessage to the Link
	 * 
	 * @param name    name of the message to identify it
	 * @param message message to be sent
	 */
	@SimpleFunction
	public void SendLogicMessage(String name, boolean message) {
		if (!isConnected) {
			// Can't Run Yet
		} else {
			try {
				JSONObject obj = new JSONObject();
				obj.put("link_code", linkCode);
				obj.put("name", name);
				obj.put("type", "logic");
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
	public void SendImage(String name, String image) {

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
								SendImage(fName, fImage);
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

				String image64 = Base64.encodeToString(imageBytes, false);

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
	 * Opens the custom window to Control RTMP Streaming
	 */
	@SimpleFunction
	public void OpenStreamWindow() {
		
		
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		final LinearLayout layout = new LinearLayout(this.container.$context());
		layout.setBackgroundColor(Color.WHITE);
		layout.setOrientation(LinearLayout.VERTICAL);
		
		
		final StreamLinkCamera camera = new StreamLinkCamera(this.container.$context(), rtmpServerURL, streamKey);
		camera.setZOrderOnTop(false);
		
		final Button startStopStreamButton = new Button(this.container.$context());
		startStopStreamButton.setText("Start Stream");
		startStopStreamButton.setOnClickListener( new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				
				
				if(camera.isStreaming()) {
					startStopStreamButton.setText("Start Stream");
					camera.stopStreaming();
				}else {
					startStopStreamButton.setText("Stop Stream");
					camera.startStreaming();
				}
				
				
				
			}
			
		});
		
		Button closeButton = new Button(this.container.$context());
		closeButton.setText("Close");
		closeButton.setOnClickListener( new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				
				camera.closeCamera();
				((ViewManager)layout.getParent()).removeView(layout);
				
			}
			
		});
		
		layout.addView(closeButton, params);
		layout.addView(startStopStreamButton, params);
		layout.addView(camera, params);
		
		LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
		
		this.container.$context().addContentView(layout, layoutParams);
		
	}

	/**
	 * Runs after CreateLink is successful
	 * 
	 * @param linkCode
	 * @param description
	 */
	@SimpleEvent
	public void OnLinkCreated(String linkCode, String description) {
		EventDispatcher.dispatchEvent(this, "OnLinkCreated", linkCode, description);
	}

	/**
	 * Runs after ConnectToLink is successful
	 */
	@SimpleEvent
	public void OnLinkConnected(String linkCode, String description) {
		EventDispatcher.dispatchEvent(this, "OnLinkConnected", linkCode, description);
	}

	/**
	 * Runs when a text based message is received
	 * 
	 * @param name    identifier for message
	 * @param message message that was sent
	 */
	@SimpleEvent
	public void OnTextMessageRecieved(String name, String message) {
		EventDispatcher.dispatchEvent(this, "OnTextMessageRecieved", name, message);
	}

	/**
	 * Runs when a math based message is received
	 * 
	 * @param name    identifier for message
	 * @param message message that was sent
	 */
	@SimpleEvent
	public void OnMathMessageRecieved(String name, long message) {
		EventDispatcher.dispatchEvent(this, "OnMathMessageRecieved", name, message);
	}

	/**
	 * Runs when a logic based message is received
	 * 
	 * @param name    identifier for message
	 * @param message message that was sent
	 */
	@SimpleEvent
	public void OnLogicMessageRecieved(String name, boolean message) {
		EventDispatcher.dispatchEvent(this, "OnLogicMessageRecieved", name, message);
	}

	/**
	 * Runs when a image is received
	 * 
	 * @param name  identifier for image
	 * @param image path to the image file
	 */
	@SimpleEvent
	public void OnImageRecieved(String name, String image) {
		EventDispatcher.dispatchEvent(this, "OnImageRecieved", name, image);
	}
}
