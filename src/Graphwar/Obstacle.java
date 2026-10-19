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
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Random;
import GraphServer.Constants;
import GraphServer.MapShape;

public class Obstacle
{
	private	BufferedImage terrain;
	private Graphics2D terrainGraphics;
	private final BufferedImage[] displayImages = new BufferedImage[AppearanceSettings.ArenaTheme.values().length];
	
	int expX;
	int expY;
	int expRadius;
	
	public Obstacle(int numCircles, int circleInfo[])
	{
		terrain = new BufferedImage(Constants.PLANE_LENGTH, Constants.PLANE_HEIGHT, BufferedImage.TYPE_3BYTE_BGR);
		
		terrainGraphics = (Graphics2D)terrain.getGraphics();
		terrainGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);

		
		terrainGraphics.setColor(Color.WHITE);
		terrainGraphics.fillRect(0, 0, terrain.getWidth(), terrain.getHeight());
		
		terrainGraphics.setColor(Color.BLACK);
		
		for(int i=0; i<numCircles; i++)
		{			
			int x = circleInfo[3*i];
			int y = circleInfo[3*i+1];
			int radius = circleInfo[3*i+2];
			
			terrainGraphics.fillOval(x-radius, y-radius, 2*radius, 2*radius);
		}
		
		//transparent = new Color(0,0,0,0);
		//terrainGraphics.setComposite(AlphaComposite.Src);
		//terrainGraphics.setColor(transparent);
		terrainGraphics.setColor(Color.WHITE);
		
		expX = 0;
		expY = 0;
		expRadius = 0;
	}

	public Obstacle(int numShapes, MapShape shapes[])
	{
		terrain = new BufferedImage(Constants.PLANE_LENGTH, Constants.PLANE_HEIGHT, BufferedImage.TYPE_3BYTE_BGR);
		terrainGraphics = (Graphics2D)terrain.getGraphics();
		terrainGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
		terrainGraphics.setColor(Color.WHITE);
		terrainGraphics.fillRect(0, 0, terrain.getWidth(), terrain.getHeight());
		terrainGraphics.setColor(Color.BLACK);

		for(int i=0; i<numShapes; i++)
		{
			MapShape shape = shapes[i];
			if(shape.getType() == MapShape.CIRCLE)
			{
				int radius = shape.getA();
				terrainGraphics.fillOval(shape.getX()-radius, shape.getY()-radius, radius*2, radius*2);
			}
			else
			{
				terrainGraphics.fillRect(shape.getX(), shape.getY(), shape.getA(), shape.getB());
			}
		}

		terrainGraphics.setColor(Color.WHITE);
		expX = 0;
		expY = 0;
		expRadius = 0;
	}
		
	public static int getNumCircles()
	{
		Random random = new Random();
		
		int numCircles = (int)(random.nextGaussian()*Constants.NUM_CIRCLES_STANDARD_DEVIATION+Constants.NUM_CIRCLES_MEAN_VALUE);
		
		while(numCircles < 0)
		{
			numCircles = (int)(random.nextGaussian()*Constants.NUM_CIRCLES_STANDARD_DEVIATION+Constants.NUM_CIRCLES_MEAN_VALUE);
		}	
		
		return numCircles;
	}
	
	public int getExplosionX()
	{
		return expX;
	}
	
	public int getExplosionY()
	{
		return expY;
	}
	
	public int getExplosionRadius()
	{
		return expRadius;
	}
	
	public boolean collidePoint(int x, int y)
	{		
		if(x<0 || x>=Constants.PLANE_LENGTH)
			return true;
		
		if(y<0 || y>=Constants.PLANE_HEIGHT)
			return true;
		
		if(terrain.getRGB(x, y) != -1)
			return true;
		
		return false;
	}
	
	public void setExplosion(int x, int y, int radius)
	{
		expX = x;
		expY = y;
		expRadius = radius;
	}
	
	public void explodePoint()
	{
		terrainGraphics.fillOval(expX-expRadius, expY-expRadius, expRadius*2, expRadius*2);
		for(int i=0; i<displayImages.length; i++)
		{
			displayImages[i] = null;
		}
	}
	
	public boolean soldierCollides(int x, int y, int radius)
	{		
		if(x+radius >= Constants.PLANE_LENGTH)
		{
			return true;
		}
		if(x-radius < 0)
		{
			return true;
		}		
		if(y+radius >= Constants.PLANE_HEIGHT)
		{
			return true;
		}
		if(y-radius < 0)
		{
			return true;
		}
		
		if( terrain.getRGB(x, y) == -1)
		{	
			if(terrain.getRGB(x+radius, y) == -1)
			{
				if(terrain.getRGB(x-radius, y) == -1)
				{
					if(terrain.getRGB(x, y+radius) == -1)
					{
						if(terrain.getRGB(x, y-radius) == -1)
						{
							return false;
						}
					}
				}
			}
			
		}
		
		return true;
	}
	
	public Image getImage()
	{
		return terrain;
	}

	public BufferedImage getDisplayImage(AppearanceSettings.ArenaTheme theme)
	{
		if(theme == null || theme == AppearanceSettings.ArenaTheme.CLASSIC)
		{
			return terrain;
		}
		int index = theme.ordinal();
		if(displayImages[index] == null)
		{
			displayImages[index] = createDisplayImage(theme);
		}
		return displayImages[index];
	}

	private BufferedImage createDisplayImage(AppearanceSettings.ArenaTheme theme)
	{
		Color background = theme == AppearanceSettings.ArenaTheme.GARDEN
				? YimoTheme.GARDEN_BACKGROUND : YimoTheme.ARCADE_BACKGROUND;
		Color obstacle = theme == AppearanceSettings.ArenaTheme.GARDEN
				? YimoTheme.GARDEN_OBSTACLE : YimoTheme.ARCADE_OBSTACLE;
		BufferedImage image = new BufferedImage(terrain.getWidth(), terrain.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(background);
		graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		if(theme == AppearanceSettings.ArenaTheme.GARDEN)
		{
			graphics.setColor(new Color(74, 125, 94, 32));
			for(int y=12; y<image.getHeight(); y+=24)
			{
				for(int x=12+(y/24%2)*12; x<image.getWidth(); x+=24)
				{
					graphics.fillOval(x, y, 3, 3);
				}
			}
		}
		else
		{
			graphics.setColor(new Color(89, 111, 173, 28));
			graphics.setStroke(new BasicStroke(1.0f));
			for(int x=0; x<image.getWidth(); x+=32) graphics.drawLine(x, 0, x, image.getHeight());
			for(int y=0; y<image.getHeight(); y+=32) graphics.drawLine(0, y, image.getWidth(), y);
		}
		graphics.dispose();

		for(int y=0; y<terrain.getHeight(); y++)
		{
			int[] displayRow = image.getRGB(0, y, image.getWidth(), 1, null, 0, image.getWidth());
			int[] maskRow = terrain.getRGB(0, y, terrain.getWidth(), 1, null, 0, terrain.getWidth());
			for(int x=0; x<displayRow.length; x++)
			{
				int coverage = 255 - (maskRow[x] & 0xff);
				if(coverage == 0)
				{
					continue;
				}
				int base = displayRow[x];
				int red = blend((base >>> 16) & 0xff, obstacle.getRed(), coverage);
				int green = blend((base >>> 8) & 0xff, obstacle.getGreen(), coverage);
				int blue = blend(base & 0xff, obstacle.getBlue(), coverage);
				displayRow[x] = 0xff000000 | (red << 16) | (green << 8) | blue;
			}
			image.setRGB(0, y, image.getWidth(), 1, displayRow, 0, image.getWidth());
		}
		return image;
	}

	private int blend(int base, int color, int coverage)
	{
		return (base * (255 - coverage) + color * coverage) / 255;
	}
	
}
