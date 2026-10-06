//  Copyright (C) 2011 Lucas Catabriga Rocha <catabriga90@gmail.com>
//    
//  This file is part of Graphwar.
//
//  Graphwar is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.
//
//  Graphwar is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.

//  You should have received a copy of the GNU General Public License
//  along with Graphwar.  If not, see <http://www.gnu.org/licenses/>.

package GraphServer;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


public class ClientConnection implements Runnable
{
	private static final Timer HANDSHAKE_TIMER = new Timer("graphserver-handshake-deadlines", true);

	private Connection connection;	
	private List<Player> players;	
	private GraphServer server;	
	private boolean running;	
	private boolean leader;
	private boolean readyNextTurn;	
	private boolean gameFinished;
	private boolean skipLevel;
	private RoomAccessPolicy roomAccessPolicy;
	private RoomAccessToken.Payload roomAccessPayload;
	
	public ClientConnection(GraphServer server, Socket socket) throws IOException
	{
		this(server, socket, RoomAccessPolicy.open());
	}

	public ClientConnection(GraphServer server, Socket socket, RoomAccessPolicy roomAccessPolicy) throws IOException
	{
		this(server, socket, roomAccessPolicy, Constants.TIMEOUT_CONNECTING);
	}

	ClientConnection(GraphServer server, Socket socket, RoomAccessPolicy roomAccessPolicy,
			long handshakeTimeoutMillis) throws IOException
	{
		this.server = server;
		this.roomAccessPolicy = roomAccessPolicy;
		TimerTask deadline = new TimerTask()
		{
			public void run()
			{
				try { socket.close(); } catch(IOException ignored) { }
			}
		};
		HANDSHAKE_TIMER.schedule(deadline, Math.max(1L, handshakeTimeoutMillis));
		try
		{
			this.connection = new Connection(socket);
			String hello = connection.readMessage();
			String response = NetworkProtocol.handshakeResponse(hello);
			connection.sendMessage(response);
			if(NetworkProtocol.isHandshakeAccepted(response) == false)
			{
				connection.close();
				throw new IOException("Rejected incompatible YIMO client");
			}
			if(roomAccessPolicy != null && roomAccessPolicy.isRequired())
			{
				String accessMessage = connection.readMessage();
				String[] fields = accessMessage == null ? new String[0] : accessMessage.split("&", -1);
				RoomAccessToken.Payload payload = fields.length == 2 && NetworkProtocol.TOURNAMENT_JOIN.equals(fields[0])
						? roomAccessPolicy.accept(fields[1], System.currentTimeMillis()) : null;
				if(payload == null)
				{
					connection.sendMessage(NetworkProtocol.TOURNAMENT_REJECTED);
					connection.close();
					throw new IOException("Rejected invalid tournament room token");
				}
				this.roomAccessPayload = payload;
				connection.sendMessage(NetworkProtocol.TOURNAMENT_ACCEPTED+"&"+payload.getMatchId()+"&"
						+payload.getParticipantId()+"&"+payload.getRoomSlot());
			}
		}
		finally
		{
			deadline.cancel();
		}
			
		this.players = new ArrayList<Player>();
		
		this.running = false;		
		this.leader = false;
		this.readyNextTurn = false;		
		this.gameFinished = false;
		this.skipLevel = false;
	}
	
	public List<Player> getPlayers()
	{
		return players;		
	}

	public String getTournamentParticipantId()
	{
		return roomAccessPayload == null ? null : roomAccessPayload.getParticipantId();
	}

	public String getTournamentDisplayName()
	{
		return roomAccessPayload == null ? null : roomAccessPayload.getDisplayName();
	}
	
	public boolean isLeader()
	{
		return leader;
	}
	
	public void setLeader(boolean leader)
	{
		this.leader = leader;
	}
	
	public boolean getSkipLevel()
	{
		return skipLevel;
	}
	
	public void setSkipLevel(boolean skip)
	{
		this.skipLevel = skip;
	}
	
	public boolean isFinished()
	{
		return gameFinished;
	}
	
	public void setFinished(boolean finished)
	{
		this.gameFinished = finished;
	}
	
	public void removePlayer(Player player)
	{
		players.remove(player);
	}
	
	public void addPlayer(Player player)
	{
		players.add(player);
	}
	
	public boolean getReadyNextTurn()
	{
		return this.readyNextTurn;
	}
	
	public void setReadyNextTurn(boolean ready)
	{
		this.readyNextTurn = ready;
	}
	
	public boolean checkTimeout()
	{
		if(System.currentTimeMillis() - connection.getLastReceivedTime() > Constants.TIMEOUT_DROP)
		{
			return true;
		}
		
		return false;
	}

	public boolean checkStayAliveTime()
	{
		if(System.currentTimeMillis() - connection.getLastSentTime() > Constants.TIMEOUT_KEEPALIVE)
		{
			return true;
		}
		
		return false;
	}
	
	public void disconnect()
	{
		running = false;
		if(roomAccessPayload != null && roomAccessPolicy != null)
		{
			roomAccessPolicy.release(roomAccessPayload.getParticipantId());
			roomAccessPayload = null;
		}
						
		try 
		{
			connection.close();
		}
		catch (IOException e) 
		{
			e.printStackTrace();
		}
	}
	
	public void sendMessage(String message)
	{
		connection.sendMessage(message);
	}
	
	public void sendKeepAlive()
	{
		connection.sendMessage(NetworkProtocol.NO_INFO+"");
	}
	
	public void run() 
	{
		running = true;
		
		while(running)
		{
			try 
			{
				String message = connection.readMessage();
																
				if(message == null)
				{
					server.removeClient(this);
					disconnect();
				}
				else
				{
					server.handleMessage(message, this);
					
					if(checkStayAliveTime())
					{
						sendKeepAlive();
					}
				}
			} 
			catch (SocketTimeoutException e)
			{
				//e.printStackTrace();
				
				if(checkTimeout())
				{
					server.removeClient(this);
					disconnect();
				}
				else
				{
					sendKeepAlive();
				}
			}
			catch (IOException e) 
			{
				if(running) System.out.println("Client connection closed ("+e.getClass().getSimpleName()+").");
				
				server.removeClient(this);
				disconnect();
			}
		}
	}
}
