package haus.orange.link;

import java.net.URISyntaxException;

import org.json.JSONException;
import org.json.JSONObject;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.PropertyCategory;
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

import io.socket.client.IO;
import io.socket.client.Socket;

/*
Jacob Bashista 5/27/19

Link is a component designed to allow
devices to communicate across networks.
*/

@DesignerComponent(version = 1, description = "Linking Devices Across Networks", category = ComponentCategory.EXTENSION, nonVisible = true, iconName = "https://orange.haus/link/icon.png")
@SimpleObject(external = true)
@UsesLibraries(libraries = "engineio-client.jar," + "socketio-client.jar")
@UsesPermissions(permissionNames = "android.permission.INTERNET, android.permission.ACCESS_NETWORK_STATE")
public class Link extends AndroidNonvisibleComponent implements Component {

	private ComponentContainer container;
	
	private Socket socket;
	
	private String serverIP = "orange.haus";
	
	public Link(ComponentContainer container) {
		super(container.$form());

		this.container = container;
	}
	
	/**
	 * Specifies the server address.
	 *
	 * @param name name of the table
	 */
	@DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "orange.haus")
	@SimpleProperty(category = PropertyCategory.BEHAVIOR)
	public void ServerAddress(String ip) {
		this.serverIP = ip;
	}
	
	/**
	 * Initializes the Socket IO Instance
	 */
	@SimpleFunction
	public void InitLink() {
		try {
			socket = IO.socket(serverIP);
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Gets a Link code from the server
	 * 
	 * @return Link code as a string
	 */
	@SimpleFunction
	public String GetLinkCode() {
		return "";
	}
	
	/**
	 * Creates a Link
	 * 
	 * @param linkCode link code to connect to
	 */
	@SimpleFunction
	public void MakeLink(String linkCode) {
		
	}
	
	/**
	 * Sends a text object to Link
	 * 
	 * @param text text to send to Link
	 */
	@SimpleFunction
	public void SendText(String text) {
		try {
			JSONObject obj = new JSONObject();
			obj.put("msg", text);
			socket.emit("sendtext", obj);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
}
