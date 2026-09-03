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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.IOException;
import java.util.ListIterator;

import javax.swing.JPanel;

import GraphServer.Constants;

public class RoomBoard extends JPanel implements MouseMotionListener, MouseListener
{
	private Graphwar graphwar;
	
	private int width;
	private int minHeight;
	private int height;
			
	private boolean roomFocused;
	private int focusedRoomNum;

	private final int entryHeight = 20;
	
	private final Color focusColor = new Color(255,174,0,75);
	
	public RoomBoard(Graphwar graphwar, int width, int minHeight)
	{
		this.graphwar = graphwar;
		
		this.width = width;
		this.minHeight = minHeight;
		this.height = minHeight;
		this.setOpaque(true);
		this.setBackground(YimoTheme.INPUT);
			
		Dimension desired = new Dimension(Math.max(width, getWidth()), height);
		if(!desired.equals(this.getPreferredSize()))
		{
			this.setPreferredSize(desired);
			this.revalidate();
		}
		
		this.addMouseListener(this);
		this.addMouseMotionListener(this);
	}
	
	private void resize()
	{
		this.height = entryHeight*graphwar.getGlobalClient().getRooms().size();
		
		if(height < minHeight)
		{
			height = minHeight;
		}
				
		this.setPreferredSize(new Dimension(width, height));
		this.revalidate();
	}
	
	public void paintComponent(Graphics g)
	{		
		super.paintComponent(g);
		resize();
		
		int drawWidth = Math.max(1, this.getWidth());
		g.setColor(YimoTheme.INPUT);
		
		g.fillRect(0, 0, drawWidth-1, Math.max(0, this.getHeight()-1));
		
		g.setColor(new Color(56, 90, 112));
				
		Graphics2D g2d = (Graphics2D)g;
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setFont(new Font("Sans", Font.BOLD, 13));
		drawWidth = Math.max(1, this.getWidth());
	
		ListIterator<Room> itr = graphwar.getGlobalClient().getRooms().listIterator();    	
		int i=0;
    	while(itr.hasNext())
    	{
    		Room room = itr.next();
    		
			if(roomFocused)
			{
				if(focusedRoomNum == i)
				{
					g2d.setColor(new Color(242, 163, 91, 90));
					g2d.fillRect(0, entryHeight*i, drawWidth, entryHeight);
				}
			}
				
			g2d.setColor(YimoTheme.TEXT);
			g2d.drawString(" "+room.getName(), 8, entryHeight*(i+1)-4);
			
			String mode = "y";
			
			if(room.getGameMode()==1)
			{
				mode = "y'";
			}
			else if(room.getGameMode()==2)
			{
				mode = "y''";
			}
			
			g2d.drawString(mode, Math.max(8, drawWidth-42), entryHeight*(i+1)-7);
			
			g2d.drawString(room.getNumPlayers()+"/10", Math.max(8, drawWidth-110), entryHeight*(i+1)-6);
			
			g2d.setColor(new Color(56, 90, 112));
			g2d.drawLine(0, entryHeight*(i+1)-1, drawWidth-1, entryHeight*(i+1)-1);
			
			i++;
		}		
	}

	public synchronized void mouseMoved(MouseEvent e)
	{		
		if(roomFocused)
		{
			int lastFocus = focusedRoomNum;
			
			focusedRoomNum = e.getY()/entryHeight;
			
			if(focusedRoomNum != lastFocus)
			{
				graphwar.getUI().getScreen(Constants.GLOBAL_ROOM_SCREEN).repaint();
			}
			
		}
		else
		{
			roomFocused = true;
			focusedRoomNum = e.getY()/entryHeight;
			graphwar.getUI().getScreen(Constants.GLOBAL_ROOM_SCREEN).repaint();
		}		
	}

	public void mouseClicked(MouseEvent e) 
	{
		
	}

	public void mouseEntered(MouseEvent e) 
	{
		
	}

	public void mouseExited(MouseEvent e)
	{
		roomFocused = false;
		
		graphwar.getUI().getScreen(Constants.GLOBAL_ROOM_SCREEN).repaint();
	}

	public void mousePressed(MouseEvent e) 
	{
		
	}

	public void mouseReleased(MouseEvent e) 
	{
		if(graphwar.getGameData().getGameState() == Constants.NONE)
		{
			int roomNum = e.getY()/entryHeight;
			
			if(roomNum < graphwar.getGlobalClient().getRooms().size())
			{
				Room room = graphwar.getGlobalClient().getRooms().get(roomNum);
				
				((GlobalScreen)graphwar.getUI().getScreen(Constants.GLOBAL_ROOM_SCREEN)).showMessage("Connecting...");
				
				try 
				{			
					graphwar.joinGame(room.getIp(), room.getPort());
					graphwar.getGameData().addPlayer(graphwar.getGlobalClient().getLocalPlayerName());
					graphwar.getUI().setScreen(Constants.PRE_GAME_SCREEN);		
				} 
				catch (IOException e1)
				{
					((GlobalScreen)graphwar.getUI().getScreen(Constants.GLOBAL_ROOM_SCREEN)).showMessage("Could not connect.");
					graphwar.getGameData().disconnect();
					e1.printStackTrace();
				}				
			}
		}
	}

	public void mouseDragged(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}
}
