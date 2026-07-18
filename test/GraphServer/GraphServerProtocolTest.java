package GraphServer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

/** A small socket smoke test for the server's canonical shot broadcast. */
public final class GraphServerProtocolTest
{
	private static void check(boolean condition, String message)
	{
		if(!condition)
		{
			throw new AssertionError(message);
		}
	}

	public static void main(String[] args) throws Exception
	{
		GraphServer server = new GraphServer();
		ServerSocket pair = new ServerSocket(0);
		Socket clientSocket = new Socket("127.0.0.1", pair.getLocalPort());
		Socket serverSocket = pair.accept();
		pair.close();
		ClientConnection client = new ClientConnection(server, serverSocket);
		server.clients.add(client);

		Player player = new Player("smoke");
		player.setNumSoldiers(1);
		client.addPlayer(player);
		server.players.add(player);

		server.startGame();
		BufferedReader input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		String message;
		while((message = input.readLine()) != null && !message.startsWith(NetworkProtocol.START_GAME+"&"))
		{
		}
		check(message != null, "the server must send a start message");

		server.handleMessage(NetworkProtocol.FIRE_FUNC+"&"+player.getID()+"&0", client);
		String shot = input.readLine();
		check(shot != null && shot.startsWith(NetworkProtocol.FIRE_FUNC+"&"),
				"accepted shots must be broadcast by the server");
		check(shot.split("&").length >= 5, "the broadcast must carry the authoritative hit fields");

		server.handleMessage(NetworkProtocol.FIRE_FUNC+"&"+player.getID()+"&0", client);
		Thread.sleep(100);
		check(!input.ready(), "a repeated shot must not be broadcast");

		clientSocket.close();
		server.finalize();
	}
}
