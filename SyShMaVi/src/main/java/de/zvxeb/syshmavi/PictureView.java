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
package de.zvxeb.syshmavi;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

public class PictureView extends JFrame implements KeyListener {
	private static final long serialVersionUID = 1L;
	private JScrollPane jsp;
	private JLabel pictureLabel;
	
	
	public PictureView(BufferedImage img)
	{
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		
		pictureLabel = new JLabel(new ImageIcon(img));
		jsp = new JScrollPane(pictureLabel);
		
		add(jsp);
		
		addKeyListener(this);
	}
	
	public static void createPictureView(BufferedImage img)
	{
		final BufferedImage fimg = img;
		SwingUtilities.invokeLater
		(
			new Runnable() {
				public void run()
				{
					PictureView pv = new PictureView(fimg);
					pv.pack();
					
					Dimension screensize = Toolkit.getDefaultToolkit().getScreenSize();
					
					int hmax = (screensize.width * 80) / 100;
					int vmax = (screensize.height * 80) / 100;
					
					Dimension pvsize = pv.getSize();
					
					int nh = Math.min(hmax, pvsize.width);
					int nv = Math.min(vmax, pvsize.height);
					
					if(nh != pvsize.width || nv != pvsize.height)
					{
						pv.setSize(nh, nv);
					}

					pv.setVisible(true);
				}
			}
		);
	}

	@Override
	public void keyPressed(KeyEvent e) {
	}

	@Override
	public void keyReleased(KeyEvent e) {
		if(e.getKeyCode() == KeyEvent.VK_ESCAPE)
		{
			dispose();
		}
	}

	@Override
	public void keyTyped(KeyEvent e) {
	}
}
