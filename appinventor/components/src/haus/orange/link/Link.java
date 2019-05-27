package haus.orange.link;

import java.net.URISyntaxException;

import org.json.JSONException;
import org.json.JSONObject;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.UsesLibraries;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.common.ComponentCategory;
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

@DesignerComponent(version = 1, description = "Linking Devices Across Networks", category = ComponentCategory.EXTENSION, nonVisible = true, iconName = "https://orange.haus/link/clienticon.png")
@SimpleObject(external = true)
@UsesLibraries(libraries = "engineio-client.jar," + "socketio-client.jar")
@UsesPermissions(permissionNames = "android.permission.INTERNET, android.permission.ACCESS_NETWORK_STATE")
public class Link extends AndroidNonvisibleComponent implements Component {

	private ComponentContainer container;
	
	Socket socket;
	
	public Link(ComponentContainer container) {
		super(container.$form());

		this.container = container;
	}
	
	public void InitLink() {
		try {
			socket = IO.socket("http://localhost");
			socket.connect();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public String GetLinkCode() {
		return "";
	}
	
	public void MakeLink(String linkCode) {
		
	}
	
	public void SendText(String text) {
		try {
			JSONObject obj = new JSONObject();
			obj.put("test", text);
			if(socket.connected()) {
				socket.emit("send", obj);
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
}
