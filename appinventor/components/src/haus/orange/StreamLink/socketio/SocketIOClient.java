package haus.orange.StreamLink.socketio;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.Date;

import org.json.JSONException;
import org.json.JSONObject;

import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.PermissionResultHandler;
import com.google.appinventor.components.runtime.util.MediaUtil;
import com.google.appinventor.components.runtime.Form;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Environment;
import android.util.Base64;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class SocketIOClient {
	
	private Socket socket;
	private boolean isConnected;
	private String linkCode;
	private final SocketIOEvents events;
	private boolean havePermission;
	private final ComponentContainer container;
	
	public SocketIOClient(ComponentContainer container, SocketIOEvents events, String socketAddress) {
		
		this.events = events;
		this.container = container;
		
		final ComponentContainer fContainer = container;
		final SocketIOEvents fEvents = events;
		
		try {
			socket = IO.socket(socketAddress);
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
						fContainer.$context().runOnUiThread(new Runnable() {
							public void run() {
								try {
									fEvents.LinkConnected(obj.getString("link_code"), obj.getString("link_description"));
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
							fContainer.$context().runOnUiThread(new Runnable() {
								public void run() {
									try {
										fEvents.LinkConnected(obj.getString("link_code"), obj.getString("link_description"));
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
							fContainer.$context().runOnUiThread(new Runnable() {
								public void run() {
									fEvents.MessageReceived(name, message);
								}
							});
						} else if (obj.getString("type").equals("image")) {
							final String name = obj.getString("name");
							final String image = obj.getString("message");
							fContainer.$context().runOnUiThread(new Runnable() {
								public void run() {
									processReceivedImage(name, image);
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
	
	private void processReceivedImage(String name, String base64Image) {
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

		events.ImageReceived(name, file.getPath());

	}
	
	public void createLink(String deviceID, String password, String description) {
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
	
	public void connectToLink(String deviceID, String linkCode, String password) {
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
	
	public boolean isConnected() {
		return isConnected;
	}
	
	
	public void sendMessage(String name, String message) {
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
	
	public void sendImage(String name, String image) {
		// Only for rerunning function on permission granted
		final String fName = name;
		final String fImage = image;
		
		final Form form = container.$form();

		if (!havePermission) {
			form.runOnUiThread(new Runnable() {
				@Override
				public void run() {

					form.askPermission(Manifest.permission.CAMERA, new PermissionResultHandler() {
						@Override
						public void HandlePermissionResponse(String permission, boolean granted) {
							if (granted) {
								havePermission = true;
								events.ImageReceived(fName, fImage);
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

				BitmapDrawable bitmapDraw = MediaUtil.getBitmapDrawable(form, image);

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
}
