package com.google.appinventor.components.runtime;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;

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
	private boolean isServer;
	private String connectionIP;
	
	private Context context;

    public NetworkDatabase(ComponentContainer container){
        super(container.$form());
        
        context = container.$context();
        
        tableName = "default";
        isServer = false;
        connectionIP = "0.0.0.0"; 
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
     * Returns whether this app instance is acting as a server.
     *
     * @return  is server as boolean.
     */
    @SimpleProperty(
        category = PropertyCategory.BEHAVIOR,
        description = "Is this the server?")
    public boolean IsServer() {
      return isServer;
    }

    /**
     * Specifies the type of app.
     *
     * @param appType  boolean value indicating app type (client or server)
     */
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
        defaultValue = "false")
    @SimpleProperty(
        category = PropertyCategory.BEHAVIOR)
    public void IsServer(boolean appType) {
      isServer = appType;
    }
    
    
    /**
     * Returns the ip to connect to (only for client).
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
     * Sets the ip address of the server for the client to connect to
     *
     * @param setIP  ip address of the server
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
}
