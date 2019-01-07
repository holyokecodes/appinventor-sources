package com.google.appinventor.components.runtime;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import edu.wpi.first.wpilibj.networktables.NetworkTable;

import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.annotations.UsesLibraries;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.PropertyCategory;

/*
Jacob Bashista 1/7/19

NetworkDatabase is a component designed to wrap
the functions of the WPI NetworkTables Library
as well as provide additional helper functions.
*/


@DesignerComponent(version = 1,
        description = "Allows for local network communication",
        category = ComponentCategory.EXTENSION,
        nonVisible = true,
        iconName = "images/extension.png")
@SimpleObject(external = true)
@UsesLibraries(libraries = "NetworkTables.jar")
@UsesPermissions(permissionNames = "android.permission.INTERNET, android.permission.ACCESS_NETWORK_STATE")
public class NetworkDatabase extends AndroidNonvisibleComponent
        implements Component {
	
	private String tableName;
	private String connectionIP;
	
	private NetworkTable networkTable;
	
	private Context context;

    public NetworkDatabase(ComponentContainer container){
        super(container.$form());
        
        context = container.$context();
    }
    
    /**
     * Returns the tables name as a string.
     *
     * @return  table name as string.
     */
    @SimpleProperty(
        category = PropertyCategory.BEHAVIOR,
        description = "Table name for Network")
    public String TableName() {
      return tableName;
    }

    /**
     * Specifies the tables name.
     *
     * @param name  name of the table
     */
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING,
        defaultValue = "default")
    @SimpleProperty(
        category = PropertyCategory.BEHAVIOR)
    public void TableName(String name) {
      tableName = name;
    }
    
    
    /**
     * Returns the IP to connect to (only for client).
     *
     * @return  IP Address of server for client
     */
    @SimpleProperty(
        category = PropertyCategory.BEHAVIOR,
        description = "Server IP Client should connect to (only client)")
    public String ConnectionIP() {
      return connectionIP;
    }

    /**
     * Sets the IP address of the server for the client to connect to
     *
     * @param setIP  IP address of the server
     */
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING,
        defaultValue = "0.0.0.0")
    @SimpleProperty(
        category = PropertyCategory.BEHAVIOR)
    public void ConnectionIP(String setIP) {
      connectionIP = setIP;
    }
    
    
    /**
     * Gets the device IP Address (For Server)
     * 
     * @return	IP address as string
     */
    @SimpleFunction
    @SuppressWarnings("deprecation")
    public String GetDeviceIPAddress() {
    	
    	WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    	String ipAddress = Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
    	
    	return ipAddress;
    }   
    
    /**
     * Configures device to act as client
     */
    @SimpleFunction
    public void ConfigureClient() {
    	
    	NetworkTable.setClientMode();
    	NetworkTable.setIPAddress(ConnectionIP());
    	
    	networkTable = NetworkTable.getTable(TableName());
    }
    
    /**
     * Configures device to act as a server
     */
    @SimpleFunction
    public void ConfigureServer() {
    	networkTable = NetworkTable.getTable(tableName);
    }
    
    /**
     * Sends a string to the table
     * 
     * 
     * @param key	table key to insert string
     * @param message	string to insert
     */
    @SimpleFunction
    public void SendString(String key, String message) {
    	networkTable.putString(key, message);
    }
    
    /**
     * Gets a string from the table
     * 
     * 
     * @param key	table key to get string
     * @return	string at key location
     */
    @SimpleFunction
    public String RecieveString(String key) {
    	String message = networkTable.getString(key, "null");
    	return message;
    }
    
    
}
