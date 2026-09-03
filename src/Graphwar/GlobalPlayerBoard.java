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

import javax.swing.JPanel;


public class GlobalPlayerBoard extends JPanel 
{
	private Graphwar graphwar;
	
	private int width;
	private int minHeight;
	
	private final int entryHeight = 20;
	
	public GlobalPlayerBoard(Graphwar graphwar, int width, int minHeight)
	{
		this.graphwar= graphwar;
		
		this.width = width;
		this.minHeight = minHeight;
		setOpaque(true);
		setBackground(YimoTheme.INPUT);
	}
	
	public void resize()
	{		
		int height = entryHeight*graphwar.getGlobalClient().getGlobalPlayers().length;
		
		if(height < minHeight)
		{
			height = minHeight;
		}
		
		Dimension desired = new Dimension(Math.max(width, getWidth()), height);
		if(!desired.equals(this.getPreferredSize()))
		{
			this.setPreferredSize(desired);
			this.revalidate();
		}
	}
	
	public void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		resize();
		
		g.setColor(YimoTheme.INPUT);
		
		g.fillRect(0, 0, Math.max(0, this.getWidth()-1), Math.max(0, this.getHeight()-1));
		
		g.setColor(new Color(56, 90, 112));
		
		//g.drawRect(0, 0, width, height);
		
		Graphics2D g2d = (Graphics2D)g;
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setFont(new Font("Sans", Font.BOLD, 13));
		
		String[] playersNames = graphwar.getGlobalClient().getGlobalPlayers();
		
		
		for(int i=0; i<playersNames.length; i++)
		{
			g2d.setColor(YimoTheme.TEXT);
			g2d.drawString(" "+playersNames[i], 8, entryHeight*(i+1)-5);
			g2d.setColor(new Color(56, 90, 112));
			g2d.drawLine(0, entryHeight*(i+1)-1, this.getWidth()-1, entryHeight*(i+1)-1);
		}
	}
}
