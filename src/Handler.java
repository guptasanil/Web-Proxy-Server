import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.net.URL;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Handler implements Runnable {

	Socket clientSocket; // Implements socket

	BufferedReader readFromClient; // Reads data client sends

	BufferedWriter sendToClient; // Sends data to client

	private Thread httpsRequest; // Thread for https requests

	public Handler(Socket clientSocket) { // Handle object to deal with GET requests
		this.clientSocket = clientSocket;
		try {
			this.clientSocket.setSoTimeout(2000); // read call on InputStream blocks
			readFromClient = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			sendToClient = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void run() {

		String clientRequest;
		try {
			clientRequest = readFromClient.readLine(); // Gets Request from client
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Error");
			return;
		}
		if (clientRequest.contains("push.services.mozilla") || clientRequest.contains("firefox.settings")
				|| clientRequest.contains("detectportal") || clientRequest.contains("favicon")) {
			System.out.println("");
		} else {
			System.out.println("Request Received " + clientRequest);

			String requestType = clientRequest.substring(0, clientRequest.indexOf(' ')); // Gets request type

			String urlString = clientRequest.substring(clientRequest.indexOf(' ') + 1); // Removes request type

			urlString = urlString.substring(0, urlString.indexOf(' ')); // URL

			if (!urlString.substring(0, 4).equals("http")) { // Adds 'http://' if user doesn't
				String temp = "http://";
				urlString = temp + urlString;
			}

			if (Server.isBlocked(urlString)) { // Check if URL is blocked
				System.out.println("Blocked site requested : " + urlString);
				blockedSiteRequested();
				return;
			}

			if (requestType.equals("CONNECT")) { // Checks if HTTPS request
				System.out.println("HTTPS Request for : " + urlString + "\n");
				handleHTTPSRequest(urlString);
			}

			else {
				String body;
				if ((body = Server.getFromCached(urlString)) != null) { // Checks if URL is in cached list
					System.out.println("Cached Copy found for : " + urlString + "\n");
					cachedPage(body);
				} else {
					System.out.println("HTTP GET for : " + urlString + "\n");
					notCachedPage(urlString);
				}
			}
		}
	}

	private void cachedPage(String body) { // Sends cache file to client

		long startTime = System.currentTimeMillis();
		try {
			String response;
			response = "HTTP/1.0 200 OK\n" + "Proxy-agent: ProxyServer/1.0\n" + "\r\n";
			sendToClient.write(response);
			sendToClient.flush();
			sendToClient.write(body); 
			sendToClient.flush();


			if (sendToClient != null) {
				sendToClient.close();
			}

		} catch (IOException e) {
			System.out.println("Error");
			e.printStackTrace();
		}

		long finishTime = System.currentTimeMillis();
		System.out.println("That took: " + (finishTime - startTime) + " ms");
	}

	
	private void notCachedPage(String urlString) { // Sends data to client
		long startTime = System.currentTimeMillis(); // check speed

		if (urlString.contains("favicon"))
			return;
		try {
			boolean caching = true;
			String tempBody = "";

			URL remoteURL = new URL(urlString); // Creates URL
			HttpURLConnection remoteServerConnection = (HttpURLConnection) remoteURL.openConnection(); // Create connection to remote server
			remoteServerConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			remoteServerConnection.setRequestProperty("Content-Language", "en-US");
			remoteServerConnection.setUseCaches(false);
			remoteServerConnection.setDoOutput(true);

			BufferedReader proxyAndRemoteRead = new BufferedReader(
					new InputStreamReader(remoteServerConnection.getInputStream())); // Reader between proxy and remote
																						// server

			String line = "HTTP/1.0 200 OK\n" + "Proxy-agent: ProxyServer/1.0\n" + "\r\n";
			sendToClient.write(line); // Message to be sent to client

			while ((line = proxyAndRemoteRead.readLine()) != null) { // checks messages between proxy and remote
																			// server

				sendToClient.write(line); // Sends message to client

				if (caching) { // Adds to cache
					tempBody += line;
				}
			}
			sendToClient.flush(); // Makes sure data is sent

			if (proxyAndRemoteRead != null) {
				proxyAndRemoteRead.close();
			}

			if (caching) {
				Server.addToCached(urlString, tempBody); // add URL to cached list
			}
			if (sendToClient != null) {
				sendToClient.close();
			}
		}

		catch (Exception e) {
			e.printStackTrace();
		}

		long finishTime = System.currentTimeMillis();
		System.out.println("That took: " + (finishTime - startTime) + " ms"); // Check speed
	}

	private void handleHTTPSRequest(String urlString) {

		String url = urlString.substring(7); // Get URL
		String pieces[] = url.split(":");
		url = pieces[0];
		int port = Integer.valueOf(pieces[1]); // Get port

		try {
			for (int i = 0; i < 5; i++) {
				readFromClient.readLine(); // read rest of data
			}
			InetAddress address = InetAddress.getByName(url); // Gets IP address

			Socket remoteServerSocket = new Socket(address, port); // Opens socket to remote server
			remoteServerSocket.setSoTimeout(5000);

			String line = "HTTP/1.0 200 Connection established\r\n" + "Proxy-Agent: ProxyServer/1.0\r\n" + "\r\n";
			sendToClient.write(line);
			sendToClient.flush();

			
			BufferedWriter proxyAndRemoteWrite = new BufferedWriter(
					new OutputStreamWriter(remoteServerSocket.getOutputStream())); // Create a  writer betwen proxy and remote

			
			BufferedReader proxyAndRemoteRead = new BufferedReader(
					new InputStreamReader(remoteServerSocket.getInputStream())); // Create Reader from proxy and remote

			
			ClientToServerHttpsTransmit clientToServerHttps = new ClientToServerHttpsTransmit(
					clientSocket.getInputStream(), remoteServerSocket.getOutputStream()); 

			httpsRequest = new Thread(clientToServerHttps); // Create thread to listen to client and transmit to server
			httpsRequest.start();

			
			try {
				byte[] buffer = new byte[4096];
				int read;
				do {
					read = remoteServerSocket.getInputStream().read(buffer);
					if (read > 0) {
						clientSocket.getOutputStream().write(buffer, 0, read);
						if (remoteServerSocket.getInputStream().available() < 1) {
							clientSocket.getOutputStream().flush(); // Listen to remote server and pass to client
						}
					}
				} while (read >= 0);
			} catch (SocketTimeoutException e) {

			} catch (IOException e) {
				e.printStackTrace();
			}
			if (remoteServerSocket != null) {
				remoteServerSocket.close();
			}

			if (proxyAndRemoteRead != null) {
				proxyAndRemoteRead.close();
			}

			if (proxyAndRemoteWrite != null) {
				proxyAndRemoteWrite.close();
			}

			if (sendToClient != null) {
				sendToClient.close();
			}

		} catch (SocketTimeoutException e) {
			String line = "HTTP/1.0 504 Timeout Occured after 10s\n" + "User-Agent: ProxyServer/1.0\n" + "\r\n";
			try {
				sendToClient.write(line);
				sendToClient.flush();
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		} catch (Exception e) {
			System.out.println("Error on HTTPS : " + urlString);
			e.printStackTrace();
		}

	}
	class ClientToServerHttpsTransmit implements Runnable {

		InputStream proxyToClientIS;
		OutputStream proxyToServerOS;

		public ClientToServerHttpsTransmit(InputStream proxyToClientIS, OutputStream proxyToServerOS) {
			this.proxyToClientIS = proxyToClientIS;
			this.proxyToServerOS = proxyToServerOS;
		}

		@Override
		public void run() {
			try {
				// Read byte by byte from client and send directly to server
				byte[] buffer = new byte[4096];
				int read;
				do {
					read = proxyToClientIS.read(buffer);
					if (read > 0) {
						proxyToServerOS.write(buffer, 0, read);
						if (proxyToClientIS.available() < 1) {
							proxyToServerOS.flush();
						}
					}
				} while (read >= 0);
			} catch (SocketTimeoutException ste) {
				// TODO: handle exception
			} catch (IOException e) {
				System.out.println("Proxy to client HTTPS read timed out");
				e.printStackTrace();
			}
		}
	}


	private void blockedSiteRequested() {
		try {
			OutputStreamWriter outputStreamWriter = new OutputStreamWriter(clientSocket.getOutputStream());
			BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter);
			String line = "HTTP/1.0 403 Access Forbidden \n" + "User-Agent: ProxyServer/1.0\n" + "\r\n"
					+ "403 Access Forbidden";
			bufferedWriter.write(line, 0, line.length());
			bufferedWriter.flush();
			bufferedWriter.close();
		} catch (IOException e) {
			System.out.println("Error writing to client when requested a blocked site");
			e.printStackTrace();
		}
	}
}