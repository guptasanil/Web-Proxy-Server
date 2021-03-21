import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Scanner;


public class Server implements Runnable {


	public static void main(String[] args) {
		Server server = new Server(3000); // Create server which listens for incoming connections
		server.listen();
	}

	private ServerSocket serverSocket; // Implements server socket
	
	private boolean active = true; // Signifies whether server is active or not
	
	static HashMap<String, String> cachedItems; // HashMap where cached items are stored
	
	static ArrayList<String> blocked; // ArrayList where all blocked items are stored

	static ArrayList<Thread> currentThreads; // ArrayList with all threads running

	
	public Server(int port) { // Creates server (which runs from given port number)

		
		cachedItems = new HashMap<>(); // // HashMap where cached items are stored
		blocked = new ArrayList<>(); // ArrayList where all blocked items are stored
		currentThreads = new ArrayList<>(); // ArrayList with all threads running

		
		new Thread(this).start(); // Begin excecution on thread

		try {
			serverSocket = new ServerSocket(port); // Create the Server Socket for the proxy server
			System.out.println("Port: " + serverSocket.getLocalPort() + "\nWaiting for client...");
			active = true;
		}
		catch (SocketException se) {
			System.out.println("Socket Exception");
			se.printStackTrace();
		} catch (SocketTimeoutException ste) {
			System.out.println("Timeout occured");
		} catch (IOException io) {
			System.out.println("IO exception");
		}
	}


	public void listen() {

		while (active) {
			try {
				Socket socket = serverSocket.accept(); // Listens for connection to be made and accepts it

				Thread thread = new Thread(new Handler(socket)); // Creates thread and passes through handler

				currentThreads.add(thread); // adds thread to list of threads

				thread.start(); // Begins excecution on thread
			} catch (SocketException e) {
				System.out.println("Server closed");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	
	private void closeProxy() {
		System.out.println("\nClosing Server..");
		active = false; 

		try {
			
			for (Thread thread : currentThreads) { // loops through open threads and closes them
				if (thread.isAlive()) {
					System.out.print("Closing...");
					thread.join();
					System.out.println(" Closed");
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		try {
			System.out.println("Terminating Connection");
			serverSocket.close(); // Closes server socket
		} catch (Exception e) {
			System.out.println("Exception");
			e.printStackTrace();
		}
	}

	public static String getFromCached(String url) { //Gets file from cached list
		return cachedItems.get(url);
	}

	
	public static void addToCached(String url, String body) { //Adds URL to cached list
		cachedItems.put(url, body);
	}

	public static boolean isBlocked(String url) { // Checks if URL is on blocked list
		if (blocked.contains(url)) {
			System.out.println("blocked");
			return true;
		} else {
			System.out.println("Not blocked");
			return false;
		}
	}

	
	public void run() {
		Scanner scanner = new Scanner(System.in);

		String command;
		while (active) {
			System.out.println(
					"To block a website, type 'b ' followed by the URL \nTo see blocked websites, type 'sb' "
					+ "\nTo unblock, type 'ub' followed by the URL \nType 'c' to close the server");
			command = scanner.nextLine();
			if (command.toLowerCase().equals("sb")) { // Show block list
				System.out.println("\nBlocked Sites:");
				for(String S: blocked) {
					   System.out.println(S);
					}
				System.out.println();
			}
			else if(command.contains("ub")) { // Add to block list
				command = command.substring(3);
				command = "http://" + command + "/";
				blocked.remove(command);
				System.out.println("\n" + command + " unblocked successfully \n");
			}
			else if(command.contains("b")) {  // Remove from block list
				command = command.substring(2);
				command = "http://" + command + "/";
				blocked.add(command);
				System.out.println("\n" + command + " blocked successfully \n");	
			}
			
			else if (command.equals("c")) { // Close server
				active = false;
				closeProxy();
			}
		}
		scanner.close();
	}

}
