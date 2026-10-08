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

package RoomServer;

import java.io.IOException;

import GraphServer.ClientConnection;
import GraphServer.Constants;
import GraphServer.GraphServer;
import GraphServer.Player;
import GraphServer.RoomAccessPolicy;
import GraphServer.TournamentResultReporter;

public class RemoteGraphServer extends GraphServer
{
	private GlobalClient globalClient;
	private boolean tournamentResultReported;
	private boolean tournamentResultReporting;
	
	public int getNumClients()
	{
		return this.clients.size();
	}
	
	public int getNumPlayers()
	{
		return this.players.size();
	}
	
	public int getGameState()
	{
		return this.gameState;
	}
	
	public boolean isAcceptingConnections()
	{
		return this.acceptingConnections;
	}
		
	public RemoteGraphServer(GlobalClient globalClient) throws IOException
	{
		super();
		
		this.globalClient = globalClient;
		tournamentResultReported = false;
	}

	/** Hidden tournament rooms may use the same status bridge with a required signed token. */
	public RemoteGraphServer(GlobalClient globalClient, RoomAccessPolicy roomAccessPolicy) throws IOException
	{
		super(roomAccessPolicy);

		this.globalClient = globalClient;
		tournamentResultReported = false;
	}

	public RemoteGraphServer(GlobalClient globalClient, int port, RoomAccessPolicy roomAccessPolicy) throws IOException
	{
		super(port, roomAccessPolicy);
		this.globalClient = globalClient;
		tournamentResultReported = false;
	}

	public RemoteGraphServer(GlobalClient globalClient, int port, RoomAccessPolicy roomAccessPolicy,
			int tournamentRound) throws IOException
	{
		super(port, roomAccessPolicy, tournamentRound);
		this.globalClient = globalClient;
		tournamentResultReported = false;
	}

	protected void sendAddPlayerMessage(Player player, ClientConnection playerFrom)
	{
		super.sendAddPlayerMessage(player, playerFrom);
		
		if(globalClient != null) globalClient.sendRoomStatus(this.gameMode, this.players.size());
	}
	
	protected boolean removePlayer(int playerID, ClientConnection client)
	{
		boolean v = super.removePlayer(playerID, client);
		
		if(globalClient != null) globalClient.sendRoomStatus(this.gameMode, this.players.size());
		
		return v;
	}
	
	protected void sendModeMessage()
	{
		super.sendModeMessage();
		
		if(globalClient != null) globalClient.sendRoomStatus(this.gameMode, this.players.size());
	}
	
	public void removeClient(ClientConnection client)
	{
		String[] result = null;
		String reason = "FORFEIT";
		if(isTournamentRoom() && this.gameState == Constants.GAME)
		{
			result = tournamentResult();
			if(result != null) reason = "NORMAL";
			else result = tournamentForfeitResult(client);
		}
		super.removeClient(client);
		if(isTournamentRoom())
		{
			if(result != null) reportTournamentResult(result, reason);
			return;
		}
		if(this.clients.size() == 0)
		{
			if(this.gameState == Constants.GAME)
			{
				this.goPreGame();
				if(globalClient != null) globalClient.recreateRoom();
			}
		}
		if(globalClient != null) globalClient.sendRoomStatus(this.gameMode, this.players.size());
	}
	
	protected void startGame()
	{
		 super.startGame();
		 if(globalClient != null) globalClient.hideRoom();
	}
	
	protected void finishGame(ClientConnection client)
	{
		super.finishGame(client);
		if(isTournamentRoom())
		{
			if(this.gameState == Constants.PRE_GAME)
			{
				String[] result = tournamentResult();
				if(result != null) reportTournamentResult(result, "NORMAL");
			}
			return;
		}
		if(this.gameState == Constants.PRE_GAME && globalClient != null)
		{
			globalClient.recreateRoom();
		}
	}

	private synchronized void reportTournamentResult(String[] result, String reason)
	{
		if(tournamentResultReported || tournamentResultReporting) return;
		tournamentResultReporting = true;
		stopAcceptingConnections();
		final String[] savedResult = result.clone();
		final String savedReason = reason;
		Thread reporter = new Thread(new Runnable()
		{
			public void run()
			{
				while(true)
				{
					try
					{
						String secret = System.getenv("YIMO_ROOM_HMAC_SECRET");
						if(secret == null || secret.length() == 0)
						{
							throw new IOException("YIMO_ROOM_HMAC_SECRET is missing");
						}
						TournamentResultReporter.reportUntilAccepted(Constants.TOURNAMENT_API_BASE_URL,
								savedResult[0], savedResult[1], savedResult[2], savedReason, secret);
						synchronized(RemoteGraphServer.this)
						{
							tournamentResultReported = true;
						}
						RemoteGraphServer.this.finalize();
						return;
					}
					catch(Exception error)
					{
						System.err.println("Tournament result remains pending; retrying: " + error.getMessage());
						try
						{
							Thread.sleep(5000L);
						}
						catch(InterruptedException ignored)
						{
							// Keep the result pending rather than discarding it during shutdown.
						}
					}
				}
			}
		}, "yimo-result-reporter-" + result[0]);
		reporter.setDaemon(false);
		reporter.start();
	}
}
