/*
    Copyright 2008-2022 Hendrik Iben

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.package com.github.hiben;
*/
package de.zvxeb.jres.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;

import javax.swing.JComponent;

public class ImageDisplay extends JComponent {
	private static final long serialVersionUID = 1L;
	
	private BufferedImage image;
	private int scale;
	private int width, height;
	private Dimension size;
	
	private BufferedImageOp scaler;
	
	public ImageDisplay(BufferedImage image, int scale)
	{
		super();
		setScale(scale);
		setImage(image);
	}
	
	public BufferedImage getImage()
	{
		return image;
	}
	
	public int getScale()
	{
		return scale;
	}
	
	public void setScale(int scale)
	{
		if(this.scale == scale)
			return;
		
		this.scale = scale;
		if(this.scale==0)
		{
			this.scale = 1;
		}
		
		if(this.scale!=1)
		{
			AffineTransform at = AffineTransform.getScaleInstance(this.scale, this.scale);
			scaler = new AffineTransformOp(at, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
		}
		else
		{
			scaler = null;
		}
		
		sizeChanged();
	}
	
	public void setImage(BufferedImage img)
	{
		if(this.image == img)
			return;
		
		this.image = img;
		if(this.image==null)
		{
			width = 100;
			height = 100;
		}
		else
		{
			width = image.getWidth();
			height = image.getHeight();
		}
		sizeChanged();
	}
	
	private void sizeChanged()
	{
		size = new Dimension(width * scale, height * scale);
		setMinimumSize(size);
		setMaximumSize(size);
		setPreferredSize(size);
		setSize(size);
		invalidate();
		repaint();
	}
	
	@Override
	public void setBounds(int x, int y, int width, int height)
	{
		super.setBounds(x, y, size.width * scale, size.height * scale);
	}
	
	@Override
	public void paintComponent(Graphics g)
	{
		Graphics2D g2d = (Graphics2D)g;
		g2d.drawImage(image, scaler, 0, 0);
	}
	
	public Point getPointInImage(Point componentPoint)
	{
		if(scale==1)
			return componentPoint;
		
		if(scale<0)
		{
			return new Point(width - componentPoint.x / (-scale), height - componentPoint.y / (-scale));
		} 
		else
		{
			return new Point(componentPoint.x / scale, componentPoint.y / scale);
		}
	}
}
