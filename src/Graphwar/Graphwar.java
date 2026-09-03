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

package Graphwar;

import java.awt.Dimension;
import java.io.IOException;

import javax.swing.JFrame;

import GraphServer.Constants;
import GraphServer.GraphServer;
import GraphServer.NetworkConfig;

public class Graphwar extends JFrame
{
	private GraphServer gameServer;
	private GameData gameData;
	private GlobalClient globalClient;
	private GraphUI graphUI;
	
	public static void main(String[] args)
	{
		handleArgs(args);
		
		Graphwar graphwar = new Graphwar();
				
		graphwar.init();
		
		// This is this way because it was adapted from an applet
		while(true)
		{
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public static void handleArgs(String[] args)
	{
		Constants.applyNetworkConfig(NetworkConfig.fromCommandLine(args));
	}
	
	public void init()
	{		
		setTitle("YIMO Graphwar");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBackground(new java.awt.Color(8, 14, 26));
		setResizable(true);
		
		try 
		{
			gameData = new GameData(this);
			globalClient = new GlobalClient(this);
			graphUI = new GraphUI(this);
		}
		catch (InterruptedException e) 
		{
			e.printStackTrace();
		} 
		catch (Exception e) 
		{
			e.printStackTrace();
		}
		
		setContentPane(graphUI);
		setMinimumSize(new Dimension(800, 600));
		pack();
		setLocationRelativeTo(null);
		setVisible(true);
		
		graphUI.setScreen(Constants.MAIN_MENU_SCREEN);
		
		this.validate();
		this.repaint();

	}
	
	public void start()
	{
		
	}
	
	public void stop()
	{
		globalClient.stop();
		gameData.disconnect();
		graphUI.stop();
		
		if(gameServer != null)
		{
			gameServer.finalize();
			gameServer = null;
		}
	}
	
	public void destroy()
	{
		
	}
	
	public GraphUI getUI()
	{
		return graphUI;
	}
	
	public GameData getGameData()
	{
		return gameData;
	}
	
	public GlobalClient getGlobalClient()
	{
		return globalClient;
	}
	
	public void finishGame()
	{
		if(gameServer != null)
		{
			gameServer.finalize();
			gameServer = null;
		}
	}
	
	public void joinGlobal(String name) throws IOException
	{
		globalClient.joinGlobalServer(Constants.GLOBAL_IP, Constants.GLOBAL_PORT, name);
		
		graphUI.setScreen(Constants.GLOBAL_ROOM_SCREEN);
	}
	
	public void joinGame(String ip, int port) throws IOException
	{
		gameData.connect(ip, port);		
	}

	public void joinGame(String ip, int port, String playerName) throws IOException
	{
		gameData.connect(ip, port, playerName);
	}
	
	public void createGame(int port) throws IOException
	{
		createGame(port, "Player");
	}

	public void createGame(int port, String playerName) throws IOException
	{
		gameServer = new GraphServer(port);
		new Thread(gameServer).start();
		
		gameData.connect("localhost", port, playerName);
		
		graphUI.setScreen(Constants.PRE_GAME_SCREEN);
	}
}
