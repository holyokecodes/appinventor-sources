package haus.orange.StreamLink.socketio;

public interface SocketIOEvents {
	
	public void LinkConnected(String linkCode, String description);
	public void MessageReceived(String name, String message);
	public void ImageReceived(String name, String image);

}
