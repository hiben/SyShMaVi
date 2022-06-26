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
/*
 * Created on 19.09.2008
 */
package de.zvxeb.jres.ui;

import java.awt.BorderLayout;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import de.zvxeb.jres.Font;
import de.zvxeb.jres.InvalidVocFileException;
import de.zvxeb.jres.MapTile;
import de.zvxeb.jres.ResBitmap;
import de.zvxeb.jres.ResFile;
import de.zvxeb.jres.ResFileException;
import de.zvxeb.jres.ResManager;
import de.zvxeb.jres.ResMovie;
import de.zvxeb.jres.SSLogic;
import de.zvxeb.jres.SSMap;
import de.zvxeb.jres.SSModel;
import de.zvxeb.jres.SSTexture;
import de.zvxeb.jres.TextureProperties;
import de.zvxeb.jres.VocFile;
import de.zvxeb.jres.util.AudioPlayback;
import de.zvxeb.jres.util.GIMPPaletteFilter;
import de.zvxeb.jres.util.JavaArrayPaletteFilter;
import de.zvxeb.jres.util.PNGImageFilter;
import de.zvxeb.jres.util.Util;

public class JResBrowser {

	private ResManager mgr;
	
	private TextureProperties textureProperties = null;
	
	private JTree resTree;
	private DefaultMutableTreeNode root;
	private DefaultTreeModel model;
	private JButton cinema;
	
	private JFrame mainFrame;

	private ResBitmap lastBitmap;
	
	private BufferedImage bitmapImage;
	private BufferedImage paletteImage;
	private BufferedImage fontImage;
	
	private VocFile lastSound;
	
	private Integer lastModelChunk = 0;
	
	byte [] current_palette = null;
	
	private ResMovie lastMovie;
	
	private File lastAddDir;
	
	private JTabbedPane infoTabs;
	private JPanel pChunk, pChunkInfo, pPalette, pBitmap, pFont;
	private ImageDisplay bitmapDisplay, paletteDisplay, fontDisplay;;
	
	private boolean movieMode;
	private int [] fbwh;
	private byte [] frameBuffer;
	
	private JLabel lInfoChunk, lInfoBitmap, lInfoPalette, lInfoFont;
	
	
	private ResFile.DirEntry lastDirEntry;
	private ResFile.SubChunk lastSubChunk;
	
	private ResFile.DirEntry lastPaletteDirEntry;
	
	private SSMap lastSSMap;
	
	private JLabel lChunkId;
	private JLabel lChunkType;
	private JLabel lContentType;
	private JLabel lDataInfo;
	private JLabel lSubChunkInfo;
	
	private JFileChooser save_chooser;
	
	// C O N S T R U C T O R
	public JResBrowser(String...files)
	{
		mgr = new ResManager();
		
		root = new DefaultMutableTreeNode("root");
		model = new DefaultTreeModel(root);

		resTree = new JTree(model);
		resTree.setRootVisible(false);
		
		if(files.length>0)
		{
			File [] ffiles = new File [files.length];

			for(int i=0; i<files.length; i++)
				ffiles[i] = new File(files[i]);

			addFiles(ffiles);
		}
		
		// Create Layout
		
		mainFrame = new JFrame("JRes");

		mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		mainFrame.setLayout(new BorderLayout());
		
		// Tree / Table Tabs
		
		JTabbedPane tp = new JTabbedPane();
		
		JScrollPane sp = new JScrollPane(resTree);
		
		tp.insertTab("Tree-View", null, sp, "Tree!", 0);
		
		JPanel testp = new JPanel();
		
		testp.add(new JLabel("Test"));
		
		JScrollPane tsp = new JScrollPane(testp);
		
		tp.insertTab("Table-View", null, tsp, "Table!", 1);

		//f.getContentPane().add(tp, BorderLayout.CENTER);
		
		// B U T T O N S
		
		JPanel buttonPanel = new JPanel();
		
		buttonPanel.setLayout(new FlowLayout());
		
		JButton load = new JButton();
		
		load.setAction
		(
			new AbstractAction("Load")
			{
				private JFileChooser chooser;
				
				private static final long serialVersionUID = 1L;
				
				public void actionPerformed(ActionEvent ae)
				{
					if(chooser==null)
					{
						chooser = new JFileChooser(lastAddDir);
						chooser.setMultiSelectionEnabled(true);
					}
					
					int res = chooser.showDialog(mainFrame, "Load");
					
					if(res==JFileChooser.APPROVE_OPTION)
					{
						addFiles(chooser.getSelectedFiles());
					}
				}
			}
		);
		
		buttonPanel.add(load);
		
		cinema = new JButton();
		
		cinema.setAction
		(
				new AbstractAction("Cinema")
				{
					private static final long serialVersionUID = 1L;
					
					public void actionPerformed(ActionEvent ae)
					{
						showCinema();
					}
				}
		);
		
		buttonPanel.add(cinema);

		JButton playsound = new JButton();
		
		playsound.setAction
		(
				new AbstractAction("Play sound")
				{
					private static final long serialVersionUID = 1L;
					
					public void actionPerformed(ActionEvent ae)
					{
						playSound();
					}
				}
		);
		
		buttonPanel.add(playsound);

		JButton savemapimage = new JButton();
		
		savemapimage.setAction
		(
				new AbstractAction("Save Map Image")
				{
					private static final long serialVersionUID = 1L;
					
					public void actionPerformed(ActionEvent ae)
					{
						saveMapImage();
					}
				}
		);
		
		buttonPanel.add(savemapimage);
		
		JButton modelInfo = new JButton();
		
		modelInfo.setAction
		(
			new AbstractAction("Model Info")
			{
				private static final long serialVersionUID = 1L;

				public void actionPerformed(ActionEvent ae)
				{
					showModelInfo();
				};
			}
		);
		
		buttonPanel.add(modelInfo);

		JButton exit = new JButton();
		
		exit.setAction
		(
			new AbstractAction("Exit")
			{
				private static final long serialVersionUID = 1L;

				public void actionPerformed(ActionEvent ae)
				{
					System.exit(0);
				};
			}
		);
		
		buttonPanel.add(exit);
		
		mainFrame.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
		
		// I N F O
		
		infoTabs = new JTabbedPane();
		
		pChunk = new JPanel();
		pChunk.setLayout(new BorderLayout());

		lInfoChunk = new JLabel();
		pChunkInfo = new JPanel();
		pChunkInfo.setLayout(new BoxLayout(pChunkInfo, BoxLayout.PAGE_AXIS));
		
		lChunkId = new JLabel();
		lChunkType = new JLabel();
		lContentType = new JLabel();
		lDataInfo = new JLabel();
		lSubChunkInfo = new JLabel();
		
		pChunk.add(lInfoChunk, BorderLayout.NORTH);
		
		pChunkInfo.add(lChunkId);
		pChunkInfo.add(lChunkType);
		pChunkInfo.add(lContentType);
		pChunkInfo.add(lDataInfo);
		pChunkInfo.add(lSubChunkInfo);
		
		JPanel tmpp = new JPanel();
		tmpp.setLayout(new FlowLayout(FlowLayout.LEADING));
		
		tmpp.add(pChunkInfo);
		tmpp.add(Box.createGlue());
		
		pChunk.add(tmpp, BorderLayout.CENTER);
		
		JPanel chunkButtonPanel = new JPanel();
		chunkButtonPanel.setLayout(new FlowLayout());
		
		JButton b = new JButton();
		b.setAction
		(
			new AbstractAction("Dump")
			{
				private static final long serialVersionUID = 1L;
				
				public void actionPerformed(ActionEvent ae)
				{
					dumpChunk(false);
				}
			}
		);
		
		chunkButtonPanel.add(b);

		b = new JButton();
		b.setAction
		(
			new AbstractAction("Dump Packed")
			{
				private static final long serialVersionUID = 1L;
				
				public void actionPerformed(ActionEvent ae)
				{
					dumpChunk(true);
				}
			}
		);

		chunkButtonPanel.add(b);

		b = new JButton();
		b.setAction
		(
			new AbstractAction("Dump SubChunk")
			{
				private static final long serialVersionUID = 1L;
				
				public void actionPerformed(ActionEvent ae)
				{
					dumpSubChunk();
				}
			}
		);

		chunkButtonPanel.add(b);
		
		pChunk.add(chunkButtonPanel, BorderLayout.SOUTH);
		
		pPalette = new JPanel();
		pPalette.setLayout(new BorderLayout());
		
		lInfoPalette = new JLabel("Info: ");

		pPalette.add(lInfoPalette, BorderLayout.NORTH);
		
		paletteDisplay = new ImageDisplay(null, 1);
		
		pPalette.add(new JScrollPane(paletteDisplay), BorderLayout.CENTER);
		
		JPanel paletteButtonPanel = new JPanel();
		paletteButtonPanel.setLayout(new FlowLayout());
		
		b = new JButton();
		b.setAction
		(
			new AbstractAction("Create Palette (GIMP or Java-Array)")
			{
				private static final long serialVersionUID = 1L;
				
				public void actionPerformed(ActionEvent ae)
				{
					savePalette();
				}
			}
		);
		
		paletteButtonPanel.add(b);
		
		pPalette.add(paletteButtonPanel, BorderLayout.SOUTH);

		//
		pBitmap = new JPanel();
		pBitmap.setLayout(new BorderLayout());
		
		lInfoBitmap = new JLabel("Info: ");
		
		pBitmap.add(lInfoBitmap, BorderLayout.NORTH);
		
		bitmapDisplay = new ImageDisplay(null, 2);
		
		pBitmap.add(new JScrollPane(bitmapDisplay), BorderLayout.CENTER);
		
		
		JPanel bitmapButtonPanel = new JPanel();
		bitmapButtonPanel.setLayout(new FlowLayout());
		
		b = new JButton();
		b.setAction
		(
			new AbstractAction("Save Bitmap")
			{
				private static final long serialVersionUID = 1L;
				
				public void actionPerformed(ActionEvent ae)
				{
					saveBitmap();
				}
			}
		);
		
		bitmapButtonPanel.add(b);
		
		b = new JButton();
		b.setAction
		(
			new AbstractAction("+")
			{
				private static final long serialVersionUID = 1L;
				
				public void actionPerformed(ActionEvent ae)
				{
					bitmapDisplay.setScale(bitmapDisplay.getScale()+1);
				}
			}
		);
		bitmapButtonPanel.add(b);

		b = new JButton();
		b.setAction
		(
			new AbstractAction("-")
			{
				private static final long serialVersionUID = 1L;
				
				public void actionPerformed(ActionEvent ae)
				{
					bitmapDisplay.setScale(bitmapDisplay.getScale()-1);
				}
			}
		);
		bitmapButtonPanel.add(b);
		
		JCheckBox cb = new JCheckBox();
		cb.setAction
		(
			new AbstractAction("Movie Mode")
			{
				private static final long serialVersionUID = 1L;
				
				public void actionPerformed(ActionEvent ae)
				{
					JCheckBox scb =(JCheckBox)ae.getSource();
					movieMode = scb.isSelected();
					
					if(!movieMode)
					{
						frameBuffer = null;
					}
						
				}
			}
		);

		bitmapButtonPanel.add(cb);
		
		pBitmap.add(bitmapButtonPanel, BorderLayout.SOUTH);
		
		//
		pFont = new JPanel();
		pFont.setLayout(new BorderLayout());
		
		lInfoFont = new JLabel("Info: ");
		
		pFont.add(lInfoFont, BorderLayout.NORTH);

		fontDisplay = new ImageDisplay(null, 1);
		
		pFont.add(new JScrollPane(fontDisplay), BorderLayout.CENTER);
		
		JPanel fontButtonPanel = new JPanel();
		fontButtonPanel.setLayout(new FlowLayout());
		
		b = new JButton();
		b.setAction
		(
			new AbstractAction("+")
			{
				private static final long serialVersionUID = 1L;
				
				public void actionPerformed(ActionEvent ae)
				{
					fontDisplay.setScale(fontDisplay.getScale()+1);
				}
			}
		);
		fontButtonPanel.add(b);
		b = new JButton();
		b.setAction
		(
			new AbstractAction("-")
			{
				private static final long serialVersionUID = 1L;
				
				public void actionPerformed(ActionEvent ae)
				{
					fontDisplay.setScale(fontDisplay.getScale()-1);
				}
			}
		);
		fontButtonPanel.add(b);
		
		pFont.add(fontButtonPanel, BorderLayout.SOUTH);
		
		infoTabs.add("Chunk", pChunk);
		infoTabs.add("Palette", pPalette);
		infoTabs.add("Bitmap", pBitmap);
		infoTabs.add("Font", pFont);

		/*
		lBitmap.addMouseListener
		(
				new MouseListener()
				{

					public void mouseClicked(MouseEvent me) {
						if(me.getButton()!=MouseEvent.BUTTON1)
							return;
						
						Point p = me.getPoint();
						
						if(bitmapImage!=null)
						{
							byte [] color = new byte [1];
							bitmapImage.getRaster().getDataElements(p.x, p.y, color);
							int icolor = bitmapImage.getRGB(p.x, p.y);
							
							System.out.println(String.format("PaletteIndex %d, Color: 0x%06x", ((int)color[0])&0xFF, icolor));
						}
					}

					public void mouseEntered(MouseEvent arg0) {
						// TODO Auto-generated method stub
						
					}

					public void mouseExited(MouseEvent arg0) {
						// TODO Auto-generated method stub
						
					}

					public void mousePressed(MouseEvent arg0) {
						// TODO Auto-generated method stub
						
					}

					public void mouseReleased(MouseEvent arg0) {
						// TODO Auto-generated method stub
						
					}
					
				}
		);
		*/
		
		bitmapDisplay.addMouseListener
		(
				new MouseListener()
				{

					public void mouseClicked(MouseEvent me) {
						if(me.getButton()!=MouseEvent.BUTTON1)
							return;
						
						Point p = me.getPoint();
						p = bitmapDisplay.getPointInImage(p);
						
						if(bitmapImage!=null)
						{
							byte [] color = new byte [1];
							bitmapImage.getRaster().getDataElements(p.x, p.y, color);
							int icolor = bitmapImage.getRGB(p.x, p.y);
							
							System.out.println(String.format("PaletteIndex %d, Color: 0x%06x", ((int)color[0])&0xFF, icolor));
						}
					}

					public void mouseEntered(MouseEvent arg0) {
						// TODO Auto-generated method stub
						
					}

					public void mouseExited(MouseEvent arg0) {
						// TODO Auto-generated method stub
						
					}

					public void mousePressed(MouseEvent arg0) {
						// TODO Auto-generated method stub
						
					}

					public void mouseReleased(MouseEvent arg0) {
						// TODO Auto-generated method stub
						
					}
					
				}
		);
		
		JSplitPane splitpane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, false, tp, infoTabs);
		mainFrame.getContentPane().add(splitpane, BorderLayout.CENTER);
		
		resTree.addTreeSelectionListener
		(
				new TreeSelectionListener()
				{
					public void valueChanged(TreeSelectionEvent e) {
						TreePath tp = e.getPath();
						Object o = tp.getLastPathComponent();
						if(o instanceof DefaultMutableTreeNode)
						{
							DefaultMutableTreeNode tn = (DefaultMutableTreeNode)o;
							
							Object uo = tn.getUserObject();
							
							if(uo instanceof ResBitmap)
								setBitmap((ResBitmap)uo);
							
							if(uo instanceof ResFile.DirEntry)
							{
								ResFile.DirEntry de = (ResFile.DirEntry)uo;
								lastDirEntry = de;
								
								if(de.getContentType()==ResFile.CCT_PALETTE)
									setPalette(de);
								if(de.getContentType()==ResFile.CCT_FONT)
									showFont(de);
								if(de.getContentType()==ResFile.CCT_AUDIO_CUTSCENE)
									showMovieInfo(de);
								if(de.getContentType()==ResFile.CCT_SOUND)
									showSoundInfo(de);
								if(de.getContentType()==ResFile.CCT_MAP)
								{
									int lnum = SSLogic.levelNumberFromChunkId(de.getChunkId());
									SSLogic.MapChunkType mct = SSLogic.getMapChunkType(de.getChunkId());
									
									SSMap map = null;
									try
									{
										map = SSMap.getMap(mgr, lnum);
										
										lastSSMap = map;
									}
									catch(InvalidParameterException ipe)
									{
										System.out.println("Can't create map: " + ipe.getMessage());
									}
									
									if(map!=null)
									{
										System.out.println("Chunk belongs to Map #" + map.getNumber() + ": " + map.getName());
									}

									if(mct==SSLogic.MapChunkType.ArchiveName)
									{
										System.out.println("Archive Name");
									} else
									if(mct==SSLogic.MapChunkType.PlayerInformation)
									{
										System.out.println("Player Information");
									} else									
									if(mct==SSLogic.MapChunkType.TileMap)
									{
										System.out.println("Tile Map");
										if(map!=null)
											map.printMap();
									}else
									if(mct==SSLogic.MapChunkType.TextureList)
									{
										System.out.println("Texture List");
										if(map!=null)
											map.printUsedTextures();
									}else
									if(mct==SSLogic.MapChunkType.MasterObjectTable)
									{
										System.out.println("Master Object Table");
										if(map!=null)
											map.printEntries();
									}else
									{
										System.out.println("Unprocessed Chunk... " + mct);
									}
								}
								
								updateChunkInfo();
							}
							
							if(uo instanceof ResFile.SubChunk)
							{
								ResFile.SubChunk sc = (ResFile.SubChunk)uo;
								lastSubChunk = sc;
								
								if(sc.getContentType()==ResFile.CCT_TEXT)
								{
									showText(sc);
								}
								
								if(sc.getContentType()==ResFile.CCT_3DMODEL) {
									lastModelChunk = Integer.valueOf(sc.getSubDirectory().getEntry().getChunkId());
								}
								
								updateChunkInfo();
							}
						}
					}
					
					
				}
		);
		
		resTree.addTreeWillExpandListener
		(
			new TreeWillExpandListener()
			{

				public void treeWillCollapse(TreeExpansionEvent arg0) throws ExpandVetoException {
				}

				public void treeWillExpand(TreeExpansionEvent tee) throws ExpandVetoException {
					TreePath tp = tee.getPath();
					Object eo = tp.getLastPathComponent();
					if(eo instanceof DefaultMutableTreeNode)
					{
						DefaultMutableTreeNode tn = (DefaultMutableTreeNode)eo;

						Object uo = tn.getUserObject();
							
						if(uo instanceof ResFile.DirEntry)
						{
							ResFile.DirEntry cd = (ResFile.DirEntry)uo;

							if(cd.getContentType()!=ResFile.CCT_BITMAP)
								return;
							
							for(int ci = 0; ci < tn.getChildCount(); ci++)
							{
								TreeNode ct = tn.getChildAt(ci);
								
								if(!(ct instanceof DefaultMutableTreeNode))
									continue;

								DefaultMutableTreeNode cmt = (DefaultMutableTreeNode)ct;

								uo = cmt.getUserObject();

								if(!(uo instanceof ResFile.SubChunk))
									continue;
								
								ResFile.SubChunk su = (ResFile.SubChunk)uo;
								
								// check if we created the bitmaps before...
								if(cmt.getChildCount()!=0)
									continue;

								byte [] bitmapdata = su.getData();
								if(bitmapdata!=null)
								{
									ResBitmap rb = new ResBitmap(bitmapdata, cd.getChunkId(), su.getNumber());
									DefaultMutableTreeNode btn = new DefaultMutableTreeNode(rb);
									model.insertNodeInto(btn, cmt, 0);
								}
							}
						}
					}
				}

			}
		);
		
		mainFrame.setSize(800, 600);
		
		Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
		
		mainFrame.setLocation((size.width - 800) / 2, (size.height - 600) / 2);
		
		byte [] defaultPalette = mgr.getData(SSLogic.defaultPaletteChunkId);
		if(defaultPalette!=null) {
			current_palette = defaultPalette;
			System.out.println("Did set SystemShock Palette!");
		}
		
		mainFrame.setVisible(true);
	}

	private void makeResTree(ResFile rf, DefaultMutableTreeNode root)
	{
		for(ResFile.DirEntry cd : rf.getEntries())
		{
			DefaultMutableTreeNode tn = new DefaultMutableTreeNode(cd);
			
			switch(cd.getChunkType())
			{
			case ResFile.CT_SUBDIR:
			case ResFile.CT_SUBDIR_COMPRESSED:
				ResFile.SubDirectory csd = rf.getSubDirectory(cd);
				for(short i = 0; i<csd.getNumberOfSubChunks(); i++)
				{
					DefaultMutableTreeNode stn = new DefaultMutableTreeNode(csd.getSubChunk(i));
					tn.add(stn);
				}
				break;
			}
			root.add(tn);
		}
	}
	
	public void ensure_saver_exists()
	{
		if(save_chooser==null)
		{
			save_chooser = new JFileChooser();
			save_chooser.setMultiSelectionEnabled(false);
		}
	}
	
	public void prepare_saver_dump(short cid, int sub, boolean noUnpack)
	{
		ensure_saver_exists();
		save_chooser.resetChoosableFileFilters();
		String fileName = String.format("dump%04X%s.raw%s", cid, ((sub>0)?String.format(".%03d", sub):""), noUnpack?"_packed":"");
		File dmpFile = new File(fileName);
		save_chooser.setSelectedFile(dmpFile);
	}
	
	public void prepare_saver_palette(short cid, int sub)
	{
		ensure_saver_exists();
		save_chooser.resetChoosableFileFilters();
		GIMPPaletteFilter gpl = new GIMPPaletteFilter();
		save_chooser.addChoosableFileFilter(gpl);
		save_chooser.addChoosableFileFilter(new JavaArrayPaletteFilter());
		save_chooser.setFileFilter(gpl);
		String fileName = String.format("palette%04X%s.gpl", cid, ((sub>0)?String.format(".%03d", sub):""));
		File dmpFile = new File(fileName);
		save_chooser.setSelectedFile(dmpFile);
	}

	public void prepare_saver_png(short cid, int sub)
	{
		ensure_saver_exists();
		save_chooser.resetChoosableFileFilters();
		save_chooser.addChoosableFileFilter(new PNGImageFilter());
		String fileName = String.format("bitmap%04X%s.png", cid, ((sub>0)?String.format(".%03d", sub):""));
		File dmpFile = new File(fileName);
		save_chooser.setSelectedFile(dmpFile);
	}

	public void prepare_mapsaver_png(int mapnum)
	{
		ensure_saver_exists();
		save_chooser.resetChoosableFileFilters();
		save_chooser.addChoosableFileFilter(new PNGImageFilter());
		String fileName = String.format("map%02X.png", mapnum);
		File dmpFile = new File(fileName);
		save_chooser.setSelectedFile(dmpFile);
	}
	
	public boolean askOverwrite(String fname)
	{
		return JOptionPane.showConfirmDialog(mainFrame, "File \"" + fname + "\" exist!\nOverwrite ?", "Confirm Overwrite", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE)==JOptionPane.YES_OPTION;
	}
	
	public void errorMessage(String errMsg)
	{
		JOptionPane.showMessageDialog(mainFrame, errMsg, "Error", JOptionPane.ERROR_MESSAGE);
	}
	
	public void dumpChunk(boolean noUnpack)
	{
		if(lastDirEntry==null)
			return;
		
		prepare_saver_dump(lastDirEntry.getChunkId(), -1, noUnpack);
		
		if(save_chooser.showSaveDialog(mainFrame)==JFileChooser.APPROVE_OPTION)
		{
			File df = save_chooser.getSelectedFile();
			
			if(df.exists())
				if(!askOverwrite(df.getName()))
					return;
			
			try {
				FileOutputStream fos = new FileOutputStream(df);
				
				byte [] data = noUnpack?lastDirEntry.getFileData():lastDirEntry.getData();
				
				fos.write(data);
				
			} catch (FileNotFoundException e) {
				errorMessage("Unable to find file. System said: " + e.getMessage());
			} catch (IOException e) {
				errorMessage("Error writing to file. System said: " + e.getMessage());
			}
		}
	}

	public void dumpSubChunk()
	{
		if(lastSubChunk==null)
			return;
		
		prepare_saver_dump(lastSubChunk.getSubDirectory().getEntry().getChunkId(), lastSubChunk.getNumber()+1, false);
		
		if(save_chooser.showSaveDialog(mainFrame)==JFileChooser.APPROVE_OPTION)
		{
			File df = save_chooser.getSelectedFile();
			
			if(df.exists())
				if(!askOverwrite(df.getName()))
					return;
			
			try {
				FileOutputStream fos = new FileOutputStream(df);
				
				byte [] data = lastSubChunk.getData();
				
				fos.write(data);
				
			} catch (FileNotFoundException e) {
				errorMessage("Unable to find file. System said: " + e.getMessage());
			} catch (IOException e) {
				errorMessage("Error writing to file. System said: " + e.getMessage());
			}
		}
	}
	
	public void savePalette()
	{
		if(current_palette==null)
			return;
		
		prepare_saver_palette(lastPaletteDirEntry.getChunkId(), -1);
		
		if(save_chooser.showSaveDialog(mainFrame)==JFileChooser.APPROVE_OPTION)
		{
			File df = save_chooser.getSelectedFile();
			boolean gimp = save_chooser.getFileFilter() == null || save_chooser.getFileFilter() instanceof GIMPPaletteFilter;
			
			if(df.exists())
				if(!askOverwrite(df.getName()))
					return;
			
			try {
				FileOutputStream fos = new FileOutputStream(df);

				if(gimp) {
					Util.writeGIMPPalette(fos, "Chunk#"+lastPaletteDirEntry.getChunkId(), current_palette);
				} else {
					Util.writeJavaPaletteArray(fos, "chunk_" + lastPaletteDirEntry.getChunkId(), current_palette);
				}
				
			} catch (FileNotFoundException e) {
				errorMessage("Unable to find file. System said: " + e.getMessage());
			} catch (IOException e) {
				errorMessage("Error writing to file. System said: " + e.getMessage());
			}
		}
	}
	
	public void saveBitmap()
	{
		if(lastBitmap==null)
			return;
		
		prepare_saver_png(lastBitmap.getChunkId(), lastBitmap.getSubChunkNumber()+1);
		
		if(save_chooser.showSaveDialog(mainFrame)==JFileChooser.APPROVE_OPTION)
		{
			File df = save_chooser.getSelectedFile();
			
			if(df.exists())
				if(!askOverwrite(df.getName()))
					return;
			
			try {
				
				String format = Util.getFileExt(df.getName());
				
				if(format==null)
					format = "png";
				
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				
				if(!ImageIO.write(lastBitmap.getImage(), format, baos))
				{
					errorMessage("Sorry, cannot determine image format for \"" + df.getName() + "\"...");
				}
				else
				{
					FileOutputStream fos = new FileOutputStream(df);
					baos.writeTo(fos);
				}
			} catch (FileNotFoundException e) {
				errorMessage("Unable to find file. System said: " + e.getMessage());
			} catch (IOException e) {
				errorMessage("Error writing to file. System said: " + e.getMessage());
			}
		}
		
	}
	
	public void updateChunkInfo()
	{
		if(lastDirEntry==null && lastSubChunk==null)
		{
			lInfoChunk.setText("No Chunk-Info available...");
			lChunkId.setText("Id: ?");
			lChunkType.setText("Chunk-Type: ?");
			lContentType.setText("Content-Type: ?");
			lDataInfo.setText("Data-Info: ?");
			lSubChunkInfo.setText("");
			return;
		}
		
		// get entry from subchunk
		if(lastDirEntry==null)
		{
			ResFile.SubDirectory sd = lastSubChunk.getSubDirectory();
			lastDirEntry = sd.getEntry();
		}
		
		// check if last subChunk belongs to current entry
		if(lastSubChunk!=null)
		{
			ResFile.SubDirectory sd = lastSubChunk.getSubDirectory();
			ResFile.DirEntry tmpe = sd.getEntry();
			if(tmpe!=lastDirEntry)
				lastSubChunk = null;
		}
		
		String infoText = "Chunk #" + lastDirEntry.getChunkId();
		
		if(lastSubChunk!=null)
			infoText += ", SubChunk " + (lastSubChunk.getNumber() + 1) + " of " + lastSubChunk.getSubDirectory().getNumberOfSubChunks();
		
		lInfoChunk.setText(infoText);
		lChunkId.setText(String.format("Id: #%1$d (0x%1$X)", lastDirEntry.getChunkId()));
		lChunkType.setText("Chunk-Type: " + lastDirEntry.getChunkTypeString());
		lContentType.setText("Content-Type: " + lastDirEntry.getContentTypeString());
		lDataInfo.setText(String.format("Data-Info: Offset=%1$d (0x%1$X), Length=%2$d (0x%2$X), Packed-Length=%3$d (0x%3$X)", lastDirEntry.getFileOffset(), lastDirEntry.getLength(), lastDirEntry.getPackedLength()));
		if(lastSubChunk!=null)
			lSubChunkInfo.setText(String.format("Sub-Chunk-Info: %1$d/%2$d, Offset=%3$d (0x%3$X), Length=%4$d (0x%4$X)", lastSubChunk.getNumber()+1, lastSubChunk.getSubDirectory().getNumberOfSubChunks(), lastSubChunk.getOffset(), lastSubChunk.getLength()));
		else
			lSubChunkInfo.setText("");
	}
	
	public void setPalette(ResFile.DirEntry cd)
	{
		ResFile rf = cd.getResFile();
		
		byte [] tmp = rf.getChunkData(cd);
		
		if(tmp!=null && tmp.length==768)
		{
			current_palette = tmp;
			paletteImage = makePaletteImage();
			paletteDisplay.setImage(paletteImage);
			
			lInfoPalette.setText("Palette from Chunk #" + cd.getChunkId());
			lastPaletteDirEntry = cd;
		}
		
		if(lastBitmap!=null)
			setBitmap(lastBitmap);
	}
	
	public void showFont(ResFile.DirEntry cd)
	{
		ResFile rf = cd.getResFile();
		
		ByteBuffer bb = rf.getChunkDataBuffer(cd);
		
		Font f = new Font(bb);
		
		fontImage = f.createFontView(0x000000, 0x222222, 0x111111, 0x333333, 0x00FF00, 0x00AA00, 0xFF0000, current_palette);
		
		if(fontImage!=null)
		{
			fontDisplay.setImage(fontImage);
			
			lInfoFont.setText("Font from Chunk #" + cd.getChunkId());
		}
	}
	
	public void showCinema()
	{
		if(lastMovie==null)
			return;
		
		MovieView mv = new MovieView(lastMovie);
		
		mv.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		mv.pack();
		mv.setVisible(true);
		
		Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
		
		mv.setLocation((size.width - mv.getWidth()) / 2, (size.height - mv.getHeight()) / 2);
	}
	
	public void playSound()
	{
		if(lastSound==null)
			return;

		Thread t = new Thread()
		{
			public void run()
			{
				List<VocFile.SoundDataI> sdil = lastSound.getSounds();
				int i=1;
				for(VocFile.SoundDataI sdi : sdil)
				{
					System.out.println("Playing sound " + i + " of " + sdil.size());
					i++;
					
					AudioFormat af = sdi.getAudioFormat();
					
					if(af==null)
					{
						System.out.println("Can't playback sound... unsupported format...");
						continue;
					}
					
					try {
						
						byte [] audio_data = sdi.getData();

						AudioPlayback playback = new AudioPlayback(af, audio_data);

						playback.start();
						
						while(!playback.isPlaybackFinished())
						{
							try {
								Thread.sleep(100);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
					} catch (LineUnavailableException e) {
						e.printStackTrace();
					}
				}
			}
		};
		
		t.start();
	}
	
	public void showMovieInfo(ResFile.DirEntry cd)
	{
		ResFile rf = cd.getResFile();
		
		ResMovie rm = new ResMovie(rf.getChunkFileDataBuffer(cd));
		
		lastMovie = rm;
		
		BufferedImage bi = rm.getFirstPicture();
		if(bi!=null)
			setMovieBitmap(bi);
	}
	
	public void showText(ResFile.SubChunk sc)
	{
		byte [] data = sc.getData();
		
		String s = Font.decodeSSString(data, 0, data.length, false);
		
		System.out.println("Text: " + s);
		
		for(int i=0; i<data.length; i++)
			System.out.print(String.format("%02X ", ((int)data[i])&0xFF));
		System.out.println();
	}
	
	public void showSoundInfo(ResFile.DirEntry cd)
	{
		byte [] data = cd.getResFile().getChunkData(cd);
		try
		{
			VocFile vf = new VocFile(ByteBuffer.wrap(data));
			
			lastSound = vf;
			
			System.out.println(vf);
			
			System.out.println(String.format("There are %d sound entries...", vf.getSounds().size()));
		}
		catch(InvalidVocFileException ivfe)
		{
			System.err.println(ivfe);
		}
	}
	
	public void setBitmap(ResBitmap rb)
	{
		if(movieMode)
		{
			if(frameBuffer==null)
			{
				if(lastBitmap!=null)
				{
					frameBuffer = lastBitmap.getBitmap();
					fbwh = new int [2];
					fbwh[0] = lastBitmap.getWidth();
					fbwh[1] = lastBitmap.getHeight();
				}
				else
				{
					frameBuffer = rb.getBitmap();
					fbwh = new int [2];
					fbwh[0] = rb.getWidth();
					fbwh[1] = rb.getHeight();
				}
			}
			else
			{
				frameBuffer = rb.getOverlay(frameBuffer, fbwh);
			}

			bitmapImage = rb.makeImage(frameBuffer, fbwh[0], fbwh[1]);
		}
		else
		{

			if(!rb.hasPrivatePalette())
				rb.setPalette(current_palette);

			bitmapImage = rb.getImage();
		}

		if(bitmapImage!=null)
		{
			bitmapDisplay.setImage(bitmapImage);

			lInfoBitmap.setText("Bitmap from Chunk #" + rb.getChunkId());
		}

		lastBitmap = rb;
		
		if(rb.getChunkId()>=1000 && rb.getChunkId()<=1272) {
			System.out.println("Texture...");
			int texNum = rb.getChunkId() - 1000;
			if(textureProperties == null) {
				System.out.println("...Trying to get properties file");
				File f = mgr.findFileInSearchPath(SSLogic.texturePropertiesFile, false);
				if(f!=null) {
					try {
						System.out.println("...Trying to parse properties");
						RandomAccessFile raf = new RandomAccessFile(f, "r");
						long len = raf.length();
						MappedByteBuffer mbb = raf.getChannel().map(MapMode.READ_ONLY, 0, len);
						textureProperties = new TextureProperties(mbb);
						
						System.out.println("There are " + textureProperties.getNumberOfProperties() + " properties...");
					} catch (FileNotFoundException e) {
						System.out.println("...No such file");
					} catch (IOException e) {
						System.out.println("...IO error");
					}
				} else {
					System.out.println("File not found...");
				}
			}
			if(textureProperties!=null) {
				TextureProperties.TextureProperty tp = textureProperties.getPropertyFor(texNum);
				if(tp!=null) {
					System.out.println(tp.toString() + " - " + tp.rawString());
				} else {
					System.out.println("No properties found...");
				}
			}
		}
	}
	
	public void setMovieBitmap(BufferedImage bi)
	{
		bitmapDisplay.setImage(bi);
	}
	
	public BufferedImage makePaletteImage()
	{
		IndexColorModel icm = new IndexColorModel(8, 256, current_palette, 0, false);
		BufferedImage pimg = new BufferedImage(160, 160, BufferedImage.TYPE_BYTE_INDEXED, icm);

		byte [] tile = new byte [9*9];
		
		byte color = 0;
		for(int r=0; r<16; r++)
			for(int c=0; c<16; c++)
			{
				Arrays.fill(tile, 0, 9*9, color);
				pimg.getRaster().setDataElements(c*10, r*10, 9, 9, tile);
				color++;
			}
		
		return pimg;
	}
	
	public BufferedImage makeMapImage()
	{
		if(lastSSMap==null)
			return null;
		
		BufferedImage bi = new BufferedImage(64*16, 64*16, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = bi.createGraphics();

		int [] polyx = null;
		int [] polyy = null;
		
		for(int ty=0; ty<64; ty++)
		{
			for(int tx=0; tx<64; tx++)
			{
				MapTile mt = lastSSMap.getTile(tx, 63-ty);

				assert(mt!=null);
				
				if(mt.getType()==MapTile.Type.Solid)
				{
					//System.out.print("-");
					continue;
				}
				
				int tex = mt.getTextureFloor();
				if(tex<0)
				{
					System.out.print("#");
					continue;
				}

				int xpos = tx * 16;
				int xp1pos = (tx + 1) * 16;
				int ypos = ty * 16;
				int yp1pos = (ty + 1) * 16;
				
				SSTexture.TextureID tid = new SSTexture.TextureID(tex, SSTexture.TextureSize.TS16);
				
				//System.out.println("Using texture: " + tid.getTextureNumber() + " C" + tid.getChunkId() + " S" + tid.getSubChunk());
				
				ResBitmap rb = SSTexture.getTexture(mgr, tid);
				if(rb==null)
				{
					//System.out.print("!");
					continue;
				}
				
				if(current_palette!=null)
					rb.setPalette(current_palette);
				
				//System.out.print(".");
				
				g2d.drawImage(rb.getImage(), null, xpos, ypos);

				MapTile.Type mtt = mt.getType();
				
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
				
				if(mt.getRadiation())
				{
					g2d.setColor(Color.RED);
					g2d.drawLine(xpos, ypos, xp1pos, ypos+8);
				}

				if(mt.getBiohazard())
				{
					g2d.setColor(Color.GREEN);
					g2d.drawLine(xpos, ypos+8, xp1pos, yp1pos);
				}
			}
			//System.out.println();
		}
		
		System.out.println("done!");
		
		return bi;
	}
	
	public void saveMapImage()
	{
		if(lastSSMap==null)
			return;
		
		prepare_mapsaver_png(lastSSMap.getNumber());
		
		if(save_chooser.showSaveDialog(mainFrame)==JFileChooser.APPROVE_OPTION)
		{
			File df = save_chooser.getSelectedFile();
			
			if(df.exists())
				if(!askOverwrite(df.getName()))
					return;

			BufferedImage mapImage = makeMapImage();
			
			if(mapImage==null)
			{
				errorMessage("Error while creating map image... check console... sorry...");
				return;
			}
			
			try {
				
				String format = Util.getFileExt(df.getName());
				
				if(format==null)
					format = "png";
				
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				
				
				if(!ImageIO.write(mapImage, format, baos))
				{
					errorMessage("Sorry, cannot determine image format for \"" + df.getName() + "\"...");
				}
				else
				{
					FileOutputStream fos = new FileOutputStream(df);
					baos.writeTo(fos);
				}
			} catch (FileNotFoundException e) {
				errorMessage("Unable to find file. System said: " + e.getMessage());
			} catch (IOException e) {
				errorMessage("Error writing to file. System said: " + e.getMessage());
			}
		}
		
	}
	
	public void addFiles(File [] af)
	{
		for(File f : af)
		{
			try
			{
				if(mgr.hasResFile(f))
				{
					System.out.println("Already loaded: " + f);
					lastAddDir = f.getParentFile();
					continue;
				}
				
				ResFile rf = new ResFile(f);
				mgr.addResFile(rf);
				
				DefaultMutableTreeNode fnode = new DefaultMutableTreeNode(f.getAbsolutePath());
				makeResTree(rf, fnode);	
				MutableTreeNode troot = (MutableTreeNode)model.getRoot();
				model.insertNodeInto(fnode, troot, troot.getChildCount());
				resTree.scrollPathToVisible(new TreePath(fnode.getPath()));
				lastAddDir = f.getParentFile();
				mgr.addSearchPath(lastAddDir);
			}
			catch(ResFileException rfe)
			{
				System.err.println("Error loading " + f + ": " + rfe);
				rfe.printStackTrace();
			}
			catch(Exception e)
			{
				System.err.println("Error loading " + f + ": " + e);
				e.printStackTrace();
			}

		}
	}
	
	public void showModelInfo() {
		if(lastModelChunk>0) {
			System.out.println("Loading Model from Chunk " + lastModelChunk);
			byte [] data = mgr.getData(lastModelChunk, 0);
		
			if(data!=null) {
				ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
				
				/*
				try {
					FileOutputStream fos = new FileOutputStream("model"+lastModelChunk+".m3");
					SSModel.model_output = new PrintStream(fos);
				} catch (FileNotFoundException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
					SSModel.model_output = null;
				}
				*/
				
				SSModel model = SSModel.parseModel(bb);
				
				System.out.println(String.format(
						"Model has %d vertices, %d nodes, Flags = %s (%d), min %s, max %s, Header = %02X%02X%02X%02X%02X%02X%02X%02X",
						model.getVertices().size(),
						model.getNumberOfNodes(),
						SSModel.decodeFlags(model.getFlags()),
						model.getFlags(),
						model.getMinCoord().toString(),
						model.getMaxCoord().toString(),
						model.getHeader()[0],
						model.getHeader()[1],
						model.getHeader()[2],
						model.getHeader()[3],
						model.getHeader()[4],
						model.getHeader()[5],
						model.getHeader()[6],
						model.getHeader()[7]
				));
				
				/*
				try {
					model.saveSurfaces("M"+lastModelChunk);
				} catch (IOException e) {
					e.printStackTrace();
				}
				*/
				
				new ModelView(mgr, 0, model, current_palette);
			}
		}
	}
	
	public static void main(String...args) throws Exception
	{
		final String [] fnames = args; 
		
		SwingUtilities.invokeLater
		(
				new Runnable()
				{
					public void run()
					{
						new JResBrowser(fnames);
					}
				}
		);
	}
}
