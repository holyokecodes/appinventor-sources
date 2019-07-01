package haus.orange.StreamLink;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.Queue;

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
import com.google.appinventor.components.runtime.Canvas;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;
import com.google.appinventor.components.runtime.util.MediaUtil;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.Environment;
import android.widget.ImageView;
import android.widget.LinearLayout;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

/*
Jacob Bashista 5/27/19

Link is a component designed to allow
devices to communicate across networks.
*/

@SuppressWarnings("deprecation")
@DesignerComponent(version = 1, description = "Allows Streaming Data Across Networks", category = ComponentCategory.EXTENSION, nonVisible = true, iconName = "https://orange.haus/link/icon.png")
@SimpleObject(external = true)
@UsesLibraries(libraries = "okio.jar, okhttp.jar, engineio.jar, socketio.jar")
@UsesPermissions(permissionNames = "android.permission.WRITE_EXTERNAL_STORAGE, android.permission.READ_EXTERNAL_STORAGE, android.permission.INTERNET, android.permission.ACCESS_NETWORK_STATE")
public class StreamLink extends AndroidNonvisibleComponent implements Component {

	private ComponentContainer container;
	
	public static Socket socket;
	
	public static String serverIP;
	
	public static String deviceID;
	
	public static String linkCode;
	
	private boolean isConnected;
	
	private ImageView imageView;
	
	public Queue<Bitmap> videoBuffer;
	
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

				}).on("newmessage", new Emitter.Listener() {
					
					@Override
					public void call(Object... args) {
						try {
							final JSONObject obj = new JSONObject((String) args[0]);
							if(obj.has("link_code")) {
								if(obj.getString("type").equals("string")) {
									final String name = obj.getString("name");
									final String message = obj.getString("message");
									container.$context().runOnUiThread(new Runnable() {
										public void run() {
											OnTextMessageRecieved(name, message);
										}
									});
								}else if(obj.getString("type").equals("image")) {
									final String name = obj.getString("name");
									final String image = obj.getString("message");
									container.$context().runOnUiThread(new Runnable() {
										public void run() {
											ProcessRecievedImage(name, image);
										}
									});
								}else if(obj.getString("type").equals("math")){
								
									final String name = obj.getString("name");
									final long message = (long)obj.getDouble("message");
									container.$context().runOnUiThread(new Runnable() {
										public void run() {
											OnMathMessageRecieved(name, message);
										}
									});
									
								}else if(obj.getString("type").equals("logic")){
								
									final String name = obj.getString("name");
									final boolean message = obj.getBoolean("message");
									container.$context().runOnUiThread(new Runnable() {
										public void run() {
											OnLogicMessageRecieved(name, message);
										}
									});
									
								}else if(obj.getString("type").equals("video")) {
									
									final String image = obj.getString("message");
									container.$context().runOnUiThread(new Runnable() {
										public void run() {
											ProcessVideoFrame(image);
										}
									});
								}
							}
						} catch(JSONException e1) {
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
		        "/Pictures/app_inventor_" + date.getTime()
		        + ".jpg");
		
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
	
	private void ProcessVideoFrame(String base64Image) {
		
		if(imageView != null) {
		
			byte[] byteImage = Base64.decode(base64Image);
			
			Bitmap bitmap = BitmapFactory.decodeByteArray(byteImage, 0, byteImage.length);
		
			imageView.setImageBitmap(bitmap);
		}
		
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
	
	
	@SimpleFunction
	public void StartVideoStream() {

		Camera camera;
		StreamLinkCamera sLCamera;
		
		camera = Camera.open();
		
		
		camera.setPreviewCallback(new PreviewCallback() {
			
			public void onPreviewFrame(byte[] data, Camera camera) {
				
				
				try {
					
					Camera.Parameters parameters = camera.getParameters();
				    int width = parameters.getPreviewSize().width;
				    int height = parameters.getPreviewSize().height;

				    YuvImage yuv = new YuvImage(data, parameters.getPreviewFormat(), width, height, null);

				    ByteArrayOutputStream out = new ByteArrayOutputStream();
				    yuv.compressToJpeg(new Rect(0, 0, width, height), 50, out);

				    byte[] bytes = out.toByteArray();
				    final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
				    
					Matrix matrix = new Matrix();
					matrix.postRotate(90);
					Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
					bitmap.recycle();
				    
				    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				    
				    rotated.compress(Bitmap.CompressFormat.JPEG, 50, outputStream);
				     
				    byte[] imageBytes = outputStream.toByteArray();
					
					String image64 = Base64.encodeToString(imageBytes, false);
					
					
					JSONObject obj = new JSONObject();
					obj.put("link_code", StreamLink.linkCode);
					// Need to pass through name
					obj.put("name", "video");
					obj.put("type", "video");
					obj.put("message", image64);
					
					socket.emit("message", obj.toString());
					
				}catch(JSONException e) {
					e.printStackTrace();
				}
			}
			
		});
		
		sLCamera = new StreamLinkCamera(this.container.$context(), camera);
		
		this.container.$context().addContentView(sLCamera, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
		
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
	 * Starts playing back the stream
	 * @param canvas canvas to replace with the imageview
	 */
	@SimpleFunction
	public void OpenVideoPlayback(Canvas canvas) {
		
		imageView = new ImageView(canvas.$form());
		
		this.container.$context().addContentView(imageView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT));
	}
	
	/**
	 * Sends a TextMessage to the Link
	 * @param name name of the message to identify it
	 * @param message message to be sent
	 */
	@SimpleFunction
	public void SendTextMessage(String name, String message) {
		if(!isConnected) {
			// Can't Run Yet
		}else {
			try {
				JSONObject obj = new JSONObject();
				obj.put("link_code", linkCode);
				obj.put("name", name);
				obj.put("type", "string");
				obj.put("message", message);
				
				socket.emit("message", obj.toString());
			} catch(JSONException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Sends a MathMessage to the Link
	 * @param name name of the message to identify it
	 * @param message message to be sent
	 */
	@SimpleFunction
	public void SendMathMessage(String name, long message) {
		if(!isConnected) {
			// Can't Run Yet
		}else {
			try {
				JSONObject obj = new JSONObject();
				obj.put("link_code", linkCode);
				obj.put("name", name);
				obj.put("type", "math");
				obj.put("message", message);
				
				socket.emit("message", obj.toString());
			} catch(JSONException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Sends a LogicMessage to the Link
	 * @param name name of the message to identify it
	 * @param message message to be sent
	 */
	@SimpleFunction
	public void SendLogicMessage(String name, boolean message) {
		if(!isConnected) {
			// Can't Run Yet
		}else {
			try {
				JSONObject obj = new JSONObject();
				obj.put("link_code", linkCode);
				obj.put("name", name);
				obj.put("type", "logic");
				obj.put("message", message);
				
				socket.emit("message", obj.toString());
			} catch(JSONException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Sends a Image to the Link
	 * @param name name of the image to identify it
	 * @param image path to image file
	 */
	@SimpleFunction
	public void SendImage(String name, String image) {
		if(!isConnected) {
			// Can't Run Yet
		}else {
			
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
				
			} catch(JSONException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
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
	 * Runs when a text based message is received
	 * @param name identifier for message
	 * @param message message that was sent
	 */
	@SimpleEvent
	public void OnTextMessageRecieved(String name, String message) {
		EventDispatcher.dispatchEvent(this, "OnTextMessageRecieved", name, message);
	}
	
	/**
	 * Runs when a math based message is received
	 * @param name identifier for message
	 * @param message message that was sent
	 */
	@SimpleEvent
	public void OnMathMessageRecieved(String name, long message) {
		EventDispatcher.dispatchEvent(this, "OnMathMessageRecieved", name, message);
	}
	
	/**
	 * Runs when a logic based message is received
	 * @param name identifier for message
	 * @param message message that was sent
	 */
	@SimpleEvent
	public void OnLogicMessageRecieved(String name, boolean message) {
		EventDispatcher.dispatchEvent(this, "OnLogicMessageRecieved", name, message);
	}
	
	/**
	 * Runs when a image is received
	 * @param name identifier for image
	 * @param image path to the image file
	 */
	@SimpleEvent
	public void OnImageRecieved(String name, String image) {
		EventDispatcher.dispatchEvent(this, "OnImageRecieved", name, image);
	}
}
