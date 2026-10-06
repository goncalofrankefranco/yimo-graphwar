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

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JPanel;

import GraphServer.Constants;

public class GraphUI extends JPanel
{
	private JPanel[] screens;
	private JPanel currentScreen;
	private int currentScreenIndex;
	
	private Graphwar graphwar;
	
	public GraphUI(Graphwar graphwar) throws Exception
	{
		super();
		
		this.graphwar = graphwar;
		
		this.setLayout(new CardLayout());
		this.setPreferredSize(new Dimension(1100, 700));
		this.setMinimumSize(new Dimension(800, 600));
		
		screens = new JPanel[Constants.NUM_SCREENS];
		
		screens[Constants.MAIN_MENU_SCREEN] = new MainMenuScreen(graphwar, "/rsc/MainMenu.txt");
		screens[Constants.PRE_GAME_SCREEN] = new PreGameScreen(graphwar, "/rsc/PreGame.txt");
		screens[Constants.GLOBAL_ROOM_SCREEN] = new GlobalScreen(graphwar, "/rsc/GlobalRoom.txt");
		screens[Constants.GAME_SCREEN] = new GameScreen(graphwar, "/rsc/GameScreen.txt");
		screens[Constants.CAMPAIGN_SCREEN] = new CampaignScreen(graphwar);
		for(int i=0; i<screens.length; i++)
		{
			add(screens[i], Integer.toString(i));
		}
				
		currentScreenIndex = -1;
		currentScreen = null;
	}
	
	public void stop()
	{
		for(int i=0; i< screens.length; i++)
		{
			if(screens[i] instanceof StartStopPanel)
			{
				((StartStopPanel) screens[i]).stopPanel();
			}
		}
	}
	
	public void setScreen(int screenNum)
	{		
		if(currentScreenIndex != screenNum)
		{
			if(currentScreen instanceof StartStopPanel)
			{
				((StartStopPanel) currentScreen).stopPanel();
			}
			
			((CardLayout)getLayout()).show(this, Integer.toString(screenNum));
			this.revalidate();
			this.repaint();
			
			currentScreen = screens[screenNum];
			currentScreenIndex = screenNum;
			if(currentScreen instanceof StartStopPanel)
			{
				((StartStopPanel) currentScreen).startPanel();
			}
		}
	}
	
	public JPanel getScreen(int screenNum)
	{
		return screens[screenNum];
	}

	static String versionBadgeText()
	{
		String buildId = Constants.BUILD_ID;
		String prefix = "YIMO-Graphwar-";
		return buildId.startsWith(prefix) ? "YIMO Graphwar " + buildId.substring(prefix.length()) : buildId;
	}

	static void paintVersionBadge(Graphics graphics, int width, int height)
	{
		if(width < 80 || height < 40) return;
		Graphics2D g = (Graphics2D)graphics.create();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
			g.setFont(font);
			String text = versionBadgeText();
			FontMetrics metrics = g.getFontMetrics();
			int paddingX = 7;
			int paddingY = 4;
			int badgeWidth = metrics.stringWidth(text) + paddingX * 2;
			int badgeHeight = metrics.getHeight() + paddingY * 2;
			int x = 12;
			int y = height - badgeHeight - 10;
			g.setColor(new Color(8, 14, 20, 190));
			g.fillRoundRect(x, y, badgeWidth, badgeHeight, 10, 10);
			g.setColor(new Color(245, 242, 232, 220));
			g.drawString(text, x + paddingX, y + paddingY + metrics.getAscent());
		}
		finally
		{
			g.dispose();
		}
	}

	@Override
	protected void paintChildren(Graphics graphics)
	{
		super.paintChildren(graphics);
		paintVersionBadge(graphics, getWidth(), getHeight());
	}
	
}
