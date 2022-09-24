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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Path;
import java.util.Arrays;

import de.zvxeb.jres.MapTile;
import de.zvxeb.jres.ResBitmap;
import de.zvxeb.jres.ResManager;
import de.zvxeb.jres.SSLogic;
import de.zvxeb.jres.SSMap;
import de.zvxeb.jres.SSObject;
import de.zvxeb.jres.SSTexture;
import de.zvxeb.jres.TextureProperties;
import de.zvxeb.jres.SSTexture.TextureID;
import de.zvxeb.jres.SSTexture.TextureSize;

import javax.swing.*;

public class Main {

	public static void printf(String format, Object...args)
	{
		System.out.println(String.format(format, args));
	}

	public static void printerrf(String format, Object...args)
	{
		System.err.println(String.format(format, args));
	}
	
	public static void showMap(ResManager rm, byte [] palette, SSMap map)
	{
		short [] textures = map.getUsedTextures();
		
		if(textures==null)
			return;
		
		TextureSize tsize = TextureSize.TS32;
		int texsize = 32;
		
		int mw = map.getHorzSize();
		int mh = map.getVertSize();
		
		int iw = texsize * mw;
		int ih = texsize * mh;
		
		BufferedImage bimg = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = bimg.createGraphics();
		
		AffineTransform rotations [] = new AffineTransform [4];
		rotations[0] = null;
		rotations[1] = new AffineTransform();
		rotations[1].rotate(Math.PI/2.0, texsize/2, texsize/2);
		rotations[2] = new AffineTransform();
		rotations[2].rotate(Math.PI, texsize/2, texsize/2);
		rotations[3] = new AffineTransform();
		rotations[3].rotate(Math.PI + Math.PI/2.0, texsize/2, texsize/2);

		AffineTransformOp rotops [] = new AffineTransformOp [4];
		
		for(int i=0; i<rotations.length; i++)
			if(rotations[i]!=null)
			{
				rotops[i] = new AffineTransformOp(rotations[i], AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
			}
			else
			{
				rotops[i] = null;
			}
		
		int [] polyx = null;
		int [] polyy = null;

		for(int tiley = 0; tiley < map.getVertSize(); tiley++)
		{
			// maps are stored top to bottom (from my personal POV that is...)
			int iy = (mh - 1) - tiley;
			for(int tilex = 0; tilex < map.getHorzSize(); tilex++)
			{
				MapTile mt = map.getTile(tilex, tiley);
				
				MapTile.Type mtt = mt.getType();
				
				if(mtt == MapTile.Type.Solid)
					continue;
				
				TextureID tid = new TextureID(mt.getTextureFloor(), tsize);
				ResBitmap rb = SSTexture.getTexture(rm, tid);

				int xpos = tilex * texsize;
				int xp1pos = xpos + texsize;
				int ypos = iy * texsize;
				int yp1pos = ypos + texsize;

				g2d.drawImage(rb.getImage(palette), rotops[mt.getFRot()%4], xpos, ypos);
				
				// triangular base
				if
				(
					   mtt.compareTo(MapTile.DiagonalBase) >= 0 
					&& mtt.compareTo(MapTile.SlopeBase) < 0
				)
				{
					/*
					 * ***
					 *  ** 
					 *   * 
					 */
					if(mtt == MapTile.Type.DiagonalNE)
					{
						//g2d.setColor(Color.CYAN);
						polyx = new int [] { xpos,	xp1pos,	xpos };
						polyy = new int [] { ypos,	yp1pos,	yp1pos }; 
					}
					/*
					 * ***
					 * ** 
					 * * 
					 */
					else if(mtt == MapTile.Type.DiagonalNW)
					{
						//g2d.setColor(Color.RED);
						polyx = new int [] { xp1pos,	xp1pos,	xpos };
						polyy = new int [] { ypos,		yp1pos,	yp1pos };
					}
					/*
					 *   *
					 *  ** 
					 * *** 
					 */
					else if(mtt == MapTile.Type.DiagonalSE)
					{
						//g2d.setColor(Color.GREEN);
						polyx = new int [] { xpos,	xp1pos,	xpos };
						polyy = new int [] { ypos,	ypos,	yp1pos };
					}
					/*
					 * * 
					 * **
					 * ***
					 */
					else if(mtt == MapTile.Type.DiagonalSW)
					{
						//g2d.setColor(Color.BLUE);
						polyx = new int [] { xpos,	xp1pos,	xp1pos };
						polyy = new int [] { ypos,	ypos,	yp1pos };
					}

					g2d.setColor(Color.BLACK);
					g2d.fillPolygon(polyx, polyy, 3);
				}
				
				
				// show floor rotation
				//g2d.setColor(Color.WHITE);
				//g2d.drawString("" + mt.getFRot(), xpos + 2, ypos + 32);
				
			}
		}
		
		PictureView.createPictureView(bimg);
	}
	
	public static void showTextures(ResManager rm, byte [] palette, SSMap map)
	{
		short [] textures = map.getUsedTextures();
		
		if(textures==null)
			return;
		
		int texh = 8;
		int texv = (textures.length + 7) / 8;

		printf("There are %d textures -> %dx%d...", textures.length, texh, texv);
		
		int texwidth = texh * (128 + 5);
		
		int texheight = texv * (128 + 5);
		
		BufferedImage teximg = new BufferedImage(texwidth, texheight, BufferedImage.TYPE_INT_RGB);
		
		Graphics2D g2d = teximg.createGraphics();
		
		int texx = 0;
		int texy = 0;
		
		int texi = 0;
		
		while(texi < textures.length)
		{
			if(texi != 0 && (texi % texh) == 0)
			{
				texx = 0;
				texy += 128 + 5;
			}
			
			TextureID tid = new TextureID(textures[texi], TextureSize.TS128);
			ResBitmap rb = SSTexture.getTexture(rm, tid);
			
			if(rb!=null)
			{
				if(palette!=null)
				{
					rb.setPalette(palette);
				}
				
				BufferedImage bimg = rb.getImage();
				
				g2d.drawImage(bimg, null, texx, texy);
				
				g2d.drawString("" + textures[texi], texx + 5, texy + 64);
			}
			
			texx += 128 + 5;
			texi++;
		}
		
		PictureView.createPictureView(teximg);
	}
	
	public static class MapListener implements MapExitListener
	{
		private Configuration configuration;
		private ResManager mgr;
		private byte [] palette;
		private int currentMapNum;
		private Environment currentEnvironment;
		private TextureProperties textureProperties;
		
		public MapListener(Configuration configuration, ResManager mgr, byte [] palette, int currentMapNum) {
			this.configuration = configuration;
			this.mgr = mgr;
			this.palette = palette;
			this.currentMapNum = currentMapNum;
			this.currentEnvironment = null;
			
			File tpf = mgr.findFileInSearchPath(SSLogic.texturePropertiesFile, false);
			if(tpf != null) {
				try {
					RandomAccessFile raf = new RandomAccessFile(tpf, "r");
					MappedByteBuffer mbb = raf.getChannel().map(MapMode.READ_ONLY, 0, raf.length());
					textureProperties = new TextureProperties(mbb);
				} catch (FileNotFoundException e) {
					System.err.println("Unable to load texture properties...");
				} catch (IOException e) {
					System.err.println("Unable to load texture properties...");
				}
			} else {
				System.err.println("Cannot find file with texture properties...");
			}
		}

		@Override
		public boolean onMapExit(MapExitEvent mee, int param) {
			int newmap = -1;
			switch(mee) {
			case Exit:
				if(param < 0) return true;
				newmap = selectMap(currentMapNum);
				if (newmap < 0) {
					return true;
				}
				break;
			case Next:
				newmap = currentMapNum+1;
				break;
			case Previous:
				newmap = currentMapNum-1;
				break;
			case Goto:
				newmap = param;
				break;
			}
			
			if(newmap >= 0 && newmap < SSLogic.numberOfMaps) {
				currentMapNum = newmap;
				SSMap map = SSMap.getMap(mgr, currentMapNum);
				System.out.println(String.format("Loading Map %d: %s", newmap, SSLogic.getLevelName(newmap)));
				Map3D m3d = Map3D.createMap3D(configuration, mgr, map, palette, textureProperties);
				m3d.addMapExitListener(this);
				if(currentEnvironment!=null) {
					Environment newEnv = m3d.getEnvironment();
					newEnv.cam_pos = currentEnvironment.cam_pos;
					newEnv.cam_rot = currentEnvironment.cam_rot;
					newEnv.cam_view = currentEnvironment.cam_view;
				}
				currentEnvironment = m3d.getEnvironment();
				return true;
			} 
			else
			{
				System.out.println("No such map: " + newmap);
				return false;
			}
		}
	}

	public static boolean hasText(String s) {
		return s != null && !s.isBlank();
	}

	public static int selectMap(int currentMap) {
		if(currentMap < 0 || currentMap >= SSLogic.levelNames.length) currentMap = 1;
		Object result =
			JOptionPane.showInputDialog(
				null,
				"Select map to render...",
				"Map Selection",
				JOptionPane.PLAIN_MESSAGE,
				null,
				SSLogic.levelNames,
				SSLogic.levelNames[currentMap]
			);
		if(result == null) {
			printf("No map selected...");
			return -1;
		}
		int index = Arrays.asList(SSLogic.levelNames).indexOf(result);
		if(index < 0) {
			printf("Invalid map...");
			return -1;
		}
		return index;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Configuration cfg = new Configuration();

		Path configPath = Path.of(cfg.getValueFor(Configuration.CONFIG_PATH).get());
		if(!configPath.toFile().isDirectory()) {
			System.out.println("Trying to create: " + configPath);
			if (!configPath.toFile().mkdir()) {
				System.err.println("Could not create .syshmavi directory...");
			}
		}

		cfg.loadConfiguration();

		String basedir = cfg.getValueFor(Configuration.DATA_PATH).orElse(null);
		
		if(args.length>0)
			basedir = args[0];

		if(!hasText(basedir) && cfg.getValueFor(Configuration.Entry.booleanEntry("showDataDirChooser", true)).get()) {
			JFileChooser baseDirChooser = new JFileChooser();
			baseDirChooser.setDialogTitle("Select System Shock Base Directory...");
			baseDirChooser.setMultiSelectionEnabled(false);
			baseDirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			if(baseDirChooser.showDialog(null, "Select") == JFileChooser.APPROVE_OPTION) {
				basedir = baseDirChooser.getSelectedFile().getPath();
			}
		}

		if(basedir == null) {
			printf("No base directory set!");
			System.exit(0);
		}

		int mapnum = cfg.getValueFor(Configuration.MAP).get();

		if(args.length>1)
		{
			if(Configuration.MAP.isValid(args[1])) {
				mapnum = Configuration.MAP.getValue(args[1]);
			} else {
				printerrf("Invalid Map number: %s; Valid range is 0-%d", args[1], SSLogic.levelNames.length-1);
				System.exit(1);
			}
		}

		if(mapnum == -1) {
			mapnum = selectMap(-1);
			if(mapnum < 0) {
				printf("No map selected...");
				System.exit(0);
			}
		}

		File f = new File(basedir);
		
		if(!f.isDirectory()){
			System.err.println("Invalid base directory: " + f);
			System.exit(1);
		}
		
		File [] files = f.listFiles();
		
		if(files==null)
		{
			System.err.println("Error getting files from: " + f);
			System.exit(1);
		}
		
		File cdromdir = null;
		File hddir = null;
		
		for(File subdir : files) {
			if(!subdir.isDirectory())
				continue;
			
			if(subdir.getName().equalsIgnoreCase("cdrom"))
				cdromdir = subdir;
			if(subdir.getName().equalsIgnoreCase("hd"))
				hddir = subdir;
		}
		
		if(cdromdir == null || hddir == null)
		{
			printerrf("No CDROM and/or HD directory found...");
			System.exit(1);
		}
		
		File cdromdatadir = null;
		File hddatadir = null;
		
		files = cdromdir.listFiles();
		
		if(files!=null)
		{
			for(File subdir : files)
			{
				if(!subdir.isDirectory())
					continue;
				
				if(subdir.getName().equalsIgnoreCase("data"))
				{
					cdromdatadir = subdir;
					break;
				}
			}
		}
		
		files = hddir.listFiles();

		if(files!=null)
		{
			for(File subdir : files)
			{
				if(!subdir.isDirectory())
					continue;
				
				if(subdir.getName().equalsIgnoreCase("data"))
				{
					hddatadir = subdir;
					break;
				}
			}
		}
		
		if(cdromdatadir == null || hddatadir == null)
		{
			printerrf("No directory for CDROM- and/or HD-data found...");
			System.exit(1);
		}
		
		ResManager rm = new ResManager();

		rm.addSearchPath(hddatadir);
		rm.addSearchPath(cdromdatadir);
		
		if(!rm.addResFileFromSearchPath(SSLogic.mapArchiveFile, false))
		{
			printerrf("Unable to locate map archive (%s)", SSLogic.mapArchiveFile);
			System.exit(1);
		}

		if(!rm.addResFileFromSearchPath(SSLogic.textureFile, false))
		{
			printerrf("Unable to locate texture ressources (%s)", SSLogic.textureFile);
			System.exit(1);
		}

		if(!rm.addResFileFromSearchPath(SSLogic.paletteFile, false))
		{
			printerrf("Unable to locate game-palette (%s)", SSLogic.paletteFile);
			System.exit(1);
		}

		if(!rm.addResFileFromSearchPath(SSLogic.objectBitmapFile, false))
		{
			printerrf("Unable to locate object art (1) (%s)", SSLogic.objectBitmapFile);
			System.exit(1);
		}

		if(!rm.addResFileFromSearchPath(SSLogic.object2BitmapFile, false))
		{
			printerrf("Unable to locate object art (2) (%s) - expect to miss some things...", SSLogic.object2BitmapFile);
		}

		if(!rm.addResFileFromSearchPath(SSLogic.object3BitmapFile, false))
		{
			printerrf("Unable to locate object art (3) (%s) - expect to miss some things...", SSLogic.object3BitmapFile);
		}

		if(!rm.addResFileFromSearchPath(SSLogic.model3DFile, false))
		{
			printerrf("Unable to locate 3d models (%s) - expect to miss some things...", SSLogic.model3DFile);
		}

		if(!rm.addResFileFromSearchPath(SSLogic.model3DTextureFile, false))
		{
			printerrf("Unable to locate 3d model textures (%s) - expect to miss some things...", SSLogic.model3DTextureFile);
		}

		if(!rm.addResFileFromSearchPath(SSLogic.wordsFile, false))
		{
			printerrf("Unable to locate words file (%s) - expect to miss some things...", SSLogic.wordsFile);
		}
		if(!rm.addResFileFromSearchPath(SSLogic.screenFile, false))
		{
			printerrf("Unable to locate screen file (%s) - expect to miss some things...", SSLogic.screenFile);
		}
		
		SSObject.prepareCommonObjectProperties(rm);
		
		/*
		printf("Loading level %d: \"%s\"", mapnum, SSLogic.getLevelName(mapnum));
		
		SSMap map = SSMap.getMap(rm, mapnum);
		
		System.out.println
		(
			 String.format
			 (
					 "Info H:%d HSh:%d HSc:%d"
					 , map.getLevelHeight()
					 , map.getHeightShift()
					 , map.getHeightScale()
			 )
		);
		
		if(map==null) {
			printerrf("Error loading map...");
			System.exit(1);
		}
		*/
		
		byte [] palette = null;
		palette = rm.getData(700);
		
		if(palette.length!=768)
			palette = null;
		
		//showTextures(rm, palette, map);
		//showMap(rm, palette, map);
		//Map3D.createMap3D(rm, map, palette);
		MapListener ml = new MapListener(cfg, rm, palette, mapnum);
		ml.onMapExit(MapExitListener.MapExitEvent.Goto, mapnum);
	}

}
