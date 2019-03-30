package haus.orange.networking;

import com.google.appinventor.components.runtime.*;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import edu.wpi.first.wpilibj.networktables.NetworkTable;
import edu.wpi.first.wpilibj.tables.IRemote;
import edu.wpi.first.wpilibj.tables.IRemoteConnectionListener;
import edu.wpi.first.wpilibj.tables.ITable;
import edu.wpi.first.wpilibj.tables.ITableListener;

import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.annotations.UsesLibraries;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleEvent;

/*
Jacob Bashista 1/7/19

NetworkDatabase is a component designed to wrap
the functions of the WPI NetworkTables Library
as well as provide additional helper functions.
*/

@DesignerComponent(version = 1, description = "A Local Network Database Extension", category = ComponentCategory.EXTENSION, nonVisible = true, iconName = "https://orange.haus/networktables/icon.png")
@SimpleObject(external = true)
@UsesLibraries(libraries = "NetworkTables.jar")
@UsesPermissions(permissionNames = "android.permission.INTERNET, android.permission.ACCESS_NETWORK_STATE")
public class NetworkDatabase extends AndroidNonvisibleComponent
		implements Component, ITableListener, IRemoteConnectionListener {

	private String tableName;
	private String connectionIP;

	private NetworkTable networkTable;

	private ComponentContainer container;

	public NetworkDatabase(ComponentContainer container) {
		super(container.$form());

		this.container = container;
	}

	@Override
	public void valueChanged(ITable itable, String string, Object o, boolean bln) {
		TableUpdated();
	}

	@Override
	public void connected(IRemote arg0) {
		Connected();
	}

	@Override
	public void disconnected(IRemote arg0) {
		Disconnected();
	}

	/**
	 * Returns the tables name as a string.
	 *
	 * @return table name as string.
	 */
	@SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Table name for Network")
	public String TableName() {
		return tableName;
	}

	/**
	 * Specifies the tables name.
	 *
	 * @param name name of the table
	 */
	@DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "default")
	@SimpleProperty(category = PropertyCategory.BEHAVIOR)
	public void TableName(String name) {
		tableName = name;
	}

	/**
	 * Returns the IP to connect to (only for client).
	 *
	 * @return IP Address of server for client
	 */
	@SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Server IP Client should connect to (only client)")
	public String ConnectionIP() {
		return connectionIP;
	}

	/**
	 * Sets the IP address of the server for the client to connect to
	 *
	 * @param setIP IP address of the server
	 */
	@DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING, defaultValue = "0.0.0.0")
	@SimpleProperty(category = PropertyCategory.BEHAVIOR)
	public void ConnectionIP(String setIP) {
		connectionIP = setIP;
	}

	/**
	 * Gets the device IP Address (For Server)
	 * 
	 * @return IP address as string
	 */
	@SimpleFunction
	@SuppressWarnings("deprecation")
	public String GetDeviceIPAddress() {

		WifiManager wifiManager = (WifiManager) container.$context().getSystemService(Context.WIFI_SERVICE);
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
		networkTable.addTableListener(this);
		networkTable.addConnectionListener(this, false);
	}

	/**
	 * Configures device to act as a server
	 */
	@SimpleFunction
	public void ConfigureServer() {
		NetworkTable.setServerMode();
		networkTable = NetworkTable.getTable(tableName);
		networkTable.addTableListener(this);
		networkTable.addConnectionListener(this, false);
	}

	/**
	 * Sends a text object to the table
	 * 
	 * @param key     table key to insert text
	 * @param message text to insert
	 */
	@SimpleFunction
	public void PutText(String key, String message) {
		networkTable.putString(key, message);
	}

	/**
	 * Gets a text object from the table
	 * 
	 * @param key table key to get text
	 * @return text at key location
	 */
	@SimpleFunction
	public String GetText(String key) {
		String message = networkTable.getString(key, "null");
		return message;
	}

	/**
	 * Puts a boolean object in the table at the key
	 * 
	 * @param key   key to place boolean at
	 * @param value boolean to insert
	 */
	@SimpleFunction
	public void PutBoolean(String key, boolean value) {
		networkTable.putBoolean(key, value);
	}

	/**
	 * Gets a boolean object from the table
	 * 
	 * @param key key to get boolean from
	 * @return boolean at position
	 */
	@SimpleFunction
	public boolean GetBoolean(String key) {
		boolean value = networkTable.getBoolean(key, false);
		return value;
	}

	/**
	 * Puts a number object in the table at the key
	 * 
	 * @param key   key to insert number at
	 * @param value number to insert
	 */
	@SimpleFunction
	public void PutNumber(String key, double value) {
		networkTable.putNumber(key, value);
	}

	/**
	 * Gets a number value from the table
	 * 
	 * @param key key to get number from
	 * @return number at key
	 */
	@SimpleFunction
	public double GetNumber(String key) {
		double value = networkTable.getNumber(key, 0.0);
		return value;
	}

	/**
	 * Returns true if key is in table
	 * 
	 * @param key key to search for
	 * @return is key in table
	 */
	@SimpleFunction
	public boolean ContainsKey(String key) {
		return networkTable.containsKey(key);
	}

	/**
	 * Returns true if configured as a server
	 * 
	 * @return is device configured as server
	 */
	@SimpleFunction
	public boolean IsServer() {
		return networkTable.isServer();
	}

	/**
	 * Returns true if the client is connected to server
	 * 
	 * @return is device connected to server
	 */
	@SimpleFunction
	public boolean IsConnected() {
		return networkTable.isConnected();
	}

	/**
	 * Triggers when a key in the table was updated
	 */
	@SimpleEvent
	public void TableUpdated() {
		EventDispatcher.dispatchEvent(this, "TableUpdated");
	}

	/**
	 * Triggers when a connection was successful
	 */
	@SimpleEvent
	public void Connected() {
		EventDispatcher.dispatchEvent(this, "Connected");
	}

	/**
	 * Triggers when a connection was removed
	 */
	@SimpleEvent
	public void Disconnected() {
		EventDispatcher.dispatchEvent(this, "Disconnected");
	}
}
