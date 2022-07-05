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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.glu.GLU;
import de.zvxeb.jkeyboard.KeyReleaseListener;
import de.zvxeb.jkeyboard.jogl.JOGLKeyboard;
import de.zvxeb.jres.MapTile;
import de.zvxeb.jres.ResBitmap;
import de.zvxeb.jres.ResFile;
import de.zvxeb.jres.ResManager;
import de.zvxeb.jres.SSCritter;
import de.zvxeb.jres.SSLogic;
import de.zvxeb.jres.SSMap;
import de.zvxeb.jres.SSModel;
import de.zvxeb.jres.SSObject;
import de.zvxeb.jres.SSScreen;
import de.zvxeb.jres.SSSprite;
import de.zvxeb.jres.SSTexture;
import de.zvxeb.jres.TextureProperties;
import de.zvxeb.jres.Utils;
import de.zvxeb.jres.SSObject.Container;
import de.zvxeb.jres.SSObject.Fixture;
import de.zvxeb.jres.SSObject.MOTEntry;
import de.zvxeb.jres.SSObject.ObjectClass;
import de.zvxeb.jres.SSObject.RenderType;
import de.zvxeb.jres.SSTexture.TextureID;
import de.zvxeb.jres.TextureProperties.TextureProperty;
import de.zvxeb.syshmavi.MapExitListener.MapExitEvent;

import com.jogamp.common.nio.Buffers;
import com.jogamp.newt.event.MouseEvent;
import com.jogamp.newt.event.MouseListener;
import com.jogamp.newt.event.awt.AWTMouseAdapter;
import com.jogamp.opengl.util.awt.TextRenderer;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureCoords;
import com.jogamp.opengl.util.texture.TextureData;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;

public class Map3D implements GLEventListener, WindowListener {
	// Note: Cursors are in chunk 1362 in gameart.res
	
	// if set to false, use normal GL calls instead of
	// vertex indices and arrays_
	// VAs have a bit better performance...
	// 18.01.2009 - VAs are much better when doing glowing...
	// 19.01.2009 - performance rating VA + multitex -> VA + two_pass -> non-VA
	private static final boolean useVertexArrays = true;
	private static final boolean use_two_pass_glow = false;
	private static final boolean use_multitex_glow = true;
	private static final boolean use_vis_blocker = true;
	private static final boolean use_static_visibility = true;
	// set to true to have lower and upper darkmaps
	// saved as PGM images to current directory
	private static final boolean darkness_debug = false;
	
	private static final float text_scale_3d = 0.005f;
	
	private static final int CYBER_TEX = 0;

	//private boolean single_message = true;
	
	private GLU glu;
	private Environment env;
	
	private double mapToWorldSpace;
	private double mapToTextureSpace;
	private double worldToTextureSpace;
	private double tileToWorldSpace;
	private double worldToTileSpace;
	private double texBase;
	
	private JFrame frame;
	private GLCanvas glcanvas;
	
	short [] texids;
	private Texture [] textures;
	private Texture [] glow_tex;
	private TextureCoords texCoords;
	float [] texCoordPairs;
	
	private ArrayList<Integer> pickColors = new ArrayList<Integer>();
	private int pickColorIndex = 0;
	private Map<Integer, Integer> colorQuadIndex = new TreeMap<Integer, Integer>();
	private Map<Integer, Integer> colorTriIndex = new TreeMap<Integer, Integer>();
	
	// map for animated textures
	private Map<Integer, Integer> nextTexture = new TreeMap<Integer, Integer>();
	
	private Map<Integer, Texture> animTextures = new TreeMap<Integer, Texture>();
	private Map<Integer, Texture> animGlowTextures = new TreeMap<Integer, Texture>();
	
	private Map<Integer, Texture> modelTextures = new TreeMap<Integer, Texture>();
	private Map<Integer, Texture> modelGlowTextures = new TreeMap<Integer, Texture>();
	
	private Map<Integer, SSModel> models = new TreeMap<Integer, SSModel>();
	private Map<SSModel, Set<Integer>> modelTextureChunks = new HashMap<SSModel, Set<Integer>>();
	private Map<Integer, Integer> classIndexTextureChunkMap = new TreeMap<Integer, Integer>();
	
	private Map<Integer, Texture> decalTextures = new TreeMap<Integer, Texture>();
	private Map<Integer, Integer> decalScale = new TreeMap<Integer, Integer>();
	
	private double [] tmpCrateDarkness = new double [8];
	private Map<Integer, double []> crateDarknessMap = new TreeMap<Integer, double[]>();

	private Configuration configuration;
	
	private static class CenteredTexture {
		public Texture texture;
		public int center_horz;
		public int center_vert;
		
		public CenteredTexture(Texture tex, int c_h, int c_v) {
			this.texture = tex;
			this.center_horz = c_h;
			this.center_vert = c_v;
		}
		
		public static CenteredTexture makeCenteredTexture(GL2 gl, BufferedImage bi, ResBitmap rb, byte [] palette) {
			if(bi==null)
				bi = rb.getARGBImage(palette);
			
			Texture tex = AWTTextureIO.newTexture(gl.getGLProfile(), bi, true);
			tex.setTexParameteri(gl, GL.GL_TEXTURE_WRAP_S, GL.GL_NONE);
			tex.setTexParameteri(gl, GL.GL_TEXTURE_WRAP_T, GL.GL_NONE);
			
			int c_h = rb.getHot1();
			int c_v = rb.getHot2();
			
			if(c_h == 0) {
				c_h = rb.getWidth() / 2;
			}
			if(c_v == 0) {
				c_v = rb.getHeight();
			}
			
			return new CenteredTexture(tex, c_h, c_v);
		}
		public static CenteredTexture makeCenteredTexture(GL2 gl, ResBitmap rb, byte [] palette) {
			return makeCenteredTexture(gl, null, rb, palette);
		}
	}
	
	private Map<Integer, CenteredTexture> critterTextures = new TreeMap<Integer, CenteredTexture>();
	
	private int [] anim_group_size;
	private int [] anim_group_index;
	private int [] anim_group_direction;
	
	private TextureProperties textureProperties;
	
	private int doorFrame = 0;
	
	private double lastDoorPos = 0.0;
	private double doorSpeed = 8.0; // FPS
	
	private double texAnimPos = 0.0;
	private double texAnimSpeed = 4.0; // FPS
	
	private Map<Integer, Texture []> doorTextureMap = new TreeMap<Integer, Texture[]>();
	
	Texture vismaptex;
	BufferedImage vismapimg;
	
	Random rnd = new Random();
	
	private boolean show_vismap = false;
	
	private SSMap map;
	private SSMap nextMap = null;
	private ResManager rm;
	private byte [] palette;
	
	private double [] [] lower_darkmap;
	private double [] [] upper_darkmap;
	
	TilePolys [] [] polygons;
	
	enum PolygonType { PT_Wall, PT_Floor, PT_Ceiling, PT_Door };
	
	int [] tex_quads;
	int [] tex_tris;
	
	int quads_total;
	int tris_total;
	
	int max_quads_per_tex;
	int max_tris_per_tex;
	
	int [] tex_quad_offset;
	int [] tex_tri_offset;
	
	double [] quad_vertex;
	double [] quad_plane;
	double [] quad_tc;
	double [] quad_dark;
	double [] quad_full_bright;
	
	PolygonType [] quad_pt; 
	
	int [] quad_tileoffs;

	double [] tri_vertex;
	double [] tri_plane;
	double [] tri_tc;
	double [] tri_dark;
	double [] tri_full_bright;

	PolygonType [] tri_pt; 
	
	int [] tri_tileoffs;
	
	DoubleBuffer db_vertex_quad, db_texcoord_quad, db_color_quad, db_fb_quad;
	DoubleBuffer db_vertex_tri, db_texcoord_tri, db_color_tri, db_fb_tri;
	IntBuffer ib_index_quad, ib_index_tri;
	
	List<SSObject.MOTEntry> mapObjects;
	List<SSObject.Fixture> fixtureInfo;
	List<SSObject.Container> containerInfo;
	// this is a mapping from object-id to
	// position in mapObjects
	int [] mORIndex;
	
	double [] object_vertex;
	double [] object_rotation;
	SSObject.ObjectClass [] object_class;
	int [] object_id;
	int [] object_class_index;
	SSObject.CommonObjectProperty [] object_properties;
	
	double [] object_darkness;
	
	CenteredTexture [] objectSprites;
	
	int valid_objects;
	
	private float [] object_string_offsets;
	
	TextRenderer tr;
	
	private static final byte P_NORTH = 1;
	private static final byte P_EAST = 2;
	private static final byte P_SOUTH = 4;
	private static final byte P_WEST = 8;
	
	private static final byte [] portals =       { P_NORTH, P_EAST, P_SOUTH, P_WEST };
	private static final int [] portal_offsets = { 0, -1,   1, 0,   0, 1,    -1, 0 };
	
	private byte [] portal_map;
	private boolean [] visible_tiles;
	private boolean [] blocking_tiles;
	private boolean [] has_drawable;
	
	// static visibility information
	private int [] [] vis_info;
	
	private int old_tile_x = -1, old_tile_y = -1, old_cdir = -1;
	
	private static final String cheatFly = "fly";
	private static final String cheatNoFly = "nofly";
	private static final String cheatHide = "hide";
	private static final String cheatNoHide = "nohide";
	private static final String cheatNormals = "normals";
	private static final String cheatNoNormals = "nonormals";
	private static final String cheatNoLighting = "nolight";
	private static final String cheatLighting = "light";
	private static final String cheatOnlyPick = "pick";
	private static final String cheatNotOnlyPick = "nopick";

	private static String [] directions = 
	{
		"N", "NE", "E", "SE", "S", "SW", "W", "NW"
	};
	
	private Cheats cheatCodes;
	private KeyReleaseListener toggles;
	
	private static double [] [] tile_verts =
	  {
		  // Horz, Height, Vert
		
		  //   N
		  // W   E
		  //   S
		  // 2-3
		  // | |
		  // 1-4
		    { 0, 0, 1,  0, 0, 0,  1, 0, 0,  1, 0, 1 } // normal
		  // 1-2
		  // \ |
		  //  -3
		  , { 0, 0, 0,  1, 0, 0,  1, 0, 1 } // diag NE
		  // 1-2
		  // | /
		  // 3-
		  , { 0, 0, 0,  1, 0, 0,  0, 0, 1 } // diag NW
		  //  -2
		  // / |
		  // 1-3
		  , { 0, 0, 1,  1, 0, 0,  1, 0, 1 } // diag SE
		  // 2-
		  // | \
		  // 1-3
		  , { 0, 0, 1,  0, 0, 0,  1, 0, 1 } // diag SW
		  // 2^-3^
		  // |  |
		  // 1- 4
		  , { 0, 0, 1,  0, 1, 0,  1, 1, 0,  1, 0, 1 } // slope N
		  // 2 -3^
		  // |  |
		  // 1- 4^
		  , { 0, 0, 1,  0, 0, 0,  1, 1, 0,  1, 1, 1 } // slope E
		  // 2 -3
		  // |  |
		  // 1^-4^
		  , { 0, 1, 1,  0, 0, 0,  1, 0, 0,  1, 1, 1 } // slope S
		  // 2^-3
		  // |  |
		  // 1^-4
		  , { 0, 1, 1,  0, 1, 0,  1, 0, 0,  1, 0, 1 } // slope W
          // Valleys
		  //   N
		  // W   E
		  //   S
		  // 2 -4
		  // | \|
		  // 1 -3_
		  , { 0, 1, 1,  0, 1, 0,  1, 0, 1,  1, 1, 0 } // valleyNW
		  // 1 -2
		  // | /|
		  // 3_-4
		  , { 0, 1, 0,  1, 1, 0,  0, 0, 1,  1, 1, 1 } // valleyNE
		  // 2_-4
		  // | \|
		  // 1 -3
		  , { 0, 1, 1,  0, 0, 0,  1, 1, 1,  1, 1, 0, } // valleySE
		  // 1 -2_
		  // | /|
		  // 3 -4
		  , { 0, 1, 0,  1, 0, 0,  0, 1, 1,  1, 1, 1 } // valleySW
          // Rigdes
		  //   N
		  // W   E
		  //   S
		  // 2_-4_
		  // | \|
		  // 1_-3
		  , { 0, 0, 1,  0, 0, 0,  1, 1, 1,  1, 0, 0 } // ridgeSE
		  // 1_-2_
		  // | /|
		  // 3 -4_
		  , { 0, 0, 0,  1, 0, 0,  0, 1, 1,  1, 0, 1 } // ridgeSW
		  // 2 -4_
		  // | \|
		  // 1_-3_
		  , { 0, 0, 1,  0, 1, 0,  1, 0, 1,  1, 0, 0, } // ridgeNW
		  // 1_-2
		  // | /|
		  // 3_-4_
		  , { 0, 0, 0,  1, 1, 0,  0, 0, 1,  1, 0, 1 } // ridgeNE
	  };
	
	private static final int [] vertex_access_id = { 0, 1, 2, 3 };
	
	// how to access vertices when building two triangles
	// 1-2    /2
	// 3/  + 3-4
	private static final int [] vertex_access_two_tri = { 0, 1, 2,  1, 3, 2 };
	
	private static final int TCMOD = 4;
	
	private static final int
		  TL = 0
		, TR = 1
		, BR = 2
		, BL = 3
		;
	
	private static int [] [] tile_texcoords =
	{
		  { BL, TL, TR, BR } // normal and simple slopes
		, { TL, TR, BR } // diagonal NE
		, { TL, TR, BL } // diagonal NW
		, { BL, TR, BR } // diagonal SE
		, { BL, TL, BR } // diagonal SW
		, { BL, TL, BR, TR } // valleyNW
		, { TL, TR, BL, BR } // valleyNE
		  // repeat above two alternating for other valleys and ridges 
	};
	
	
	private static Map<MapTile.Type, double []> vertMap;
	private static Map<MapTile.Type, int []> texcoordMap;
	
	static
	{
		vertMap = new TreeMap<MapTile.Type, double []>();
		vertMap.put(MapTile.Type.Open, tile_verts[0]);
		vertMap.put(MapTile.Type.DiagonalNE, tile_verts[1]);
		vertMap.put(MapTile.Type.DiagonalNW, tile_verts[2]);
		vertMap.put(MapTile.Type.DiagonalSE, tile_verts[3]);
		vertMap.put(MapTile.Type.DiagonalSW, tile_verts[4]);
		
		vertMap.put(MapTile.Type.SlopeN, tile_verts[5]);
		vertMap.put(MapTile.Type.SlopeE, tile_verts[6]);
		vertMap.put(MapTile.Type.SlopeS, tile_verts[7]);
		vertMap.put(MapTile.Type.SlopeW, tile_verts[8]);

		vertMap.put(MapTile.Type.ValleyNW, tile_verts[9]);
		vertMap.put(MapTile.Type.ValleyNE, tile_verts[10]);
		vertMap.put(MapTile.Type.ValleySE, tile_verts[11]);
		vertMap.put(MapTile.Type.ValleySW, tile_verts[12]);

		vertMap.put(MapTile.Type.RidgeSE, tile_verts[13]);
		vertMap.put(MapTile.Type.RidgeSW, tile_verts[14]);
		vertMap.put(MapTile.Type.RidgeNW, tile_verts[15]);
		vertMap.put(MapTile.Type.RidgeNE, tile_verts[16]);
		
		texcoordMap = new TreeMap<MapTile.Type, int []>();
		texcoordMap.put(MapTile.Type.Open, tile_texcoords[0]);
		texcoordMap.put(MapTile.Type.DiagonalNE, tile_texcoords[1]);
		texcoordMap.put(MapTile.Type.DiagonalNW, tile_texcoords[2]);
		texcoordMap.put(MapTile.Type.DiagonalSE, tile_texcoords[3]);
		texcoordMap.put(MapTile.Type.DiagonalSW, tile_texcoords[4]);
		
		texcoordMap.put(MapTile.Type.SlopeN, tile_texcoords[0]);
		texcoordMap.put(MapTile.Type.SlopeE, tile_texcoords[0]);
		texcoordMap.put(MapTile.Type.SlopeS, tile_texcoords[0]);
		texcoordMap.put(MapTile.Type.SlopeW, tile_texcoords[0]);

		texcoordMap.put(MapTile.Type.ValleyNW, tile_texcoords[5]);
		texcoordMap.put(MapTile.Type.ValleyNE, tile_texcoords[6]);
		texcoordMap.put(MapTile.Type.ValleySE, tile_texcoords[5]);
		texcoordMap.put(MapTile.Type.ValleySW, tile_texcoords[6]);

		texcoordMap.put(MapTile.Type.RidgeSE, tile_texcoords[5]);
		texcoordMap.put(MapTile.Type.RidgeSW, tile_texcoords[6]);
		texcoordMap.put(MapTile.Type.RidgeNW, tile_texcoords[5]);
		texcoordMap.put(MapTile.Type.RidgeNE, tile_texcoords[6]);
	}
	
	private List<MapExitListener> listeners = new Vector<MapExitListener>();
	
	public boolean addMapExitListener(MapExitListener mee) {
		return listeners.add(mee);
	}

	public boolean removeMapExitListener(MapExitListener mee) {
		return listeners.remove(mee);
	}
	
	public Environment getEnvironment() {
		return env;
	}
	
	public Map3D(Configuration configuration, ResManager rm, SSMap map, byte [] palette, TextureProperties textureProperties)
	{
		this.configuration = configuration;

		env = new Environment();
		env.cam_pos[VecMath.IDX_X] = map.getHorzSize() * env.level_scale / 2;
		env.cam_pos[VecMath.IDX_Y] = 0;
		env.cam_pos[VecMath.IDX_Z] = map.getVertSize() * env.level_scale / 2;
		
		env.cam_rot[VecMath.IDX_X] = 0;
		env.cam_rot[VecMath.IDX_Y] = 0;
		env.cam_rot[VecMath.IDX_Z] = 0;
		
		env.cam_view[VecMath.IDX_X] = 0.0;
		env.cam_view[VecMath.IDX_Y] = 0.0;
		env.cam_view[VecMath.IDX_Z] = -1.0;
		
		env.frame_time = -1;

		this.textureProperties = textureProperties;
		this.map = map;
		this.rm = rm;
		this.palette = palette;
	}
	
	private TextureProperty getTexturePropertyFor(int texid) {
		if(textureProperties!=null) {
			return textureProperties.getPropertyFor(texid);
		}
		
		return null;
	}
	
	private boolean createGlowTex(ResBitmap fromres, BufferedImage fromrgb, BufferedImage into) {
		byte [] bitmap = fromres.getBitmap();
		int w = fromrgb.getWidth();
		int h = fromrgb.getHeight();
		
		boolean does_glow_at_all = false;
		
		for(int y=0; y<h; y++)
		{
			for(int x=0; x<w; x++)
			{
				int palindex = ((int)bitmap[y * w + x])&0xff;
				// 8-10 is also not glowing. checked with palimage texture in real game engine 12.06.2010
				boolean is_glowing = (palindex > 2) && (palindex < 8 || palindex > 10) && (palindex < 32);
				int color;
				color = is_glowing?(0xFF000000|fromrgb.getRGB(x, y)):0x00FFFFFF;
				into.setRGB(x, y, color);
				if((is_glowing))
					does_glow_at_all = true;
			}
		}
		
		return does_glow_at_all;
	}
	
	private void fetchTextures(GL2 gl)
	{
		texids = map.getUsedTextures();
		textures = new Texture [texids.length];
		glow_tex = new Texture [texids.length];
		
		anim_group_size = new int [texids.length];
		anim_group_index = new int [texids.length];
		anim_group_direction = new int [texids.length];
		Arrays.fill(anim_group_direction, 1);
		
		int ti = 0;
		int gtex_count = 0;
		int group_count = 0;
		
		int max_group_size = 0;

		if(!map.isCyberspace()) {
			for(short s : texids)
			{
				TextureID tid = new TextureID(s, SSTexture.TextureSize.TS128);
				ResBitmap rb = SSTexture.getTexture(rm, tid);
				BufferedImage bi = rb.getImage(palette);

				textures[ti] = AWTTextureIO.newTexture(gl.getGLProfile(), bi, true);
				textures[ti].setTexParameteri(gl, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
				textures[ti].setTexParameteri(gl, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);

				BufferedImage glow_bitmap = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);

				boolean does_glow_at_all = createGlowTex(rb, bi, glow_bitmap);

				if(does_glow_at_all)
				{
					glow_tex[ti] = AWTTextureIO.newTexture(gl.getGLProfile(), glow_bitmap, true);
					glow_tex[ti].setTexParameteri(gl, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
					glow_tex[ti].setTexParameteri(gl, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
					gtex_count++;
				}
				else
				{
					glow_tex[ti] = null;
				}

				TextureProperty tp = getTexturePropertyFor(s);

				Map<Integer, Integer> indexToTexture = new TreeMap<Integer, Integer>();
				int min_id=Integer.MAX_VALUE, max_id=0;

				if(tp!=null && tp.getAnimationGroup()!=0) {
					int current_texid = s - tp.getAnimationIndex();

					TextureProperty current_tp = null;
					while((current_texid <= s) || (current_tp!=null && current_tp.getAnimationGroup()==tp.getAnimationGroup())) {
						current_tp = getTexturePropertyFor(current_texid);

						if(current_tp!=null && current_tp.getAnimationGroup()==tp.getAnimationGroup()) {
							indexToTexture.put((int)current_tp.getAnimationIndex(), current_texid);
							min_id = Math.min(min_id, current_tp.getAnimationIndex());
							max_id = Math.max(max_id, current_tp.getAnimationIndex());
						}
						current_texid++;
					}
				}

				if(indexToTexture.size()>1) {
					group_count++;

					max_group_size = Math.max(max_group_size, indexToTexture.size());

					anim_group_size[ti] = indexToTexture.size();

					for(int gi=min_id; gi<=max_id; gi++) {
						int gtid = indexToTexture.get(gi);
						Texture anim_tex;
						Texture anim_glow_tex;

						if(gtid == s) {
							anim_tex = textures[ti];
							anim_glow_tex = glow_tex[ti];
						} else {
							tid = new TextureID(gtid, SSTexture.TextureSize.TS128);
							rb = SSTexture.getTexture(rm, tid);
							bi = rb.getImage(palette);

							anim_tex = AWTTextureIO.newTexture(gl.getGLProfile(), bi, true);
							anim_tex.setTexParameteri(gl, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
							anim_tex.setTexParameteri(gl, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);


							glow_bitmap = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);

							does_glow_at_all = createGlowTex(rb, bi, glow_bitmap);

							if(does_glow_at_all)
							{
								anim_glow_tex = AWTTextureIO.newTexture(gl.getGLProfile(), glow_bitmap, true);
								anim_glow_tex.setTexParameteri(gl, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
								anim_glow_tex.setTexParameteri(gl, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
							}
							else
							{
								anim_glow_tex = null;
							}
						}

						animTextures.put(gtid, anim_tex);
						if(anim_glow_tex!=null) {
							animGlowTextures.put(gtid, anim_glow_tex);
						}

						int next_texid;
						if(gi==max_id) {
							next_texid = indexToTexture.get(min_id);
						} else {
							next_texid = indexToTexture.get(gi+1);
						}
						System.out.println(s + ": " + gtid + " -> " + next_texid);
						nextTexture.put(gtid, next_texid);
					}
				}

				ti++;
			}

			System.out.println("Processed " + ti + " textures... " + gtex_count + " are glowing, there are " + group_count + " animation groups");

			// All textures have same dimension and type...
			if(textures.length>0)
			{
				texCoords = textures[0].getImageTexCoords();

				texCoordPairs = new float []
				                           {
						texCoords.left(),  texCoords.top()
						, texCoords.right(), texCoords.top()
						, texCoords.right(), texCoords.bottom()
						, texCoords.left(),  texCoords.bottom()
				                           };
			}
		} else {
			texCoordPairs = new float [8];
		}
		
		tex_quads = new int [map.isCyberspace() ? 1 : textures.length];
		tex_tris = new int [map.isCyberspace() ? 1 : textures.length];

		tex_quad_offset = new int [map.isCyberspace() ? 1 : textures.length];
		tex_tri_offset = new int [map.isCyberspace() ? 1 : textures.length];
	}
	
	private void fetchSpriteTextures(GL2 gl) {
		objectSprites = new CenteredTexture [object_properties.length];
		
		int processedTexs = 0;
		int blackTexs = 0;
		int index = 0;
		
		for(SSObject.CommonObjectProperty cop : object_properties) {

			objectSprites[index] = null;
			
			if(cop!=null) {
				ResBitmap spriterm = SSSprite.getSprite(rm, cop.baseframe);

				if(spriterm!=null) {
					if(!spriterm.isTotalBlack()) {
						objectSprites[index] = CenteredTexture.makeCenteredTexture(gl, spriterm, palette);
						
						System.out.println("Texture for sprite #" + object_id[index]);
						System.out.println("Size: " + objectSprites[index].texture.getWidth() + "x" + objectSprites[index].texture.getHeight() + " Center: " + + objectSprites[index].center_horz + "x" + + objectSprites[index].center_vert);
						
						processedTexs++;
					} else {
						blackTexs++;
					}
				}			
			} else {
			}
			index++;
		}
		
		System.out.println(String.format("Loaded %d of %d sprites (%d black)...", processedTexs, valid_objects, blackTexs));
	}
	
	private void fetchDoorTextures(GL2 gl) {
		for(SSObject.MOTEntry mote : mapObjects) {
			if(mote.getObjectClass() == SSObject.ObjectClass.DoorsAndGratings.ordinal()) {
				int doorChunk = SSLogic.doorBitmapChunkBase + mote.getObjectId() - SSObject.object_index[SSObject.ObjectClass.DoorsAndGratings.ordinal()][0];
				if(doorTextureMap.containsKey(Integer.valueOf(doorChunk)))
					continue;
				//System.out.println("Fetching texture for door #" + doorChunk);
				ResFile.DirEntry de = rm.getChunkEntry(doorChunk);
				if(!de.isSubDirectory())
					continue;
				ResFile.SubDirectory subdir = de.getResFile().getSubDirectory(de);
				int numtexs = subdir.getNumberOfSubChunks();
				Texture [] texs = new Texture [numtexs];

				for(int t=0; t<numtexs; t++) {
					byte [] tdata = rm.getData(doorChunk, t);
					ResBitmap trb = new ResBitmap(tdata, (short)doorChunk, t);
					BufferedImage bi = trb.getARGBImage(palette);
					texs[t] = AWTTextureIO.newTexture(gl.getGLProfile(), bi, true);
					texs[t].setTexParameteri(gl, GL.GL_TEXTURE_WRAP_S, GL.GL_NONE);
					texs[t].setTexParameteri(gl, GL.GL_TEXTURE_WRAP_T, GL.GL_NONE);
				}

				doorTextureMap.put(Integer.valueOf(doorChunk), texs);
			}
		}
	}
	
	private void fetchCritterTextures(GL2 gl) {
		for(SSObject.MOTEntry mote : mapObjects) {
			if(mote.getOClass() == SSObject.ObjectClass.Critters) {
				int critNum = mote.getObjectId() - SSCritter.BASE_OBJECT_ID;
				if(critNum < 0 || critNum >= SSCritter.NUM_CRITTERS)
					continue;
				
				int critChunk = SSCritter.Critter_idle_base_chunk[critNum]+6;
				
				if(critNum == SSCritter.CRITTER_SHODAN) {
					critChunk -= 6;
				}
				
				if(critterTextures.containsKey(critNum))
					continue;
				
				System.out.println("Fetching texture for critter #" + mote.getObjectId() + ": Chunk #" + critChunk);
				ResFile.DirEntry de = rm.getChunkEntry(critChunk);
				if(de==null || !de.isSubDirectory())
					continue;
				
				ResFile.SubDirectory subdir = de.getResFile().getSubDirectory(de);
				
				int numtexs = subdir.getNumberOfSubChunks();
				
				if(numtexs > 0) {
					byte [] tdata = rm.getData(critChunk, 0);
					ResBitmap crb = new ResBitmap(tdata, (short)critChunk, 0);
					
					CenteredTexture ct = CenteredTexture.makeCenteredTexture(gl, crb, palette);
					
					System.out.println("Size: " + ct.texture.getWidth() + "x" + ct.texture.getHeight() + "Center: " + ct.center_horz + "x" + ct.center_vert);
					
					critterTextures.put(Integer.valueOf(critNum), ct);
				}
			}
		}
	}
	
	private void fetchModels() {
		for(int oindex=0; oindex<valid_objects; oindex++) {
			if(object_properties[oindex].renderType == SSObject.RenderType.Model3D) {
				MOTEntry mote = mapObjects.get(mORIndex[oindex]);
				
				int modelnum = object_properties[oindex].model3d_index;
				
				int modelchunk = modelnum + SSModel.MODEL_CHUNK_BASE;
				
				boolean hadModel = false;
				byte [] modeldata = null;
				if(!models.containsKey(modelnum)) {
					System.out.println("Loading model #" + modelchunk + " - #" + modelnum);
					modeldata = rm.getData(modelchunk, 0);
				} else {
					hadModel = true;
				}
				
				if(!hadModel && modeldata==null) {
					System.err.println("No data for model #" + modelchunk);
				} else {
					try {
						SSModel model = models.get(modelnum);
						if(model == null) {
							ByteBuffer bb = ByteBuffer.wrap(modeldata).order(ByteOrder.LITTLE_ENDIAN);
							model = SSModel.parseModel(bb);
							models.put(modelnum, model);
						}
						
						if(model.usesObjectTexture()) {
							Fixture f = fixtureInfo.get(object_class_index[oindex]);
							int tc = SSModel.MODEL_OBJECT_TEXTURE_CHUNK_BASE + (f.getStart_frame() & 0x7f); // 0x7f is non-special
							// is monitor ?
							if(mote.getOClass() == ObjectClass.SceneryAndFixtures) {
								if(mote.getObjectSubClass() == 0 || mote.getObjectSubClass() == 5) {
									if(mote.getObjectType() == 6 || mote.getObjectType() == 7) {
										tc = SSScreen.ANIMATED_SCREEN_START + f.getStart_frame();
										System.out.println("This is a monitor: tc: " + tc);
									}
								}
							}
							
							System.out.println("Adding model-texture " + tc + " Class Index " + object_class_index[oindex] + " Start-Frame " + f.getStart_frame());
							Set<Integer> textureChunks = modelTextureChunks.get(model);
							if(textureChunks == null) {
								textureChunks = new TreeSet<Integer>();
								modelTextureChunks.put(model, textureChunks);
							}
							textureChunks.add(tc);
							classIndexTextureChunkMap.put(object_class_index[oindex], tc);
						}
					} catch(Exception e) {
						System.err.println("Error while parsing model data for #" + modelchunk);
						System.err.println(e.getMessage());
						
					}
				}
			}
		}
	}
	
	private void fetchModelTextures(GL2 gl) {
		for(Map.Entry<Integer, SSModel> modelentry : models.entrySet()) {
			SSModel model = modelentry.getValue();
			
			boolean usesObjectTexture = model.usesObjectTexture();
			
			System.out.println("Fetching " + model.getUsedTextures().size() + " textures  for model " + modelentry.getKey() + ". Nodes: " + model.getNumberOfNodes());
			
			List<Integer> texIds = new Vector<Integer>(model.getUsedTextures());
			
			if(usesObjectTexture) {
				Set<Integer> modelTextures = modelTextureChunks.get(model);
				System.out.println("Also fetching model-textures " + modelTextures);

				for(int modelTexChunk : modelTextures) {
					/*if(modelTexChunk < 2180 || modelTexChunk > 2194) {
						System.out.println("...invalid model texture... " + modelTexChunk );
					} else {*/
						texIds.add(modelTexChunk);
					//}
				}
			}
			
			for(Integer texid : texIds) {
				if(modelTextures.containsKey(texid))
					continue;
				
				System.out.println("Loading model texture #" + texid);
				
				byte [] texdata = rm.getData(texid, 0);
				
				if(texdata==null) {
					System.err.println("Unable to load texture data...");
					continue;
				}
				
				ResBitmap rb = new ResBitmap(texdata, texid.shortValue(), 0);
				BufferedImage bi = rb.getARGBImage(palette);
				
				Texture mtex = AWTTextureIO.newTexture(gl.getGLProfile(), bi, true);
				mtex.setTexParameteri(gl, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
				mtex.setTexParameteri(gl, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
				mtex.setTexParameteri(gl, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
				mtex.setTexParameteri(gl, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
				
				modelTextures.put(texid, mtex);
				
				BufferedImage glowimg = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_INT_ARGB);
				
				boolean glows_at_all = createGlowTex(rb, bi, glowimg);
				
				if(glows_at_all) {
					Texture glowtex = AWTTextureIO.newTexture(gl.getGLProfile(), glowimg, true);
					glowtex.setTexParameteri(gl, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
					glowtex.setTexParameteri(gl, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
					glowtex.setTexParameteri(gl, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
					glowtex.setTexParameteri(gl, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
					modelGlowTextures.put(texid, glowtex);
				}
			}
		}
	}
	
	private void fetchOtherFixtureTextures(GL2 gl) {
		List<Integer> texIds = new LinkedList<Integer>();
		for(int oindex=0; oindex<valid_objects; oindex++) {
			texIds.clear();
			if(object_class[oindex] == ObjectClass.Containers && object_properties[oindex].renderType == RenderType.Special) {
				SSObject.Container con = containerInfo.get(object_class_index[oindex]);
				if(con == null) {
					System.err.println("No container info for container! obj-id #" + object_id[oindex] + " CI: " + object_class_index[oindex]);
					continue;
				}

				System.out.println("Container texture: " + con.getTopTexture() + " " + con.getSideTexture() + " CI: " + object_class_index[oindex]);
				
				texIds.add(con.getTopTexture() == 0 ? SSObject.Container.CONTAINER_DEFAULT_TOP_BOTTOM_TEXTURE : con.getTopTexture());
				texIds.add(con.getSideTexture() == 0 ? SSObject.Container.CONTAINER_DEFAULT_SIDE_TEXTURE : con.getSideTexture());
			}
			
			if(object_id[oindex] == SSObject.OBJID_BRIDGE) {
				Fixture fix = fixtureInfo.get(object_class_index[oindex]);
				
				if(fix == null) {
					System.err.println("No fixture info for bridge! obj-id #" + object_id[oindex]);
					continue;
				}
				
				int texTop = fix.getTexture() & 0x00FF;
				int texSide = (fix.getTexture() & 0xFF00) >>> 8;
		
				// special texture ?
				// highest bit indicates use of wall texture (7 bit index)
				// else fetch from model textures (add 7 bits to base chunk)
				if( (texTop & 0x80) == 0) {
					texIds.add( texTop & 0x7F );
				}
				if( (texSide & 0x80) == 0) {
					texIds.add( texSide & 0x7F );
				}
			}

			for(int texid : texIds) {

				texid += SSModel.MODEL_OBJECT_TEXTURE_CHUNK_BASE;

				if(!modelTextures.containsKey(texid)) {
					System.out.println(String.format(
							"Loading other-fixture texture #%d for object id %d"
							, texid
							, object_id[oindex]
					));

					byte [] texdata = rm.getData(texid, 0);

					if(texdata==null) {
						System.err.println("Unable to load other-fixture texture data... (id #" + texid + ")");
						continue;
					}

					ResBitmap rb = new ResBitmap(texdata, (short)texid, 0);

					if(rb.getWidth() * rb.getHeight() <= 0) {
						System.err.println("Invalid other-fixture texture data... (id #" + texid + ")");
						continue;
					}

					BufferedImage bi = rb.getARGBImage(palette);

					Texture mtex = AWTTextureIO.newTexture(gl.getGLProfile(), bi, true);
					mtex.setTexParameteri(gl, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
					mtex.setTexParameteri(gl, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
					mtex.setTexParameteri(gl, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
					mtex.setTexParameteri(gl, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);

					modelTextures.put(texid, mtex);
					System.out.println("Model-Texture-Put: #" + texid + " -> " + mtex);

					BufferedImage glowimg = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_INT_ARGB);

					boolean glows_at_all = createGlowTex(rb, bi, glowimg);

					if(glows_at_all) {
						Texture glowtex = AWTTextureIO.newTexture(gl.getGLProfile(), glowimg, true);
						glowtex.setTexParameteri(gl, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
						glowtex.setTexParameteri(gl, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
						glowtex.setTexParameteri(gl, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
						glowtex.setTexParameteri(gl, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
						modelGlowTextures.put(texid, glowtex);
					}
				}
			}
			
			if(object_class[oindex] == SSObject.ObjectClass.SceneryAndFixtures) {
				SSObject.MOTEntry mote = mapObjects.get(mORIndex[oindex]);
				// is words ?
				if(mote.getObjectTypeId() == SSObject.OTID_WORDS) {
					Fixture fix = fixtureInfo.get(mote.getObjectClassIndex());
					if(fix!=null) {
						byte [] worddata = rm.getData(SSLogic.wordChunk, fix.getWord());
						if(worddata != null) {
							int font = SSLogic.fontBaseChunk + Fixture.WORDS_FONT[fix.getFlags() & 0x03];
							ResFile.DirEntry de = rm.getChunkEntry(font);
							if(de!=null) {
								de.zvxeb.jres.Font f = new de.zvxeb.jres.Font(de.getDataBuffer());
								Dimension d = f.stringBounds(worddata, 0, worddata.length-1);
								BufferedImage wordsImage = new BufferedImage(d.width+2, d.height+2, BufferedImage.TYPE_INT_ARGB);
								int colorIndex = fix.getTexture()&0xFF;
								if(colorIndex == 0)
									colorIndex = SSLogic.defaultFontColor;
								
								int wcolor = 0xFF000000 | (palette[colorIndex*3] << 16) | (palette[colorIndex*3+1] << 8) | palette[colorIndex*3+2]; 
								
								f.renderString(worddata, 0, worddata.length-1, wordsImage, 1, 1, wcolor, 0, palette);
								
								Texture mtex = AWTTextureIO.newTexture(gl.getGLProfile(), wordsImage, true);
								mtex.setTexParameteri(gl, GL.GL_TEXTURE_WRAP_S, GL.GL_NONE);
								mtex.setTexParameteri(gl, GL.GL_TEXTURE_WRAP_T, GL.GL_NONE);
								mtex.setTexParameteri(gl, GL.GL_TEXTURE_MAG_FILTER, d.height > 8 ? GL.GL_LINEAR_MIPMAP_LINEAR : GL.GL_NEAREST);
								mtex.setTexParameteri(gl, GL.GL_TEXTURE_MIN_FILTER, d.height > 8 ? GL.GL_LINEAR_MIPMAP_LINEAR : GL.GL_NEAREST);
								
								decalTextures.put(oindex, mtex);
								
								int logScale = (fix.getFlags() >> 4) & 0x03;
								
								if(logScale != 0) {
									decalScale.put(oindex, logScale);
								}
								
							}
						}
					}
					continue; // next object
				}
				
				if(mote.getObjectTypeId() == SSObject.OTID_GRAFITTI || mote.getObjectTypeId() == SSObject.OTID_DECAL) {
					short chunk = (short) (mote.getObjectTypeId() == SSObject.OTID_GRAFITTI ? SSLogic.graffitiChunk : SSLogic.decalChunk);
					int subChunk = mote.getState();
					byte [] textureData = rm.getData(chunk, subChunk);
					
					if(textureData != null) {
						ResBitmap rm = new ResBitmap(textureData, chunk, subChunk);
						BufferedImage bi = rm.getARGBImage(palette);
						
						Texture mtex = AWTTextureIO.newTexture(gl.getGLProfile(), bi, true);
						mtex.setTexParameteri(gl, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
						mtex.setTexParameteri(gl, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
						mtex.setTexParameteri(gl, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
						mtex.setTexParameteri(gl, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
						
						decalTextures.put(oindex, mtex);
					}
				}
			}
		}
	}
	
	private void cleanStageForNewMap() 
	{
		glcanvas.removeGLEventListener(Map3D.this);
		JOGLKeyboard.dispose();
		JOGLKeyboard.removeKeyReleaseListener(toggles);
		JOGLKeyboard.removeKeyReleaseListener(cheatCodes);
		frame.dispose();
	}
	
	public void setup()
	{
		cheatCodes = new Cheats();
		cheatCodes.addCheat(cheatFly);
		cheatCodes.addCheat(cheatNoFly);
		cheatCodes.addCheat(cheatHide);
		cheatCodes.addCheat(cheatNoHide);
		cheatCodes.addCheat(cheatNormals);
		cheatCodes.addCheat(cheatNoNormals);
		cheatCodes.addCheat(cheatNoLighting);
		cheatCodes.addCheat(cheatLighting);
		cheatCodes.addCheat(cheatOnlyPick);
		cheatCodes.addCheat(cheatNotOnlyPick);
		
		toggles = new KeyReleaseListener()
		{
			@Override
			public void keyReleased(int keyid) {
				if(keyid == env.key_show_map)
				{
					show_vismap = !show_vismap;
					System.out.println("VisMap: " + (show_vismap?"on":"off"));
				}
				
				if(keyid == env.key_previous_map)
				{
					int nextMap = Map3D.this.map.getNumber() - 1;
					if(nextMap >= 0) {
						SSMap m = SSMap.getMap(Map3D.this.rm, nextMap);
						loadMap(m);
					}
				}
				
				if(keyid == env.key_next_map)
				{
					int nextMap = Map3D.this.map.getNumber() + 1;
					if(nextMap < SSLogic.numberOfMaps) {
						SSMap m = SSMap.getMap(Map3D.this.rm, nextMap);
						loadMap(m);
					}
				}
				
				if(keyid == KeyEvent.VK_ESCAPE)
				{
					if(mapExit(MapExitEvent.Exit, -1))
					{
						cleanStageForNewMap();
					}
					else {
						System.out.println("Listener does not allow exit...");
					}
				}
			}
		};
		
		
		env.lastTime = -1L;
		
		env.rotationZ = 0.0;
		env.rotationY = 0.0;

		frame = new JFrame("SyShMaVi-3D");
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		
		glcanvas = new GLCanvas();
		glcanvas.setFocusTraversalKeysEnabled(false);
		
		JOGLKeyboard.initialize(glcanvas);

		JOGLKeyboard.addKeyReleaseListener(cheatCodes);
		JOGLKeyboard.addKeyReleaseListener(toggles);
		
		frame.add(glcanvas, BorderLayout.CENTER);
		
		glcanvas.addGLEventListener(this);
		
		frame.setSize(640, 480);
		frame.setVisible(true);
		
		frame.addWindowListener(this);
		
		glcanvas.requestFocus();
	}
	
	private final int M_F = 1;
	private final int M_B = 2;
	private final int M_L = 4;
	private final int M_R = 8;

	private final int BLOCK_FB = M_F | M_B;
	private final int BLOCK_LR = M_L | M_R;
	
	public void handle_movement(long delta)
	{
		int f = JOGLKeyboard.isPressed(env.key_forward)?M_F:0;
		int b = JOGLKeyboard.isPressed(env.key_backward)?M_B:0;
		int l = JOGLKeyboard.isPressed(env.key_left)?M_L:0;
		int r = JOGLKeyboard.isPressed(env.key_right)?M_R:0;

		int mov_case = f | b | l | r;
		
		// filter contradictions
		if( (mov_case & BLOCK_FB) == BLOCK_FB )
			mov_case &= ~BLOCK_FB;

		if( (mov_case & BLOCK_LR) == BLOCK_LR )
			mov_case &= ~BLOCK_LR;
		
		int t_l = JOGLKeyboard.isPressed(env.key_turn_left)?M_L:0;
		int t_r = JOGLKeyboard.isPressed(env.key_turn_right)?M_R:0;
		
		int turn_case = t_l | t_r;
		
		if( (turn_case & BLOCK_LR) == BLOCK_LR )
			turn_case &= ~BLOCK_LR;

		boolean running = JOGLKeyboard.isPressed(env.key_runmod);
		double mov_scale = (double)delta / 1000.0;
		double mov_mult = mov_scale * (running?env.run_mod:1.0);
		
		if(turn_case!=0)
		{
			double turn_add = mov_mult * env.turn_speed;
			
			if((turn_case & M_L)!=0)
			{
				env.cam_rot[VecMath.IDX_Y] -= turn_add;
			}
			
			if((turn_case & M_R)!=0)
			{
				env.cam_rot[VecMath.IDX_Y] += turn_add;
			}
			
			while(env.cam_rot[VecMath.IDX_Y]<0)
				env.cam_rot[VecMath.IDX_Y] += 360.0;
			while(env.cam_rot[VecMath.IDX_Y]>=360.0)
				env.cam_rot[VecMath.IDX_Y] -= 360.0;
		}
		
		// check situation...
		if(mov_case!=0)
		{
			double mov_angle = 0.0;

			switch(mov_case)
			{
			case 1: // f
				break;
			case 2: // b
				mov_angle = 180.0;
				break;
			case 4: // l
				mov_angle = 90.0;
				break;
			case 5: // l + f
				mov_angle = 45.0;
				break;
			case 6: // l + b
				mov_angle = 135.0;
				break;
			case 8: // r
				mov_angle = -90.0;
				break;
			case 9: // r + f
				mov_angle = -45.0;
				break;
			case 10: // r + b
				mov_angle = -135.0;
				break;
			default:
				System.err.println("Missed this: " + mov_case);
			}

			mov_angle -= env.cam_rot[VecMath.IDX_Y];

			double movement = mov_mult * env.speed;
			
			double r_ma = (mov_angle / 180.0) * Math.PI;

			double delta_x = Math.sin(r_ma) * movement;
			double delta_z = Math.cos(r_ma) * movement;

			env.cam_pos[VecMath.IDX_X] -= delta_x;
			env.cam_pos[VecMath.IDX_Z] -= delta_z;
		}
		
		if(JOGLKeyboard.isPressed(env.key_down) && !JOGLKeyboard.isPressed(env.key_up))
		{
			double movement = mov_mult * env.speed;
			env.cam_pos[VecMath.IDX_Y] -= movement;
		}
		else
		{
			if(JOGLKeyboard.isPressed(env.key_up) && !JOGLKeyboard.isPressed(env.key_down))
			{
				double movement = mov_mult * env.speed;
				env.cam_pos[VecMath.IDX_Y] += movement;
			}
		}
		
		if(JOGLKeyboard.isPressed(env.key_lookup) && !JOGLKeyboard.isPressed(env.key_lookdown))
		{
			double movement = mov_mult * env.speed;
			env.cam_rot[VecMath.IDX_X] -= 45 * movement;
		}
		else
		if(JOGLKeyboard.isPressed(env.key_lookdown) && !JOGLKeyboard.isPressed(env.key_lookup))
		{
			double movement = mov_mult * env.speed;
			env.cam_rot[VecMath.IDX_X] += 45 * movement;
		}
		else
		if(JOGLKeyboard.isPressed(env.key_lookcenter))
		{
			env.cam_rot[VecMath.IDX_X] = 0;
		}
		
		env.cam_rot[VecMath.IDX_X] = Math.max(-90.0, Math.min(90.0, env.cam_rot[VecMath.IDX_X]));
		
		if(JOGLKeyboard.isPressed(KeyEvent.VK_B))
		{
			env.fov_h -= 10.0 * mov_scale;
			env.fov_update = true;
		}
		if(JOGLKeyboard.isPressed(KeyEvent.VK_N))
		{
			env.fov_h = 60.0;
			env.fov_update = true;
		}
		if(JOGLKeyboard.isPressed(KeyEvent.VK_M))
		{
			env.fov_h += 10.0 * mov_scale;
			env.fov_update = true;
		}
		
		env.fov_h = Math.max(30.0, Math.min(180.0, env.fov_h));
		
		env.cam_view[VecMath.IDX_X] = Math.sin(env.cam_rot[VecMath.IDX_Y] / 180.0 * Math.PI);
		env.cam_view[VecMath.IDX_Z] = -Math.cos(env.cam_rot[VecMath.IDX_Y] / 180.0 * Math.PI);
		env.cam_view[VecMath.IDX_Y] = -Math.cos((90 - env.cam_rot[VecMath.IDX_X]) / 180.0 * Math.PI);
		VecMath.normalize(env.cam_view, env.cam_view);
		
		env.cam_planed = 
			-(
				env.cam_view[VecMath.IDX_X] * env.cam_pos[VecMath.IDX_X] +
				env.cam_view[VecMath.IDX_Y] * env.cam_pos[VecMath.IDX_Y] +
				env.cam_view[VecMath.IDX_Z] * env.cam_pos[VecMath.IDX_Z]
			);
	}
	
	public void handle_environment(long delta)
	{
		lastDoorPos += delta * doorSpeed / 1000.0;
		
		while(lastDoorPos>1.0) {
			doorFrame++;
			lastDoorPos-=1.0;
		}
		
		texAnimPos += delta * texAnimSpeed / 1000.0;
		
		int texAdvance = 0;
		while(texAnimPos>1.0) {
			texAdvance++;
			texAnimPos-=1.0;
		}
		
		if(texAdvance > 0) {
			for(int ti=0; ti < texids.length; ti++) {
				if(anim_group_size[ti]<2)
					continue;
				
				anim_group_index[ti] += anim_group_direction[ti] * texAdvance;
				
				while( (anim_group_index[ti] < 0) || (anim_group_index[ti] % anim_group_size[ti] != anim_group_index[ti]) ) {
					if(anim_group_index[ti] < 0) {
						anim_group_index[ti] *= -1; // bounce at start - easy...
						anim_group_direction[ti] = 1;
					}
					if(anim_group_index[ti] >= anim_group_size[ti]) {
						// bouce at end - wierd...
						anim_group_index[ti] = (anim_group_size[ti]-2) - (anim_group_index[ti] - anim_group_size[ti]);
						anim_group_direction[ti] = -1;
					}
				}
				
				int texid = texids[ti];
				
				Integer next = texid;
				
				for(int a = 0; a < anim_group_index[ti] && next != null; a++) {
					next = nextTexture.get(next); 
				}
				
				if(next!=null) {
					Texture t = animTextures.get(next);
					Texture g = animGlowTextures.get(next);
					
					if(t!=null) {
						textures[ti] = t;
						glow_tex[ti] = g;
					}
				}
			}
		}
	}
	
	private static final int DIR_N = 0;
	private static final int DIR_NW = 1;
	private static final int DIR_W = 2;
	private static final int DIR_SW = 3;
	private static final int DIR_S = 4;
	private static final int DIR_SE = 5;
	private static final int DIR_E = 6;
	private static final int DIR_NE = 7;

	public void createPolygons()
	{
		double [] []
		             v0 = new double [2] [3]
		           , v1 = new double [2] [3]
		           , v3 = new double [2] [3]
		           , v01 = new double [2] [3]
		           , v03 = new double [2] [3]
		           , normal = new double [2] [3]
		           ;
		double []
		        	plane_d = new double [2]
 		          , centerX = new double [2]
		          , centerY = new double [2]
 		          , centerZ = new double [2]
		          ;

		int vlast; 
		
		double
		    x1, y1, z1
		  , x2, y2, z2
		  , x3, y3, z3
		  , x4, y4, z4
		  ;
		
		double
		    tc_u1, tc_v1
		  , tc_u2, tc_v2
		  , tc_u3, tc_v3
		  , tc_u4, tc_v4
		  ;
		
		for(int mapy = 0; mapy < map.getVertSize(); mapy++)
		{
			for(int mapx = 0; mapx < map.getHorzSize(); mapx++)
			{
				if(!map.isSolidTile(mapx, (map.getVertSize()-1)-mapy))
				{
					int my = (map.getVertSize()-1)-mapy;
					MapTile mt = map.getTile(mapx, my);
					MapTile.Type mttf = mt.getFloorType();
					MapTile.Type mttc = mt.getCeilingType();
					
					double slope = mt.getSlope() * mapToWorldSpace;
					
					// assume floor and ceiling are sloping
					double fslope = slope;
					double cslope = slope;

					// and now check if one of them does not
					if(mttf.compareTo(MapTile.SlopeBase)<0)
						fslope = 0.0;
					
					if(mttc.compareTo(MapTile.SlopeBase)<0)
						cslope = 0.0;
					
					int trot = TCMOD - mt.getFRot();
					
					double fpos = mt.getFloor() * mapToWorldSpace;
					// ceiling height is measured at end of slope
					double cpos = mt.getCeiling() * mapToWorldSpace - cslope;
					
					TilePolys tp = new TilePolys();
					
					polygons[mapx][mapy] = tp;

					int texid;
					
					double [] vertCoords = vertMap.get(mttf);
					int [] texIndices = texcoordMap.get(mttf);
					
					int verts = vertCoords.length / 3;
					int polygons = 1;
					int vertsperpoly = verts / polygons;

					int [] va = vertex_access_id;

					texid = map.isCyberspace() ? CYBER_TEX : mt.getTextureIndexFloor();

					boolean doTriangles = true;

					if(MapTile.isOpen(mttf) || MapTile.isSlope(mttf))
						doTriangles = false;

					if(doTriangles && !MapTile.isDiag(mttf))
					{
						verts = 6;
						va = vertex_access_two_tri;
						polygons = 2;
						vertsperpoly = verts / polygons;
					}

					vlast = vertsperpoly-1;

					for(int p=0; p<polygons; p++)
					{
						VecMath.mkVec
						(
								vertCoords[va[p * 3] * 3] * tileToWorldSpace
								, vertCoords[va[p * 3] * 3 + 1] * fslope
								, vertCoords[va[p * 3] * 3 + 2] * tileToWorldSpace
								, v0[p]
						);
						VecMath.mkVec
						(
								vertCoords[va[p * 3 + 1] * 3] * tileToWorldSpace
								, vertCoords[va[p * 3 + 1] * 3 + 1] * fslope
								, vertCoords[va[p * 3 + 1] * 3 + 2] * tileToWorldSpace
								, v1[p]
						);
						VecMath.mkVec
						(
								vertCoords[va[p * 3 + vlast] * 3] * tileToWorldSpace
								, vertCoords[va[p * 3 + vlast] * 3 + 1] * fslope
								, vertCoords[va[p * 3 + vlast] * 3 + 2] * tileToWorldSpace
								, v3[p]
						);

						VecMath.vecSub(v1[p], v0[p], v01[p]);
						VecMath.vecSub(v3[p], v0[p], v03[p]);

						// 03 to 01 is CCW -> normal points up
						VecMath.crossProduct(v03[p], v01[p], normal[p]);
						VecMath.normalize(normal[p], normal[p]);

						centerX[p] = mapx * tileToWorldSpace + 0.5 * tileToWorldSpace;
						centerY[p] = mapy * tileToWorldSpace + 0.5 * tileToWorldSpace;
						centerZ[p] = mt.getFloorAt(MapTile.CENTER, MapTile.CENTER) * mapToWorldSpace;

						plane_d[p] = -(normal[p][0] * centerX[p] + normal[p][1] * centerZ[p] + normal[p][2] * centerY[p]);
	
						int tcindex;
						
						tcindex = (texIndices[va[p*3]] + trot) % TCMOD;
						tc_u1 = texCoordPairs[tcindex*2];
						tc_v1 = texCoordPairs[tcindex*2+1];
						x1 = mapx * tileToWorldSpace + vertCoords[va[p * 3] * 3] * tileToWorldSpace;
						y1 = fpos + vertCoords[va[p * 3] * 3 + 1] * fslope;
						z1 = mapy * tileToWorldSpace + vertCoords[va[p * 3] * 3 + 2] * tileToWorldSpace;

						tcindex = (texIndices[va[p*3 + 1]] + trot) % TCMOD;
						tc_u2 = texCoordPairs[tcindex*2];
						tc_v2 = texCoordPairs[tcindex*2+1];
						x2 = mapx * tileToWorldSpace + vertCoords[va[p * 3 + 1] * 3] * tileToWorldSpace;
						y2 = fpos + vertCoords[va[p * 3 + 1] * 3 + 1] * fslope;
						z2 = mapy * tileToWorldSpace + vertCoords[va[p * 3 + 1] * 3 + 2] * tileToWorldSpace;

						tcindex = (texIndices[va[p*3 + 2]] + trot) % TCMOD;
						tc_u3 = texCoordPairs[tcindex*2];
						tc_v3 = texCoordPairs[tcindex*2+1];
						x3 = mapx * tileToWorldSpace + vertCoords[va[p * 3 + 2] * 3] * tileToWorldSpace;
						y3 = fpos + vertCoords[va[p * 3 + 2] * 3 + 1] * fslope;
						z3 = mapy * tileToWorldSpace + vertCoords[va[p * 3 + 2] * 3 + 2] * tileToWorldSpace;

						double c1r, c1g, c1b, c2r, c2g, c2b, c3r, c3g, c3b, c4r, c4g, c4b;
						
						if(map.isCyberspace())
						{
							MapTile mt1 = map.getTile((int)(x1 * worldToTileSpace), (map.getVertSize() - 1) - (int)(z1 * worldToTileSpace));
							MapTile mt2 = map.getTile((int)(x2 * worldToTileSpace), (map.getVertSize() - 1) - (int)(z2 * worldToTileSpace));
							MapTile mt3 = map.getTile((int)(x3 * worldToTileSpace), (map.getVertSize() - 1) - (int)(z3 * worldToTileSpace));
							
							int palindex1 = mt1.getCyberColorFloor();
							int palindex2 = mt2.getCyberColorFloor();
							int palindex3 = mt3.getCyberColorFloor();
							
							c1r = (((int)palette[palindex1 * 3])&0xFF) / 255.0; 
							c1g = (((int)palette[palindex1 * 3 + 1])&0xFF) / 255.0; 
							c1b = (((int)palette[palindex1 * 3 + 2])&0xFF) / 255.0;
							
							// TODO gather other tiles
							
							c2r = (((int)palette[palindex2 * 3])&0xFF) / 255.0; 
							c2g = (((int)palette[palindex2 * 3 + 1])&0xFF) / 255.0; 
							c2b = (((int)palette[palindex2 * 3 + 2])&0xFF) / 255.0;

							c3r = (((int)palette[palindex3 * 3])&0xFF) / 255.0; 
							c3g = (((int)palette[palindex3 * 3 + 1])&0xFF) / 255.0; 
							c3b = (((int)palette[palindex3 * 3 + 2])&0xFF) / 255.0;
						} else {
							c1r = 1.0 - clamp(darkness_at(x1, z1, y1));
							c1g = c1r;
							c1b = c1r;
							c2r = 1.0 - clamp(darkness_at(x2, z2, y2));
							c2g = c2r;
							c2b = c2r;
							c3r = 1.0 - clamp(darkness_at(x3, z3, y3));
							c3g = c3r;
							c3b = c3r;
						}

						if(!doTriangles)
						{
							tcindex = (texIndices[va[p*3 + 3]] + trot) % TCMOD;
							tc_u4 = texCoordPairs[tcindex*2];
							tc_v4 = texCoordPairs[tcindex*2+1];
							x4 = mapx * tileToWorldSpace + vertCoords[va[p * 3 + 3] * 3] * tileToWorldSpace;
							y4 = fpos + vertCoords[va[p * 3 + 3] * 3 + 1] * fslope;
							z4 = mapy * tileToWorldSpace + vertCoords[va[p * 3 + 3] * 3 + 2] * tileToWorldSpace;

							if(map.isCyberspace()) {
								MapTile mt4 = map.getTile((int)(x4 * worldToTileSpace), (map.getVertSize() - 1) - (int)(z4 * worldToTileSpace));
								
								int palindex4 = mt4.getCyberColorFloor();
								
								c4r = (((int)palette[palindex4 * 3])&0xFF) / 255.0; 
								c4g = (((int)palette[palindex4 * 3 + 1])&0xFF) / 255.0; 
								c4b = (((int)palette[palindex4 * 3 + 2])&0xFF) / 255.0;
							} else {
								c4r = 1.0 - clamp(darkness_at(x4, z4, y4));
								c4g = c4r;
								c4b = c4r;
							}
							

							tp.addQuad(tc_u1, tc_v1, x1, y1, z1, c1r, c1g, c1b, tc_u2, tc_v2, x2, y2, z2, c2r, c2g, c2b, tc_u3, tc_v3, x3, y3, z3, c3r, c3g, c3b, tc_u4, tc_v4, x4, y4, z4, c4r, c4g, c4b, normal[p][0], normal[p][1], normal[p][2], plane_d[p], texid, PolygonType.PT_Floor);
							tex_quads[texid]++;
						}
						else
						{
							tp.addTri(tc_u1, tc_v1, x1, y1, z1, c1r, c1g, c1b, tc_u2, tc_v2, x2, y2, z2, c2r, c2g, c2b, tc_u3, tc_v3, x3, y3, z3, c3r, c3g, c3b, normal[p][0], normal[p][1], normal[p][2], plane_d[p], texid, PolygonType.PT_Floor);
							tex_tris[texid]++;
						}
					} 
					
					// now get ceiling vertices
					vertCoords = vertMap.get(mttc);
					texIndices = texcoordMap.get(mttc);

					verts = vertCoords.length / 3;
					polygons = 1;
					vertsperpoly = verts / polygons;
					
					texid = map.isCyberspace() ? CYBER_TEX : mt.getTextureIndexCeiling();

					trot = TCMOD - mt.getCRot();

					va = vertex_access_id;

					doTriangles = true;

					if(MapTile.isOpen(mttc) || MapTile.isSlope(mttc))
						doTriangles = false;

					if(doTriangles && !MapTile.isDiag(mttf))
					{
						verts = 6;
						polygons = 2;
						vertsperpoly = verts / polygons;
						va = vertex_access_two_tri;
					}

					vlast = vertsperpoly-1;

					for(int p=0; p<polygons; p++)
					{
						VecMath.mkVec
						(
								vertCoords[va[p * 3] * 3] * tileToWorldSpace
								, vertCoords[va[p * 3] * 3 + 1] * cslope
								, vertCoords[va[p * 3] * 3 + 2] * tileToWorldSpace
								, v0[p]
						);
						VecMath.mkVec
						(
								vertCoords[va[p * 3 + 1] * 3] * tileToWorldSpace
								, vertCoords[va[p * 3 + 1] * 3 + 1] * cslope
								, vertCoords[va[p * 3 + 1] * 3 + 2] * tileToWorldSpace
								, v1[p]
						);
						VecMath.mkVec
						(
								vertCoords[va[p * 3 + vlast] * 3] * tileToWorldSpace
								, vertCoords[va[p * 3 + vlast] * 3 + 1] * cslope
								, vertCoords[va[p * 3 + vlast] * 3 + 2] * tileToWorldSpace
								, v3[p]
						);

						VecMath.vecSub(v1[p], v0[p], v01[p]);
						VecMath.vecSub(v3[p], v0[p], v03[p]);

						// ceilings are reverted floors, so clockwise crossproduct
						// points up in this case
						// 01 to 03 is CW -> normal points up
						VecMath.crossProduct(v01[p], v03[p], normal[p]);
						VecMath.normalize(normal[p], normal[p]);

						centerX[p] = mapx * tileToWorldSpace + 0.5 * tileToWorldSpace;
						centerY[p] = mapy * tileToWorldSpace + 0.5 * tileToWorldSpace;
						centerZ[p] = mt.getCeilingAt(MapTile.CENTER, MapTile.CENTER) * mapToWorldSpace;

						plane_d[p] = -(normal[p][0] * centerX[p] + normal[p][1] * centerZ[p] + normal[p][2] * centerY[p]);

						int tcindex;
						
						tcindex = (texIndices[va[p*3]] + trot) % TCMOD;
						tc_u1 = texCoordPairs[tcindex*2];
						tc_v1 = texCoordPairs[tcindex*2+1];
						x1 = mapx * tileToWorldSpace + vertCoords[va[p * 3] * 3] * tileToWorldSpace;
						y1 = cpos + vertCoords[va[p * 3] * 3 + 1] * cslope;
						z1 = mapy * tileToWorldSpace + vertCoords[va[p * 3] * 3 + 2] * tileToWorldSpace;

						tcindex = (texIndices[va[p*3 + 1]] + trot) % TCMOD;
						tc_u2 = texCoordPairs[tcindex*2];
						tc_v2 = texCoordPairs[tcindex*2+1];
						x2 = mapx * tileToWorldSpace + vertCoords[va[p * 3 + 1] * 3] * tileToWorldSpace;
						y2 = cpos + vertCoords[va[p * 3 + 1] * 3 + 1] * cslope;
						z2 = mapy * tileToWorldSpace + vertCoords[va[p * 3 + 1] * 3 + 2] * tileToWorldSpace;

						tcindex = (texIndices[va[p*3 + 2]] + trot) % TCMOD;
						tc_u3 = texCoordPairs[tcindex*2];
						tc_v3 = texCoordPairs[tcindex*2+1];
						x3 = mapx * tileToWorldSpace + vertCoords[va[p * 3 + 2] * 3] * tileToWorldSpace;
						y3 = cpos + vertCoords[va[p * 3 + 2] * 3 + 1] * cslope;
						z3 = mapy * tileToWorldSpace + vertCoords[va[p * 3 + 2] * 3 + 2] * tileToWorldSpace;
						
						double c1r, c1g, c1b, c2r, c2g, c2b, c3r, c3g, c3b, c4r, c4g, c4b;
						
						if(map.isCyberspace())
						{
							MapTile mt1 = map.getTile((int)(x1 * worldToTileSpace), (map.getVertSize() - 1) - (int)(z1 * worldToTileSpace));
							MapTile mt2 = map.getTile((int)(x2 * worldToTileSpace), (map.getVertSize() - 1) - (int)(z2 * worldToTileSpace));
							MapTile mt3 = map.getTile((int)(x3 * worldToTileSpace), (map.getVertSize() - 1) - (int)(z3 * worldToTileSpace));
							
							int palindex1 = mt1.getCyberColorCeiling();
							int palindex2 = mt2.getCyberColorCeiling();
							int palindex3 = mt3.getCyberColorCeiling();
							
							c1r = (((int)palette[palindex1 * 3])&0xFF) / 255.0; 
							c1g = (((int)palette[palindex1 * 3 + 1])&0xFF) / 255.0; 
							c1b = (((int)palette[palindex1 * 3 + 2])&0xFF) / 255.0;
							
							// TODO gather other tiles
							
							c2r = (((int)palette[palindex2 * 3])&0xFF) / 255.0; 
							c2g = (((int)palette[palindex2 * 3 + 1])&0xFF) / 255.0; 
							c2b = (((int)palette[palindex2 * 3 + 2])&0xFF) / 255.0;

							c3r = (((int)palette[palindex3 * 3])&0xFF) / 255.0; 
							c3g = (((int)palette[palindex3 * 3 + 1])&0xFF) / 255.0; 
							c3b = (((int)palette[palindex3 * 3 + 2])&0xFF) / 255.0;
						} else {
							c1r = 1.0 - clamp(darkness_at(x1, z1, y1));
							c1g = c1r;
							c1b = c1r;
							c2r = 1.0 - clamp(darkness_at(x2, z2, y2));
							c2g = c2r;
							c2b = c2r;
							c3r = 1.0 - clamp(darkness_at(x3, z3, y3));
							c3g = c3r;
							c3b = c3r;
						}
						
						if(!doTriangles)
						{
							tcindex = (texIndices[va[p*3 + 3]] + trot) % TCMOD;
							tc_u4 = texCoordPairs[tcindex*2];
							tc_v4 = texCoordPairs[tcindex*2+1];
							x4 = mapx * tileToWorldSpace + vertCoords[va[p * 3 + 3] * 3] * tileToWorldSpace;
							y4 = cpos + vertCoords[va[p * 3 + 3] * 3 + 1] * cslope;
							z4 = mapy * tileToWorldSpace + vertCoords[va[p * 3 + 3] * 3 + 2] * tileToWorldSpace;

							if(map.isCyberspace()) {
								MapTile mt4 = map.getTile((int)(x4 * worldToTileSpace), (map.getVertSize() - 1) - (int)(z4 * worldToTileSpace));
								
								int palindex4 = mt4.getCyberColorCeiling();
								
								c4r = (((int)palette[palindex4 * 3])&0xFF) / 255.0; 
								c4g = (((int)palette[palindex4 * 3 + 1])&0xFF) / 255.0; 
								c4b = (((int)palette[palindex4 * 3 + 2])&0xFF) / 255.0;
							} else {
								c4r = 1.0 - clamp(darkness_at(x4, z4, y4));
								c4g = c4r;
								c4b = c4r;
							}

							tp.addQuad(tc_u1, tc_v1, x1, y1, z1, c1r, c1g, c1b, tc_u2, tc_v2, x2, y2, z2, c2r, c2g, c2b, tc_u3, tc_v3, x3, y3, z3, c3r, c3g, c3b, tc_u4, tc_v4, x4, y4, z4, c4r, c4g, c4b, normal[p][0], normal[p][1], normal[p][2], plane_d[p], texid, PolygonType.PT_Floor);
							tex_quads[texid]++;
						}
						else
						{
							tp.addTri(tc_u1, tc_v1, x1, y1, z1, c1r, c1g, c1b, tc_u2, tc_v2, x2, y2, z2, c2r, c2g, c2b, tc_u3, tc_v3, x3, y3, z3, c3r, c3g, c3b, normal[p][0], normal[p][1], normal[p][2], plane_d[p], texid, PolygonType.PT_Floor);
							tex_tris[texid]++;
						}
					}

					boolean checkWallN = true;
					boolean checkWallS = true;
					boolean checkWallW = true;
					boolean checkWallE = true;

					double fh1, fh2, ch1, ch2;
					double ofh1, ofh2, och1, och2;
					int toffs;
					
					texid = map.isCyberspace() ? CYBER_TEX : mt.getTextureIndexWall();
					
					toffs = mt.getTextureOffset();
					
					int pm_offs = mapy * map.getHorzSize() + mapx;
					
					// set all portals on
					portal_map[pm_offs] = (P_NORTH | P_WEST | P_SOUTH | P_EAST);
					
					if(mttf == MapTile.Type.DiagonalNE)
					{
						checkWallW = false;
						checkWallS = false;
						createWall(DIR_SW, tp, mt, texid, toffs, mapx, mapy, fpos, fpos, cpos, cpos);
						tex_quads[texid]++;
						// remove portals for SOUTH and WEST
						portal_map[pm_offs] &= (P_NORTH | P_EAST);
					}
					if(mttf == MapTile.Type.DiagonalNW)
					{
						checkWallE = false;
						checkWallS = false;
						createWall(DIR_SE, tp, mt, texid, toffs, mapx, mapy, fpos, fpos, cpos, cpos);
						tex_quads[texid]++;
						portal_map[pm_offs] &= (P_NORTH | P_WEST);
					}
					if(mttf == MapTile.Type.DiagonalSE)
					{
						checkWallW = false;
						checkWallN = false;
						createWall(DIR_NW, tp, mt, texid, toffs, mapx, mapy, fpos, fpos, cpos, cpos);
						tex_quads[texid]++;
						portal_map[pm_offs] &= (P_SOUTH | P_EAST);
					}
					if(mttf == MapTile.Type.DiagonalSW)
					{
						checkWallE = false;
						checkWallN = false;
						createWall(DIR_NE, tp, mt, texid, toffs, mapx, mapy, fpos, fpos, cpos, cpos);
						tex_quads[texid]++;
						portal_map[pm_offs] &= (P_SOUTH | P_WEST);
					}
					
					if(checkWallN)
					{
					// Y is reversed
					MapTile north = map.getTile(mapx, my+1);

					texid = map.isCyberspace() ? CYBER_TEX : mt.getTextureIdWallNorth();
					toffs = mt.getTextureOffset();
					
					fh1 = mt.getFloorAt(MapTile.WEST, MapTile.NORTH) * mapToWorldSpace;
					fh2 = mt.getFloorAt(MapTile.EAST, MapTile.NORTH) * mapToWorldSpace;
					ch1 = mt.getCeilingAt(MapTile.WEST, MapTile.NORTH) * mapToWorldSpace;
					ch2 = mt.getCeilingAt(MapTile.EAST, MapTile.NORTH) * mapToWorldSpace;

					if
					(
						   north==null
						|| north.getType()==MapTile.Type.Solid
						|| north.getType()==MapTile.Type.DiagonalNE
						|| north.getType()==MapTile.Type.DiagonalNW
						|| north.getFloor() >= mt.getCeiling()
						|| north.getCeiling() <= mt.getFloor()
					)
					{
						createWall(DIR_N, tp, mt, texid, toffs, mapx, mapy, fh1, fh2, ch1, ch2);
						tex_quads[texid]++;
						// remove NORTH portal
						portal_map[pm_offs] &= (P_SOUTH | P_EAST | P_WEST);
					}
					else
					{
						ofh1 = north.getFloorAt(MapTile.WEST, MapTile.SOUTH) * mapToWorldSpace;
						ofh2 = north.getFloorAt(MapTile.EAST, MapTile.SOUTH) * mapToWorldSpace;

						och1 = north.getCeilingAt(MapTile.WEST, MapTile.SOUTH) * mapToWorldSpace;
						och2 = north.getCeilingAt(MapTile.EAST, MapTile.SOUTH) * mapToWorldSpace;

						if(ofh1 > fh1 || ofh2 > fh2)
						{

							createWall(DIR_N, tp, mt, texid, toffs, mapx, mapy, fh1, fh2, ofh1, ofh2);
							tex_quads[texid]++;
						}
						
						if(och1 < ch1 || och2 < ch2)
						{
							createWall(DIR_N, tp, mt, texid, toffs, mapx, mapy, och1, och2, ch1, ch2);
							tex_quads[texid]++;
						}
					}
					}
					
					if(checkWallW)
					{
					// Y is reversed
					MapTile west = map.getTile(mapx-1, my);
					
					texid = map.isCyberspace() ? CYBER_TEX : mt.getTextureIdWallWest();
					toffs = mt.getTextureOffset();

					fh1 = mt.getFloorAt(MapTile.WEST, MapTile.SOUTH) * mapToWorldSpace;
					fh2 = mt.getFloorAt(MapTile.WEST, MapTile.NORTH) * mapToWorldSpace;
					ch1 = mt.getCeilingAt(MapTile.WEST, MapTile.SOUTH) * mapToWorldSpace;
					ch2 = mt.getCeilingAt(MapTile.WEST, MapTile.NORTH) * mapToWorldSpace;

					if
					(
						   west==null
						|| west.getType()==MapTile.Type.Solid
						|| west.getType()==MapTile.Type.DiagonalSW
						|| west.getType()==MapTile.Type.DiagonalNW
						|| west.getFloor() >= mt.getCeiling()
						|| west.getCeiling() <= mt.getFloor()
					)
					{
						createWall(DIR_W, tp, mt, texid, toffs, mapx, mapy, fh1, fh2, ch1, ch2);
						tex_quads[texid]++;
						portal_map[pm_offs] &= (P_NORTH | P_SOUTH | P_EAST);
					}
					else
					{
						ofh1 = west.getFloorAt(MapTile.EAST, MapTile.SOUTH) * mapToWorldSpace;
						ofh2 = west.getFloorAt(MapTile.EAST, MapTile.NORTH) * mapToWorldSpace;

						och1 = west.getCeilingAt(MapTile.EAST, MapTile.SOUTH) * mapToWorldSpace;
						och2 = west.getCeilingAt(MapTile.EAST, MapTile.NORTH) * mapToWorldSpace;

						if(ofh1 > fh1 || ofh2 > fh2)
						{
							createWall(DIR_W, tp, mt, texid, toffs, mapx, mapy, fh1, fh2, ofh1, ofh2);
							tex_quads[texid]++;
						}

						if(och1 < ch1 || och2 < ch2)
						{
							createWall(DIR_W, tp, mt, texid, toffs, mapx, mapy, och1, och2, ch1, ch2);
							tex_quads[texid]++;
						}
					}
					}
					
					if(checkWallS)
					{
					// Y is reversed
					MapTile south = map.getTile(mapx, my-1);
					
					texid = map.isCyberspace() ? CYBER_TEX : mt.getTextureIdWallSouth();
					toffs = mt.getTextureOffset();

					fh1 = mt.getFloorAt(MapTile.EAST, MapTile.SOUTH) * mapToWorldSpace;
					fh2 = mt.getFloorAt(MapTile.WEST, MapTile.SOUTH) * mapToWorldSpace;
					ch1 = mt.getCeilingAt(MapTile.EAST, MapTile.SOUTH) * mapToWorldSpace;
					ch2 = mt.getCeilingAt(MapTile.WEST, MapTile.SOUTH) * mapToWorldSpace;

					if
					(
						   south==null 
						|| south.getType()==MapTile.Type.Solid
						|| south.getType()==MapTile.Type.DiagonalSE
						|| south.getType()==MapTile.Type.DiagonalSW
						|| south.getFloor() >= mt.getCeiling()
						|| south.getCeiling() <= mt.getFloor()
					)
					{
						createWall(DIR_S, tp, mt, texid, toffs, mapx, mapy, fh1, fh2, ch1, ch2);
						tex_quads[texid]++;
						portal_map[pm_offs] &= (P_NORTH | P_WEST | P_EAST);
					}
					else
					{
						ofh1 = south.getFloorAt(MapTile.EAST, MapTile.NORTH) * mapToWorldSpace;
						ofh2 = south.getFloorAt(MapTile.WEST, MapTile.NORTH) * mapToWorldSpace;

						och1 = south.getCeilingAt(MapTile.EAST, MapTile.NORTH) * mapToWorldSpace;
						och2 = south.getCeilingAt(MapTile.WEST, MapTile.NORTH) * mapToWorldSpace;

						if(ofh1 > fh1 || ofh2 > fh2)
						{
							createWall(DIR_S, tp, mt, texid, toffs, mapx, mapy, fh1, fh2, ofh1, ofh2);
							tex_quads[texid]++;
						}

						if(och1 < ch1 || och2 < ch2)
						{
							createWall(DIR_S, tp, mt, texid, toffs, mapx, mapy, och1, och2, ch1, ch2);
							tex_quads[texid]++;
						}
					}
					}
					
					if(checkWallE)
					{
					// Y is reversed
					MapTile east = map.getTile(mapx+1, my);

					texid = map.isCyberspace() ? CYBER_TEX : mt.getTextureIdWallEast();
					toffs = mt.getTextureOffset();

					fh1 = mt.getFloorAt(MapTile.EAST, MapTile.NORTH) * mapToWorldSpace;
					fh2 = mt.getFloorAt(MapTile.EAST, MapTile.SOUTH) * mapToWorldSpace;
					ch1 = mt.getCeilingAt(MapTile.EAST, MapTile.NORTH) * mapToWorldSpace;
					ch2 = mt.getCeilingAt(MapTile.EAST, MapTile.SOUTH) * mapToWorldSpace;

					if
					(
						   east==null
						|| east.getType()==MapTile.Type.Solid
						|| east.getType()==MapTile.Type.DiagonalSE
						|| east.getType()==MapTile.Type.DiagonalNE
						|| east.getFloor() >= mt.getCeiling()
						|| east.getCeiling() <= mt.getFloor()
					)
					{
						createWall(DIR_E, tp, mt, texid, toffs, mapx, mapy, fh1, fh2, ch1, ch2);
						tex_quads[texid]++;
						portal_map[pm_offs] &= (P_NORTH | P_WEST | P_SOUTH);
					}
					else
					{
						ofh1 = east.getFloorAt(MapTile.WEST, MapTile.NORTH) * mapToWorldSpace;
						ofh2 = east.getFloorAt(MapTile.WEST, MapTile.SOUTH) * mapToWorldSpace;

						och1 = east.getCeilingAt(MapTile.WEST, MapTile.NORTH) * mapToWorldSpace;
						och2 = east.getCeilingAt(MapTile.WEST, MapTile.SOUTH) * mapToWorldSpace;

						if(ofh1 > fh1 || ofh2 > fh2)
						{
							createWall(DIR_E, tp, mt, texid, toffs, mapx, mapy, fh1, fh2, ofh1, ofh2);
							tex_quads[texid]++;
						}

						if(och1 < ch1 || och2 < ch2)
						{
							createWall(DIR_E, tp, mt, texid, toffs, mapx, mapy, och1, och2, ch1, ch2);
							tex_quads[texid]++;
						}
					}
					}
				}
			}
		}
	}
	
	private double clamp(double d)
	{
		return (d<0.0) ? 0.0 : ((d>1.0) ? 1.0 : d);
	}
	
	private void createWall(int dir, TilePolys tp, MapTile mt, int tex, int toffs, int mapx, int mapy, double l1, double l2, double h1, double h2)
	{
		double []
		        v0 = new double [3]
		      , v1 = new double [3]
		      , v3 = new double [3]
		      , v01 = new double [3]
		      , v03 = new double [3]
		      , normal = new double [3]
		      ;
		
		double plane_d;
                          ;
		double hmax = Math.max(h1, h2);
		
		double offs = ((texBase - hmax) * worldToTextureSpace) + toffs * mapToTextureSpace;
		
		double texl1coord = offs + (hmax - l1) * worldToTextureSpace;
		double texl2coord = offs + (hmax - l2) * worldToTextureSpace;
		double texh1coord = offs + (hmax - h1) * worldToTextureSpace;
		double texh2coord = offs + (hmax - h2) * worldToTextureSpace;
		
		double x1 = 0.0;
		double x2 = 1.0;
		double y1 = 0.0;
		double y2 = 1.0;
		
		switch(dir)
		{
		case DIR_N:
			y1 = y2 = mapy * tileToWorldSpace;
			x1 = mapx * tileToWorldSpace;
			x2 = x1 + tileToWorldSpace;
			break;
		case DIR_S:
			y1 = y2 = (mapy + 1) * tileToWorldSpace;
			x1 = (mapx + 1) * tileToWorldSpace;
			x2 = x1 - tileToWorldSpace;
			break;
		case DIR_W:
			x1 = x2 = mapx * tileToWorldSpace;
			y1 = (mapy + 1) * tileToWorldSpace;
			y2 = y1 - tileToWorldSpace;
			break;
		case DIR_E:
			x1 = x2 = (mapx + 1) * tileToWorldSpace;
			y1 = mapy * tileToWorldSpace;
			y2 = y1 + tileToWorldSpace;
			break;
		case DIR_NW:
			y1 = (mapy + 1) * tileToWorldSpace;
			y2 = y1 - tileToWorldSpace;
			x1 = mapx * tileToWorldSpace;
			x2 = x1 + tileToWorldSpace;
			break;
		case DIR_NE:
			y1 = mapy * tileToWorldSpace;
			y2 = y1 + tileToWorldSpace;
			x1 = mapx * tileToWorldSpace;
			x2 = x1 + tileToWorldSpace;
			break;
		case DIR_SW:
			y1 = (mapy + 1) * tileToWorldSpace;
			y2 = y1 - tileToWorldSpace;
			x1 = (mapx + 1) * tileToWorldSpace;
			x2 = x1 - tileToWorldSpace;
			break;
		case DIR_SE:
			y1 = mapy * tileToWorldSpace;
			y2 = y1 + tileToWorldSpace;
			x1 = (mapx + 1) * tileToWorldSpace;
			x2 = x1 - tileToWorldSpace;
			break;
		default:
			System.err.println("DIR!");
		}
		
		double lmin = Math.min(l1, l2);
		
		VecMath.mkVec(x1, l1, y1, v0);
		VecMath.mkVec(x1, h1, y1, v1);
		VecMath.mkVec(x2, l2, y2, v3);
		
		VecMath.vecSub(v1, v0, v01);
		VecMath.vecSub(v3, v0, v03);

		// 03 to 01 is CCW -> normal points up
		
		VecMath.normalize(VecMath.crossProduct(v03, v01, normal), normal);

		double centerX = (x2 - x1) / 2.0 + x1;
		double centerY = (y2 - y1) / 2.0 + y1;
		double centerZ = (hmax - lmin) / 2.0 + lmin;
		
		plane_d = -(normal[0] * centerX + normal[1] * centerZ + normal[2] * centerY);
		
		double c1r, c1g, c1b, c2r, c2g, c2b, c3r, c3g, c3b, c4r, c4g, c4b;
		
		if(map.isCyberspace())
		{
			MapTile t1 = map.getTile((int)(x1 * worldToTileSpace), (int)(y1 * worldToTileSpace));
			MapTile t2 = map.getTile((int)(x2 * worldToTileSpace), (int)(y2 * worldToTileSpace));
			int palc1index = t1.getCyberColorCeiling();
			int palf1index = t1.getCyberColorFloor();
			int palc2index = t2.getCyberColorCeiling();
			int palf2index = t2.getCyberColorFloor();
			
			c1r = (((int)palette[palf1index * 3])&0xFF) / 255.0; 
			c1g = (((int)palette[palf1index * 3 + 1])&0xFF) / 255.0; 
			c1b = (((int)palette[palf1index * 3 + 2])&0xFF) / 255.0;
			
			c2r = (((int)palette[palc1index * 3])&0xFF) / 255.0;
			c2g = (((int)palette[palc1index * 3 + 1])&0xFF) / 255.0;
			c2b = (((int)palette[palc1index * 3 + 2])&0xFF) / 255.0;

			c3r = (((int)palette[palc2index * 3])&0xFF) / 255.0; 
			c3g = (((int)palette[palc2index * 3 + 1])&0xFF) / 255.0; 
			c3b = (((int)palette[palc2index * 3 + 2])&0xFF) / 255.0;

			c4r = (((int)palette[palf2index * 3])&0xFF) / 255.0; 
			c4g = (((int)palette[palf2index * 3 + 1])&0xFF) / 255.0; 
			c4b = (((int)palette[palf2index * 3 + 2])&0xFF) / 255.0;

		} else {
			c1r = 1.0 - clamp(darkness_at(x1, y1, l1));
			c2r = 1.0 - clamp(darkness_at(x1, y1, h1));
			c3r = 1.0 - clamp(darkness_at(x2, y2, h2));
			c4r = 1.0 - clamp(darkness_at(x2, y2, l2));
			
			c1g = c1r; c1b = c1r;
			c2g = c2r; c2b = c2r;
			c3g = c3r; c3b = c3r;
			c4g = c4r; c4b = c4r;
		}
		
		tp.addQuad
		(
			  0.0, texl1coord, x1, l1, y1, c1r, c1g, c1b
			, 0.0, texh1coord, x1, h1, y1, c2r, c2g, c2b
			, 1.0, texh2coord, x2, h2, y2, c3r, c3g, c3b
			, 1.0, texl2coord, x2, l2, y2, c4r, c4g, c4b
			, normal[0], normal[1], normal[2], plane_d
			, tex
			, PolygonType.PT_Wall
		);
	}

	public void sortPolygons()
	{
		quad_vertex = new double [quads_total * 4 * 3];
		quad_tc = new double [quads_total * 4 * 2];
		quad_plane = new double [quads_total * 4];
		quad_dark = new double [quads_total * 4 * 3];
		quad_full_bright = new double [quad_dark.length];
		
		quad_pt = new PolygonType [quads_total];
		
		quad_tileoffs = new int [quads_total];
		
		Arrays.fill(quad_full_bright, 1.0);
		
		tri_vertex = new double [tris_total * 3 * 3];
		tri_tc = new double [tris_total * 3 * 2];
		tri_plane = new double [tris_total * 4];
		tri_dark = new double [tris_total * 3 * 3];
		tri_full_bright = new double [tri_dark.length];

		tri_pt = new PolygonType [tris_total];

		tri_tileoffs = new int [tris_total];

		Arrays.fill(tri_full_bright, 1.0);
		
		int qoffs_cur = 0, toffs_cur = 0;

		for(int t=0; t< (map.isCyberspace() ? 1 : textures.length); t++)
		{
			int tex = t;
			tex_quad_offset[t] = qoffs_cur;
			tex_tri_offset[t] = toffs_cur;
			
			for(int mapy = 0; mapy < map.getVertSize(); mapy++)
			{
				for(int mapx = 0; mapx < map.getHorzSize(); mapx++)
				{
					int tileoffs = mapy * map.getVertSize() + mapx;
					
					if(polygons[mapx][mapy]!=null)
					{
						has_drawable[tileoffs] = true;
						
						TilePolys tp = polygons[mapx][mapy];
						
						for(int qi=0; qi<tp.num_quads; qi++)
						{
							if(!map.isCyberspace() && tp.quad_textures[qi] != tex)
								continue;
							
							System.arraycopy(tp.quad, qi * 4 * 3, quad_vertex, qoffs_cur * 4 * 3, 4 * 3);
							System.arraycopy(tp.quad_tc, qi * 4 * 2, quad_tc, qoffs_cur * 4 * 2, 4 * 2);
							System.arraycopy(tp.quad_plane, qi * 4, quad_plane, qoffs_cur * 4, 4);
							System.arraycopy(tp.quad_dark, qi * 4 * 3, quad_dark, qoffs_cur * 4 * 3, 4 * 3);
							
							quad_pt[qoffs_cur] = tp.q_type[qi];
							
							quad_tileoffs[qoffs_cur] = tileoffs;
							
							qoffs_cur++;
						}
						
						for(int ti=0; ti<tp.num_tris; ti++)
						{
							if(!map.isCyberspace() && tp.tri_textures[ti] != tex)
								continue;
							
							System.arraycopy(tp.tri, ti * 3 * 3, tri_vertex, toffs_cur * 3 * 3, 3 * 3);
							System.arraycopy(tp.tri_tc, ti * 3 * 2, tri_tc, toffs_cur * 3 * 2, 3 * 2);
							System.arraycopy(tp.tri_plane, ti * 4, tri_plane, toffs_cur * 4, 4);
							System.arraycopy(tp.tri_dark, ti * 3 * 3, tri_dark, toffs_cur * 3 * 3, 3 * 3);
							
							tri_pt[toffs_cur] = tp.t_type[ti];
							
							tri_tileoffs[toffs_cur] = tileoffs;
							
							toffs_cur++;
						}
					}
				}
			}
		}
	}
	
	int TLI = 0;
	int TRI = 1;
	int BLI = 2;
	int BRI = 3;
	
	private CenteredTexture prepareSpriteCoordinates(double [] coords, int objectIndex) {
		if(object_class[objectIndex]==SSObject.ObjectClass.DoorsAndGratings)
			return null;
		
		CenteredTexture tex = objectSprites[objectIndex];
		
		if(object_class[objectIndex]==SSObject.ObjectClass.Critters) {
			tex = critterTextures.get(object_id[objectIndex]-SSCritter.BASE_OBJECT_ID);
		}

		if(tex==null)
			return null;
		
		if(object_properties[objectIndex].renderType == SSObject.RenderType.OrientedSurface) {
			if(prepareSpriteCoordinatesOriented(tex, coords, objectIndex))
				return tex;
		} else {
			if(prepareSpriteCoordinatesBillboard(tex, coords, objectIndex))
				return tex;
		}
		
		return null;
	}

	private boolean prepareSpriteCoordinatesBillboard(CenteredTexture spriteTex, double [] coords, int objectIndex) {
		double angy = -(env.cam_rot[VecMath.IDX_Y] / 180.0) * Math.PI;
		double angx = -(env.cam_rot[VecMath.IDX_X] / 180.0) * Math.PI;

		int sw = spriteTex.texture.getImageWidth();
		int sh = spriteTex.texture.getImageHeight();
		
		// TODO: scale = 4.0, more ?
		// 2.5, 2.54 for zoffs taken from object.c...
		// whats the magic here ?
		double sprite_scale = 2.5;
		double offset_scale = 2.54;
		int object_scale = object_properties[objectIndex].scale;
		
		if( object_class[objectIndex] == SSObject.ObjectClass.SceneryAndFixtures && (object_properties[objectIndex].flags & SSObject.COP_FLAG_USE_ZOFFS) == 0) {
			object_scale = 0; // some use it, some don't... flag is guessed...
		}
		
		double wscale = (sw * sprite_scale) * mapToWorldSpace; 
		double aspect = (sh / (double)sw) *  wscale;
		
		double [] opos = new double [3];
		
		// this is the global z offset from the properties
		double yoffset = (object_scale * offset_scale) * mapToWorldSpace;
		
		// this is the (y) center point in the image for billboarding
		// its main use is to make lamps stay at their position when looking up / down...
		double billboardy = (sh - spriteTex.center_vert) * sprite_scale * mapToWorldSpace;
		
		
		coords[TLI*3+ VecMath.IDX_X] = -0.5 * wscale;
		coords[TLI*3+ VecMath.IDX_Z] =  0.0;
		coords[TLI*3+ VecMath.IDX_Y] =  aspect - billboardy;

		coords[TRI*3+ VecMath.IDX_X] =  0.5 * wscale;
		coords[TRI*3+ VecMath.IDX_Z] =  0.0;
		coords[TRI*3+ VecMath.IDX_Y] =  aspect - billboardy;

		coords[BRI*3+ VecMath.IDX_X] =  0.5 * wscale;
		coords[BRI*3+ VecMath.IDX_Z] =  0.0;
		coords[BRI*3+ VecMath.IDX_Y] =  0.0 - billboardy;

		coords[BLI*3+ VecMath.IDX_X] =  -0.5 * wscale;
		coords[BLI*3+ VecMath.IDX_Z] =  0.0;
		coords[BLI*3+ VecMath.IDX_Y] =  0.0 - billboardy;
		
		double [] v = {0, 0, 0};
		for(int ci=0; ci<4; ci++) {

			v[VecMath.IDX_X] = coords[ci*3+ VecMath.IDX_X];
			v[VecMath.IDX_Y] = coords[ci*3+ VecMath.IDX_Y];
			v[VecMath.IDX_Z] = coords[ci*3+ VecMath.IDX_Z];

			VecMath.rotateX(v, angx, v);

			VecMath.rotateY(v, angy, v);
			
			coords[ci*3 ] =  v[0];
			coords[ci*3+1] = v[1];
			coords[ci*3+2] = v[2];
		}
		
		opos[VecMath.IDX_X] = object_vertex[objectIndex*6];
		opos[VecMath.IDX_Y] = (object_vertex[objectIndex*6+1] - yoffset);
		opos[VecMath.IDX_Z] = object_vertex[objectIndex*6+2];
		
		for(int ci=0; ci<4; ci++) {
			v[VecMath.IDX_X] = coords[ci*3+ VecMath.IDX_X];
			v[VecMath.IDX_Y] = coords[ci*3+ VecMath.IDX_Y];
			v[VecMath.IDX_Z] = coords[ci*3+ VecMath.IDX_Z];
			
			VecMath.vecAdd(v, opos, v);
			
			coords[ci*3 ] =  v[0];
			coords[ci*3+1] = v[1];
			coords[ci*3+2] = v[2];
		}
		
		return true;
	}
	
	private boolean prepareSpriteCoordinatesOriented(CenteredTexture spriteTex, double [] coords, int objectIndex) {
		int sw = spriteTex.texture.getImageWidth();
		int sh = spriteTex.texture.getImageHeight();
		
		double [] opos = new double [3];
		
		double wscale = sw * 4 * mapToWorldSpace;
		double aspect = (sh / (double)sw) *  wscale;

		double z_add = 1.0/128.0;
		
		coords[TLI*3+ VecMath.IDX_X] = -0.5 * wscale;
		coords[TLI*3+ VecMath.IDX_Z] =  z_add;
		coords[TLI*3+ VecMath.IDX_Y] =  aspect/2.0;

		coords[TRI*3+ VecMath.IDX_X] =  0.5 * wscale;
		coords[TRI*3+ VecMath.IDX_Z] =  z_add;
		coords[TRI*3+ VecMath.IDX_Y] =  aspect/2.0;

		coords[BRI*3+ VecMath.IDX_X] =  0.5 * wscale;
		coords[BRI*3+ VecMath.IDX_Z] =  z_add;
		coords[BRI*3+ VecMath.IDX_Y] =  -aspect/2.0;

		coords[BLI*3+ VecMath.IDX_X] =  -0.5 * wscale;
		coords[BLI*3+ VecMath.IDX_Z] =  z_add;
		coords[BLI*3+ VecMath.IDX_Y] =  -aspect/2.0;

		double rotx, roty, rotz = 0;


		rotx = object_rotation[objectIndex * 3];
		roty = object_rotation[objectIndex * 3 + 1];
		rotz = object_rotation[objectIndex * 3 + 2];


		double [] v = {0, 0, 0};
		for(int ci=0; ci<4; ci++) {

			v[VecMath.IDX_X] = coords[ci*3+ VecMath.IDX_X];
			v[VecMath.IDX_Y] = coords[ci*3+ VecMath.IDX_Y];
			v[VecMath.IDX_Z] = coords[ci*3+ VecMath.IDX_Z];


			VecMath.rotateZ(v, -rotz, v);

			VecMath.rotateX(v, rotx, v);

			VecMath.rotateY(v, roty, v);

			
			coords[ci*3 ] =  v[0];
			coords[ci*3+1] = v[1];
			coords[ci*3+2] = v[2];
		}
		
		opos[VecMath.IDX_X] = object_vertex[objectIndex*6];
		opos[VecMath.IDX_Y] = object_vertex[objectIndex*6+1];
		opos[VecMath.IDX_Z] = object_vertex[objectIndex*6+2];
		
		for(int ci=0; ci<4; ci++) {
			v[VecMath.IDX_X] = coords[ci*3+ VecMath.IDX_X];
			v[VecMath.IDX_Y] = coords[ci*3+ VecMath.IDX_Y];
			v[VecMath.IDX_Z] = coords[ci*3+ VecMath.IDX_Z];
			
			VecMath.vecAdd(v, opos, v);
			
			coords[ci*3 ] =  v[0];
			coords[ci*3+1] = v[1];
			coords[ci*3+2] = v[2];
		}

		// check for misaligned buttons
		if(object_properties[objectIndex].renderType == SSObject.RenderType.OrientedSurface) {
			if(rotx != 0.0) {
				double add_z = 0.0;
				double otile_x_d, otile_y_d;
				int otile_x = -1, otile_y = -1;
				for(int ci=0; ci<4; ci++) {
					v[VecMath.IDX_X] = coords[ci*3+ VecMath.IDX_X];
					v[VecMath.IDX_Y] = coords[ci*3+ VecMath.IDX_Y];
					v[VecMath.IDX_Z] = coords[ci*3+ VecMath.IDX_Z];
					
					otile_x_d =v[VecMath.IDX_X] * worldToTileSpace;
					otile_y_d =v[VecMath.IDX_Z] * worldToTileSpace;
					otile_x =(int)otile_x_d;
					otile_y =(int)otile_y_d;
					MapTile mt = map.getTile(otile_x, (map.getVertSize()-1)-otile_y);
					if(mt==null)
						continue;
					double floor_pos =  mt.getFloorAt(otile_x_d - otile_x, otile_y_d - otile_y) * mapToWorldSpace;

					add_z = Math.max(add_z, floor_pos - v[VecMath.IDX_Y]);
				}
				
				if(add_z > 0.5 * mapToWorldSpace) {
					System.out.println("Re-Aligning button at " + otile_x + ", " + (63-otile_y) + " + " + add_z);
					
					object_vertex[objectIndex*6+1] += add_z + z_add;
				}
			}
		}
		
		return true;
	}
	
	private Point pickPoint = null;
	
	private class PickListener implements MouseListener {

		@Override
		public void mouseClicked(MouseEvent e) {
			pickPoint = new Point(e.getX(), e.getY());
		}

		@Override
		public void mouseEntered(MouseEvent e) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void mouseExited(MouseEvent e) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void mousePressed(MouseEvent e) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void mouseReleased(MouseEvent e) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void mouseDragged(MouseEvent arg0) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void mouseMoved(MouseEvent arg0) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void mouseWheelMoved(MouseEvent arg0) {
			// TODO Auto-generated method stub
			
		}
		
	}
	
	private PickListener pickListener = new PickListener();
	
	private void drawPickColors(GL2 gl) {
		initPickColors();
		colorQuadIndex.clear();
		colorTriIndex.clear();
		
		int currentColor;
		
		gl.glDisable(GL.GL_BLEND);
		gl.glDisable(GL.GL_TEXTURE_2D);
		
		gl.glColor4d(1.0, 1.0, 1.0, 1.0);
		
		for(int texid=0; texid < textures.length; texid++) {
			if(tex_quads[texid]==0 && tex_tris[texid]==0)
				continue;
			
			int qoffs = tex_quad_offset[texid];
			int nquads = tex_quads[texid];

			for(int qn = 0; qn < nquads; qn++) {
				int qi = qoffs + qn;
				
				if(!visible_tiles[quad_tileoffs[qi]])
					continue;
				
				double cd = quad_plane[qi * 4] * env.cam_pos[VecMath.IDX_X] + quad_plane[qi * 4 + 1] * env.cam_pos[VecMath.IDX_Y] + quad_plane[qi * 4 + 2] * env.cam_pos[VecMath.IDX_Z] + quad_plane[qi * 4 + 3];
				if(cd<0.0)
					continue;
				
				currentColor = getNextPickColor();
				
				colorQuadIndex.put(currentColor, qi);
				
				double nx = quad_plane[qi * 4];
				double ny = quad_plane[qi * 4 + 1];
				double nz = quad_plane[qi * 4 + 2];
				
				gl.glNormal3d(nx, ny, nz);
				
				gl.glColor3ub((byte)((currentColor>>16)&0xFF), (byte)((currentColor>>8)&0xFF), (byte)((currentColor)&0xFF));
				gl.glBegin(GL2.GL_QUADS);
				gl.glVertex3d(quad_vertex[4*3*qi+0+0], quad_vertex[4*3*qi+0+1], quad_vertex[4*3*qi+0+2]);
				gl.glVertex3d(quad_vertex[4*3*qi+3+0], quad_vertex[4*3*qi+3+1], quad_vertex[4*3*qi+3+2]);
				gl.glVertex3d(quad_vertex[4*3*qi+6+0], quad_vertex[4*3*qi+6+1], quad_vertex[4*3*qi+6+2]);
				gl.glVertex3d(quad_vertex[4*3*qi+9+0], quad_vertex[4*3*qi+9+1], quad_vertex[4*3*qi+9+2]);
				gl.glEnd();
			}
			
			int toffs = tex_tri_offset[texid];
			int ntris = tex_tris[texid]; 
			
			for(int tn = 0; tn < ntris; tn++) {
				int ti = toffs + tn;
				
				if(!visible_tiles[tri_tileoffs[ti]])
					continue;
				
				double cd = tri_plane[ti * 4] * env.cam_pos[VecMath.IDX_X] + tri_plane[ti * 4 + 1] * env.cam_pos[VecMath.IDX_Y] + tri_plane[ti * 4 + 2] * env.cam_pos[VecMath.IDX_Z] + tri_plane[ti * 4 + 3];
				if(cd<0.0)
					continue;
				
				currentColor = getNextPickColor();

				colorTriIndex.put(currentColor, ti);
				
				double nx = tri_plane[ti * 4];
				double ny = tri_plane[ti * 4 + 1];
				double nz = tri_plane[ti * 4 + 2];
				
				gl.glNormal3d(nx, ny, nz);
				
				gl.glColor3ub((byte)((currentColor>>16)&0xFF), (byte)((currentColor>>8)&0xFF), (byte)((currentColor)&0xFF));
				gl.glBegin(GL.GL_TRIANGLES);
				gl.glVertex3d(tri_vertex[3*3*ti+0+0], tri_vertex[3*3*ti+0+1], tri_vertex[3*3*ti+0+2]);
				gl.glVertex3d(tri_vertex[3*3*ti+3+0], tri_vertex[3*3*ti+3+1], tri_vertex[3*3*ti+3+2]);
				gl.glVertex3d(tri_vertex[3*3*ti+6+0], tri_vertex[3*3*ti+6+1], tri_vertex[3*3*ti+6+2]);
				gl.glEnd();
			}
		}
	}
	
	private void processPickQuery(GLAutoDrawable drawable) {
		GL2 gl = drawable.getGL().getGL2();
		drawPickColors(gl);
		if(pickPoint!=null) {
		ByteBuffer pixelBuffer = Buffers.newDirectByteBuffer(16*3);

		int py = drawable.getSurfaceHeight() - pickPoint.y;

		gl.glReadBuffer(GL.GL_BACK);
		gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
		gl.glReadPixels(pickPoint.x, py, 1, 1, GL.GL_RGB, GL.GL_UNSIGNED_BYTE, pixelBuffer);

		pixelBuffer.rewind();
		byte r = pixelBuffer.get();
		byte g = pixelBuffer.get();
		byte b = pixelBuffer.get();
		int pixel = ((((int)r)<<16)&0xFF0000) | ((((int)g)<<8)&0xFF00) | ((((int)b))&0xFF);

		pickPoint = null;

		Integer quadIndex = colorQuadIndex.get(pixel);
		Integer triIndex = colorTriIndex.get(pixel);

		System.out.println(String.format("PixelColor: %06X QI %d TI %d", pixel, quadIndex==null?-1:quadIndex, triIndex==null?-1:triIndex));
		
		int pickTexIndex = -1;
		PolygonType pt = null;
		if(quadIndex != null) {
			pt = quad_pt[quadIndex];
			pickTexIndex = 0;
			
			while(pickTexIndex < tex_quad_offset.length && tex_quad_offset[pickTexIndex] < quadIndex)
				pickTexIndex++;
			if(pickTexIndex >= tex_quad_offset.length || tex_quad_offset[pickTexIndex] > quadIndex)
				pickTexIndex--;
		}
		if(triIndex != null) {
			pt = tri_pt[triIndex];
			pickTexIndex = 0;
			
			while(pickTexIndex < tex_tri_offset.length && tex_tri_offset[pickTexIndex] < triIndex)
				pickTexIndex++;
			if(pickTexIndex >= tex_tri_offset.length || tex_tri_offset[pickTexIndex] > triIndex)
				pickTexIndex--;
		}
		
		if(pickTexIndex!=-1) {
			TextureProperties.TextureProperty tp = textureProperties.getPropertyFor(texids[pickTexIndex]);
			if(tp!=null) {
				System.out.println(String.format("TexID %d: %s %s", texids[pickTexIndex], pt.toString(), tp.toString()));
			}
		}
		
		}
		
		if(!cheatCodes.activeCheat(cheatOnlyPick))
			gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
	}
	
	private void drawTilePolygonsCyber(GLAutoDrawable drawable, double [] q_dark, double [] t_dark, DoubleBuffer q_cb, DoubleBuffer t_cb) {
		GL2 gl = drawable.getGL().getGL2();
		
		gl.glColor4d(1.0, 1.0, 1.0, 1.0);
		gl.glActiveTexture(GL.GL_TEXTURE1);
		gl.glDisable(GL.GL_TEXTURE_2D);
		gl.glActiveTexture(GL.GL_TEXTURE0);
		gl.glDisable(GL.GL_TEXTURE_2D);
		
		ib_index_quad.rewind();
		int visqelements = 0;
		for(int qi = 0; qi < quads_total; qi++) {
			boolean inFrontOfCam = false;
			for(int vi = 0; vi < 4; vi++) {
				double cd = quad_vertex[qi * 4 * 3 + vi * 3] * env.cam_view[VecMath.IDX_X] + quad_vertex[qi * 4 * 3 + vi * 3 + 1] * env.cam_view[VecMath.IDX_Y] + quad_vertex[qi * 4 * 3 + vi * 3 + 2] * env.cam_view[VecMath.IDX_Z] + env.cam_planed;
				if(cd >= 0.0) {
					inFrontOfCam = true;
					break;
				}
			}

			if(!inFrontOfCam) {
				continue;
			}

			if(useVertexArrays)
			{
				if(quad_pt[qi] == PolygonType.PT_Wall) {
					// only draw left wall part (there is always a next wall at the right)
					ib_index_quad.put(qi * 4);
					ib_index_quad.put(qi * 4 + 1);
					visqelements++;
				} else {
					ib_index_quad.put(qi * 4);
					ib_index_quad.put(qi * 4 + 1);
					ib_index_quad.put(qi * 4 + 1);
					ib_index_quad.put(qi * 4 + 2);
					ib_index_quad.put(qi * 4 + 2);
					ib_index_quad.put(qi * 4 + 3);
					ib_index_quad.put(qi * 4 + 3);
					ib_index_quad.put(qi * 4);
					visqelements+=4;
				}
			}
			else
			{
				ib_index_quad.put(qi);
				visqelements++;
			}
		}

		ib_index_quad.rewind();

		if(visqelements>0) {
			if(useVertexArrays) {
				db_vertex_quad.rewind();
				db_texcoord_quad.rewind();

				gl.glVertexPointer(3, GL2.GL_DOUBLE, 0, db_vertex_quad);
				gl.glTexCoordPointer(2, GL2.GL_DOUBLE, 0, db_texcoord_quad);
				gl.glIndexPointer(GL2.GL_INT, 0, ib_index_quad);
				gl.glColorPointer(3, GL2.GL_DOUBLE, 0, q_cb);

				gl.glDrawElements(GL.GL_LINES, visqelements << 1, GL.GL_UNSIGNED_INT, ib_index_quad);
			} else {
				gl.glBegin(GL.GL_LINES);
				while(visqelements>0)
				{
					int vi = ib_index_quad.get();

					double nx = quad_plane[vi * 4];
					double ny = quad_plane[vi * 4 + 1];
					double nz = quad_plane[vi * 4 + 2];

					gl.glNormal3d(nx, ny, nz);

					for(int j=0; j<4; j++)
					{
						for(int nl=0; nl<2; nl++) {
							int h = j + nl;
							if(h>3)
								h = 0;

							double darknessr = q_dark[vi * 4 * 3 + h * 3];
							double darknessg = q_dark[vi * 4 * 3 + h * 3 + 1];
							double darknessb = q_dark[vi * 4 * 3 + h * 3 + 2];

							gl.glColor3d(darknessr, darknessg, darknessb);

							gl.glTexCoord2d
							(
									quad_tc[vi * 4 * 2 + h * 2]
									        , quad_tc[vi * 4 * 2 + h * 2 + 1]
							);
							gl.glVertex3d
							(
									quad_vertex[vi * 4 * 3 + h * 3]
									            , quad_vertex[vi * 4 * 3 + h * 3 + 1]
									                          , quad_vertex[vi * 4 * 3 + h * 3 + 2]
							);
						}

						if(quad_pt[vi]==PolygonType.PT_Wall)
							break;
					}

					visqelements--;
				}
				gl.glEnd();
				gl.glFlush();
			}
		}
		
			ib_index_tri.rewind();
			int vistris = 0;
			for(int ti = 0; ti < tris_total; ti++) {
				boolean inFrontOfCam = false;
				for(int vi = 0; vi < 3; vi++) {
					double cd = tri_vertex[ti * 3 * 3 + vi * 3] * env.cam_view[VecMath.IDX_X] + tri_vertex[ti * 3 * 3 + vi * 3 + 1] * env.cam_view[VecMath.IDX_Y] + tri_vertex[ti * 3 * 3 + vi * 3 + 2] * env.cam_view[VecMath.IDX_Z] + env.cam_planed;
					if(cd >= 0.0) {
						inFrontOfCam = true;
						break;
					}
				}
				
				if(!inFrontOfCam) {
					ti++;
				}

				vistris++;

				if(useVertexArrays)
				{
					ib_index_tri.put(ti * 3);
					ib_index_tri.put(ti * 3 + 1);
					ib_index_tri.put(ti * 3 + 1);
					ib_index_tri.put(ti * 3 + 2);
					ib_index_tri.put(ti * 3 + 2);
					ib_index_tri.put(ti * 3);
				}
				else
				{
					ib_index_tri.put(ti);
				}
			}

			ib_index_tri.rewind();
			
			if(vistris>0) {
				if(useVertexArrays) {
					db_vertex_tri.rewind();
					db_texcoord_tri.rewind();

					gl.glVertexPointer(3, GL2.GL_DOUBLE, 0, db_vertex_tri);
					gl.glTexCoordPointer(2, GL2.GL_DOUBLE, 0, db_texcoord_tri);
					gl.glIndexPointer(GL2.GL_INT, 0, ib_index_tri);
					gl.glColorPointer(3, GL2.GL_DOUBLE, 0, t_cb);

					gl.glDrawElements(GL.GL_LINES, vistris * 6, GL.GL_UNSIGNED_INT, ib_index_tri);
				} else {
					gl.glBegin(GL.GL_LINES);
					while(vistris>0)
					{
						int vi = ib_index_tri.get();

						double nx = tri_plane[vi * 4];
						double ny = tri_plane[vi * 4 + 1];
						double nz = tri_plane[vi * 4 + 2];

						gl.glNormal3d(nx, ny, nz);

						for(int j=0; j<3; j++)
						{
							for(int nl=0; nl<2; nl++) {
								int h = j + nl;
								if(h>2)
									h = 0;

								double darknessr = t_dark[vi * 3 * 3 + h * 3];
								double darknessg = t_dark[vi * 3 * 3 + h * 3 + 1];
								double darknessb = t_dark[vi * 3 * 3 + h * 3 + 2];

								gl.glColor3d(darknessr, darknessg, darknessb);

								gl.glTexCoord2d
								(
										  tri_tc[vi * 3 * 2 + h * 2]
										, tri_tc[vi * 3 * 2 + h * 2 + 1]
								);
								gl.glVertex3d
								(
										  tri_vertex[vi * 3 * 3 + h * 3]
										, tri_vertex[vi * 3 * 3 + h * 3 + 1]
										, tri_vertex[vi * 3 * 3 + h * 3 + 2]
								);
							}
						}

						vistris--;
					}
					gl.glEnd();
				}
			}

		if(useVertexArrays)
		{
			gl.glDisableClientState(GL2.GL_COLOR_ARRAY);
		}
	}
	
	
	private void drawTilePolygons(GLAutoDrawable drawable, double [] q_dark, double [] t_dark, DoubleBuffer q_cb, DoubleBuffer t_cb) {
		GL2 gl = drawable.getGL().getGL2();
		int qcount = 0;
		int tcount = 0;
		
		gl.glEnable(GL.GL_TEXTURE_2D);
		gl.glTexEnvi(GL.GL_TEXTURE_2D, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_MODULATE);
		gl.glColor4d(1.0, 1.0, 1.0, 1.0);
		
		for(int texid=0; texid<textures.length; texid++)
		{
			if(tex_quads[texid]==0 && tex_tris[texid]==0)
				continue;

			if(use_multitex_glow)
			{
				gl.glActiveTexture(GL.GL_TEXTURE0);
			}
			
			Texture tex = textures[texid];
			tex.bind(gl);
			
			if(use_multitex_glow)
			{
				gl.glActiveTexture(GL.GL_TEXTURE1);
				Texture gtex = glow_tex[texid];
				if(gtex!=null)
				{
					gl.glColor4d(1.0, 1.0, 1.0, 1.0);
					gl.glEnable(GL.GL_TEXTURE_2D);
					gtex.bind(gl);
					gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_DECAL);
				}
				else
				{
					gl.glDisable(GL.GL_TEXTURE_2D);
				}
				gl.glActiveTexture(GL.GL_TEXTURE0);
			}
			
			if(tex_quads[texid]>0)
			{
				int qoffs = tex_quad_offset[texid];
				int nquads = tex_quads[texid];
				int visquads = 0;

				ib_index_quad.rewind();

				for(int qn=0; qn<nquads; qn++)
				{
					int qi = qoffs + qn;
					
					if(!visible_tiles[quad_tileoffs[qi]])
						continue;
					
					double cd = quad_plane[qi * 4] * env.cam_pos[VecMath.IDX_X] + quad_plane[qi * 4 + 1] * env.cam_pos[VecMath.IDX_Y] + quad_plane[qi * 4 + 2] * env.cam_pos[VecMath.IDX_Z] + quad_plane[qi * 4 + 3];
					if(cd<0.0)
						continue;

					if(useVertexArrays)
					{
						ib_index_quad.put(qi * 4);
						ib_index_quad.put(qi * 4 + 1);
						ib_index_quad.put(qi * 4 + 2);
						ib_index_quad.put(qi * 4 + 3);
					}
					else
					{
						ib_index_quad.put(qi);
					}
					
					visquads++;
				}

				ib_index_quad.rewind();

				if(visquads > 0)
				{
					qcount+=visquads;
					
					if(useVertexArrays)
					{
						db_vertex_quad.rewind();
						db_texcoord_quad.rewind();
						
						gl.glVertexPointer(3, GL2.GL_DOUBLE, 0, db_vertex_quad);
						gl.glTexCoordPointer(2, GL2.GL_DOUBLE, 0, db_texcoord_quad);
						gl.glIndexPointer(GL2.GL_INT, 0, ib_index_quad);
						gl.glColorPointer(3, GL2.GL_DOUBLE, 0, q_cb);
						
						if(use_multitex_glow)
						{
							db_texcoord_quad.rewind();
							gl.glActiveTexture(GL.GL_TEXTURE1);
							gl.glClientActiveTexture(GL.GL_TEXTURE1);
							gl.glTexCoordPointer(2, GL2.GL_DOUBLE, 0, db_texcoord_quad);
							gl.glClientActiveTexture(GL.GL_TEXTURE0);
							gl.glActiveTexture(GL.GL_TEXTURE0);
						}
						
						
						gl.glDrawElements(GL2.GL_QUADS, visquads * 4, GL.GL_UNSIGNED_INT, ib_index_quad);
					}
					else
					{
						gl.glBegin(GL2.GL_QUADS);
						while(visquads>0)
						{
							int vi = ib_index_quad.get();
							
							double nx = quad_plane[vi * 4];
							double ny = quad_plane[vi * 4 + 1];
							double nz = quad_plane[vi * 4 + 2];
							
							gl.glNormal3d(nx, ny, nz);
							
							for(int j=0; j<4; j++)
							{
								double darknessr = q_dark[vi * 4 * 3 + j * 3];
								double darknessg = q_dark[vi * 4 * 3 + j * 3 + 1];
								double darknessb = q_dark[vi * 4 * 3 + j * 3 + 2];

								gl.glColor3d(darknessr, darknessg, darknessb);

								if(use_multitex_glow)
								{
									gl.glMultiTexCoord2d
									(
											  GL.GL_TEXTURE0
											, quad_tc[vi * 4 * 2 + j * 2]
											, quad_tc[vi * 4 * 2 + j * 2 + 1]
									);
									gl.glMultiTexCoord2d
									(
											GL.GL_TEXTURE1
											, quad_tc[vi * 4 * 2 + j * 2]
											, quad_tc[vi * 4 * 2 + j * 2 + 1]
									);
								}
								else
								{
									gl.glTexCoord2d
									(
											quad_tc[vi * 4 * 2 + j * 2]
										  , quad_tc[vi * 4 * 2 + j * 2 + 1]
									);
								}
								gl.glVertex3d
								(
									  quad_vertex[vi * 4 * 3 + j * 3]
									, quad_vertex[vi * 4 * 3 + j * 3 + 1]
									, quad_vertex[vi * 4 * 3 + j * 3 + 2]
								);
							}

							visquads--;
						}
						gl.glEnd();
					}
				}
			}
			
			if(tex_tris[texid]>0)
			{
				int toffs = tex_tri_offset[texid];
				int ntris = tex_tris[texid];
				int vistris = 0;

				ib_index_tri.rewind();
				
				for(int tn=0; tn<ntris; tn++)
				{
					int ti = toffs + tn;
					
					if(!visible_tiles[tri_tileoffs[ti]])
						continue;

					double cd = tri_plane[ti * 4] * env.cam_pos[VecMath.IDX_X] + tri_plane[ti * 4 + 1] * env.cam_pos[VecMath.IDX_Y] + tri_plane[ti * 4 + 2] * env.cam_pos[VecMath.IDX_Z] + tri_plane[ti * 4 + 3];
					if(cd<0.0)
						continue;
					
					if(useVertexArrays)
					{
						ib_index_tri.put(ti * 3);
						ib_index_tri.put(ti * 3 + 1);
						ib_index_tri.put(ti * 3 + 2);
					}
					else
					{
						ib_index_tri.put(ti);
					}
					
					vistris++;
				}

				ib_index_tri.rewind();
				
				if(vistris > 0)
				{
					tcount += vistris;
					
					if(useVertexArrays)
					{
						gl.glVertexPointer(3, GL2.GL_DOUBLE, 0, db_vertex_tri);
						gl.glTexCoordPointer(2, GL2.GL_DOUBLE, 0, db_texcoord_tri);
						gl.glIndexPointer(GL2.GL_INT, 0, ib_index_tri);
						gl.glColorPointer(3, GL2.GL_DOUBLE, 0, t_cb);
						
						if(use_multitex_glow)
						{
							gl.glActiveTexture(GL.GL_TEXTURE1);
							gl.glClientActiveTexture(GL.GL_TEXTURE1);
							gl.glTexCoordPointer(2, GL2.GL_DOUBLE, 0, db_texcoord_tri);
							gl.glClientActiveTexture(GL.GL_TEXTURE0);
							gl.glActiveTexture(GL.GL_TEXTURE0);
						}
						
						gl.glDrawElements(GL.GL_TRIANGLES, vistris * 3, GL.GL_UNSIGNED_INT, ib_index_tri);
					}
					else
					{
						gl.glBegin(GL.GL_TRIANGLES);
						while(vistris>0)
						{
							int vi = ib_index_tri.get();
							
							double nx = tri_plane[vi * 4];
							double ny = tri_plane[vi * 4 + 1];
							double nz = tri_plane[vi * 4 + 2];
							
							gl.glNormal3d(nx, ny, nz);
						
							for(int j=0; j<3; j++)
							{
								double darknessr = t_dark[vi * 3 * 3 + j * 3];
								double darknessg = t_dark[vi * 3 * 3 + j * 3 + 1];
								double darknessb = t_dark[vi * 3 * 3 + j * 3 + 2];

								gl.glColor3d(darknessr, darknessg, darknessb);

								if(use_multitex_glow)
								{
									gl.glMultiTexCoord2d
									(
										  GL.GL_TEXTURE0	
										, tri_tc[vi * 3 * 2 + j * 2]
										, tri_tc[vi * 3 * 2 + j * 2 + 1]
									);
									gl.glMultiTexCoord2d
									(
										  GL.GL_TEXTURE1	
										, tri_tc[vi * 3 * 2 + j * 2]
										, tri_tc[vi * 3 * 2 + j * 2 + 1]
									);
								}
								else
								{
									gl.glTexCoord2d
									(
											tri_tc[vi * 3 * 2 + j * 2]
										  , tri_tc[vi * 3 * 2 + j * 2 + 1]
									);
								}
								gl.glVertex3d
								(
									  tri_vertex[vi * 3 * 3 + j * 3]
									, tri_vertex[vi * 3 * 3 + j * 3 + 1]
									, tri_vertex[vi * 3 * 3 + j * 3 + 2]
								);
							}
							
							vistris--;
						}
						gl.glEnd();
					}
				}
			}
			gl.glFlush();
		}
		
		
		if(use_two_pass_glow)
		{
		// Draw glowing lights
		gl.glEnable(GL.GL_BLEND);
		gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
		gl.glColor4d(1.0, 1.0, 1.0, 1.0);
		
		if(useVertexArrays)
		{
			gl.glDisableClientState(GL2.GL_COLOR_ARRAY);
		}
		
		for(int texid=0; texid<textures.length; texid++)
		{
			if(tex_quads[texid]==0 && tex_tris[texid]==0)
				continue;

			Texture tex = glow_tex[texid];
			
			if(tex==null)
				continue;
			
			tex.bind(gl);
			
			if(tex_quads[texid]>0)
			{
				int qoffs = tex_quad_offset[texid];
				int nquads = tex_quads[texid];
				int visquads = 0;

				ib_index_quad.rewind();

				for(int qn=0; qn<nquads; qn++)
				{
					int qi = qoffs + qn;
					
					if(!visible_tiles[quad_tileoffs[qi]])
						continue;

					double cd = quad_plane[qi * 4] * env.cam_pos[VecMath.IDX_X] + quad_plane[qi * 4 + 1] * env.cam_pos[VecMath.IDX_Y] + quad_plane[qi * 4 + 2] * env.cam_pos[VecMath.IDX_Z] + quad_plane[qi * 4 + 3];
					if(cd<0.0)
						continue;

					if(useVertexArrays)
					{
						ib_index_quad.put(qi * 4);
						ib_index_quad.put(qi * 4 + 1);
						ib_index_quad.put(qi * 4 + 2);
						ib_index_quad.put(qi * 4 + 3);
					}
					else
					{
						ib_index_quad.put(qi);
					}
					
					visquads++;
				}

				ib_index_quad.rewind();

				if(visquads > 0)
				{
					qcount+=visquads;
					
					if(useVertexArrays)
					{
						db_vertex_quad.rewind();
						db_texcoord_quad.rewind();
						
						gl.glVertexPointer(3, GL2.GL_DOUBLE, 0, db_vertex_quad);
						gl.glTexCoordPointer(2, GL2.GL_DOUBLE, 0, db_texcoord_quad);
						gl.glIndexPointer(GL2.GL_INT, 0, ib_index_quad);

						gl.glDrawElements(GL2.GL_QUADS, visquads * 4, GL.GL_UNSIGNED_INT, ib_index_quad);
					}
					else
					{
						gl.glBegin(GL2.GL_QUADS);
						while(visquads>0)
						{
							int vi = ib_index_quad.get();
							
							double nx = quad_plane[vi * 4];
							double ny = quad_plane[vi * 4 + 1];
							double nz = quad_plane[vi * 4 + 2];
							
							gl.glNormal3d(nx, ny, nz);
							
							for(int j=0; j<4; j++)
							{
								gl.glTexCoord2d
								(
									  quad_tc[vi * 4 * 2 + j * 2]
									, quad_tc[vi * 4 * 2 + j * 2 + 1]
								);
								gl.glVertex3d
								(
									  quad_vertex[vi * 4 * 3 + j * 3]
									, quad_vertex[vi * 4 * 3 + j * 3 + 1]
									, quad_vertex[vi * 4 * 3 + j * 3 + 2]
								);
							}

							visquads--;
						}
						gl.glEnd();
					}
				}
			}
			
			if(tex_tris[texid]>0)
			{
				int toffs = tex_tri_offset[texid];
				int ntris = tex_tris[texid];
				int vistris = 0;

				ib_index_tri.rewind();
				
				for(int tn=0; tn<ntris; tn++)
				{
					int ti = toffs + tn;
					
					if(!visible_tiles[tri_tileoffs[ti]])
						continue;

					double cd = tri_plane[ti * 4] * env.cam_pos[VecMath.IDX_X] + tri_plane[ti * 4 + 1] * env.cam_pos[VecMath.IDX_Y] + tri_plane[ti * 4 + 2] * env.cam_pos[VecMath.IDX_Z] + tri_plane[ti * 4 + 3];
					if(cd<0.0)
						continue;
					
					if(useVertexArrays)
					{
						ib_index_tri.put(ti * 3);
						ib_index_tri.put(ti * 3 + 1);
						ib_index_tri.put(ti * 3 + 2);
					}
					else
					{
						ib_index_tri.put(ti);
					}
					
					vistris++;
				}

				ib_index_tri.rewind();
				
				if(vistris > 0)
				{
					tcount += vistris;
					
					if(useVertexArrays)
					{
						db_vertex_tri.rewind();
						db_texcoord_tri.rewind();

						gl.glVertexPointer(3, GL2.GL_DOUBLE, 0, db_vertex_tri);
						gl.glTexCoordPointer(2, GL2.GL_DOUBLE, 0, db_texcoord_tri);
						gl.glIndexPointer(GL2.GL_INT, 0, ib_index_tri);
						
						gl.glDrawElements(GL.GL_TRIANGLES, vistris * 3, GL.GL_UNSIGNED_INT, ib_index_tri);
					}
					else
					{
						gl.glBegin(GL.GL_TRIANGLES);
						while(vistris>0)
						{
							int vi = ib_index_tri.get();
							
							double nx = tri_plane[vi * 4];
							double ny = tri_plane[vi * 4 + 1];
							double nz = tri_plane[vi * 4 + 2];
							
							gl.glNormal3d(nx, ny, nz);
						
							for(int j=0; j<3; j++)
							{
								gl.glTexCoord2d
								(
									  tri_tc[vi * 3 * 2 + j * 2]
									, tri_tc[vi * 3 * 2 + j * 2 + 1]
								);
								gl.glVertex3d
								(
									  tri_vertex[vi * 3 * 3 + j * 3]
									, tri_vertex[vi * 3 * 3 + j * 3 + 1]
									, tri_vertex[vi * 3 * 3 + j * 3 + 2]
								);
							}
							
							vistris--;
						}
						gl.glEnd();
					}
				}
			}
			gl.glFlush();
		}
		}
		
		if(useVertexArrays)
		{
			gl.glDisableClientState(GL2.GL_COLOR_ARRAY);
		}
		if(use_multitex_glow)
		{
			gl.glActiveTexture(GL.GL_TEXTURE1);
			gl.glDisable(GL.GL_TEXTURE_2D);
			gl.glActiveTexture(GL.GL_TEXTURE0);
		}
	}
	
	
	private double [] [] boxCoords = {
			  { -0.5, +0.5, -0.5 } // 0 - + -
			, { -0.5, +0.5, +0.5 } // 1 - + +
			, { +0.5, +0.5, +0.5 } // 2 + + +
			, { +0.5, +0.5, -0.5 } // 3 + + -
			, { -0.5, -0.5, -0.5 } // 4 - - -
			, { -0.5, -0.5, +0.5 } // 5 - - +
			, { +0.5, -0.5, +0.5 } // 6 + - +
			, { +0.5, -0.5, -0.5 } // 7 + - -
	};
	
	private int [] [] boxFaces = {
			  { 1, 0, 3, 2 } // top, Y const +
			, { 5, 4, 7, 6 } // bottom, Y const -
			, { 1, 5, 4, 0 } // west, X const -
			, { 3, 7, 6, 2 } // east, X const +
			, { 0, 4, 7, 3 } // north , Z const -
			, { 2, 6, 5, 1 } // south , Z const +
	};
	
	private double [] [] tcTopBottom = {
			  { 1.0, 1.0 }
			, { 1.0, 0.0 }
			, { 0.0, 0.0 }
			, { 0.0, 1.0 }
	};

	private double [] [] tcSide = {
			  { 1.0, 0.0 }
			, { 1.0, 1.0 }
			, { 0.0, 1.0 }
			, { 0.0, 0.0 }
	};
	
	private double [] [] [] tcFace = {
		  tcTopBottom
		, tcTopBottom
		, tcSide
		, tcSide
		, tcSide
		, tcSide
	};
	
	private boolean [] tcFaceTex = {
			true, true, false, false, false, false
	};
	
	private void renderBox(GL2 gl, Texture topBottomTex, Texture sideTex, double [] crateDarkness) {
		Texture lastTex = null;
		for(int i=0; i<boxFaces.length; i++) {
			Texture curTex = tcFaceTex[i] ? topBottomTex : sideTex;
			if(curTex != lastTex) {
				if(lastTex!=null) {
					gl.glFlush();
					gl.glEnd();
				}
				curTex.bind(gl);
				gl.glBegin(GL2.GL_QUADS);
				lastTex = curTex;
			}
			int [] vectorIndices = boxFaces[i];
			double [] [] texCoords = tcFace[i];
			for(int v=0; v<4; v++) {
				double [] coords = boxCoords[vectorIndices[v]];
				gl.glColor3d(crateDarkness[v], crateDarkness[v], crateDarkness[v]);
				gl.glTexCoord2d(texCoords[v][0], texCoords[v][1]);
				gl.glVertex3d(coords[0], coords[1], coords[2]);
			}
		}
		gl.glEnd();
		gl.glFlush();
	}
	
	@Override
	public void display(GLAutoDrawable drawable) {
		if(nextMap!=null) {
			System.out.format("Loading new map...");
			loadMapInternal(drawable, nextMap);
			nextMap = null;
		}

		long curTime = System.currentTimeMillis();
		
		if(env.lastTime==-1)
			env.lastTime=curTime;
		
		long tdelta = curTime - env.lastTime;
		
		env.lastTime = curTime;
		
		handle_environment(tdelta);
		handle_movement(tdelta);
		
		if(env.frame_time == -1)
		{
			env.frame_time = curTime;
		}
		else
		{
			if(env.frames == 50)
			{
				long ftime = curTime - env.frame_time;
				env.fps = (double)env.frames / ((double)ftime / 1000.0);
				env.frame_time = curTime;
				env.frames = 0;
			}
		}
		
		// North: negative Z
		// East: positive X

		double cam_tile_x_d = env.cam_pos[VecMath.IDX_X] * worldToTileSpace;
		double cam_tile_y_d = env.cam_pos[VecMath.IDX_Z] * worldToTileSpace;
		
		int cam_tile_x = (int)cam_tile_x_d;
		int cam_tile_y = (int)cam_tile_y_d;

		MapTile cam_mt = map.getTile(cam_tile_x, (map.getVertSize() - 1) - cam_tile_y);

		if(cam_mt!=null && cam_mt.getType()!=MapTile.Type.Solid && !cheatCodes.activeCheat(cheatFly))
		{
			double floorh = cam_mt.getFloorAt(cam_tile_x_d  - cam_tile_x, cam_tile_y_d  - cam_tile_y);
			env.cam_pos[VecMath.IDX_Y] = floorh * mapToWorldSpace + env.eye_height;
		}
		
		String itexs = "";
		if(cam_mt!=null)
		{
			if(cam_mt.hasInvalidTexture())
			{
				itexs = " !Tex " + cam_mt.getInvalidTex();
			}
			else
			{
				itexs = " Tex " + cam_mt.getTextureIndexWall() + " " + cam_mt.getTextureWall();
			}
		}
		
		int c_dir_index = (((int)((env.cam_rot[VecMath.IDX_Y] + (360.0 + 45.0)) / 90.0))%4);
		byte avoid_portal = portals[(c_dir_index + 2)%4];

		GL2 gl = drawable.getGL().getGL2();
		
		if(old_tile_x != cam_tile_x || old_tile_y != cam_tile_y || old_cdir != c_dir_index)
		{
			computeVisibleTiles(cam_tile_x, cam_tile_y, avoid_portal);

			if(cam_mt==null || !has_drawable[cam_tile_y * map.getHorzSize() + cam_tile_x])
				allVisible();

			if(show_vismap)
			{
				updateVismapTex(gl, cam_tile_x, cam_tile_y);
			}
			
			old_tile_x = cam_tile_x;
			old_tile_y = cam_tile_y;
			old_cdir = c_dir_index;
		}
		
		if(env.fov_update)
		{
			gl.glMatrixMode(GL2.GL_PROJECTION);
			gl.glLoadIdentity();
			glu.gluPerspective(env.fov_h, env.aspect, 0.1, 100.0);
			env.fov_update = false;
			gl.glMatrixMode(GL2.GL_MODELVIEW);
		}
		
		gl.glLoadIdentity();

		gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
		
		gl.glRotated(env.cam_rot[VecMath.IDX_X], 1.0, 0.0, 0.0);
		gl.glRotated(env.cam_rot[VecMath.IDX_Y], 0.0, 1.0, 0.0);
		gl.glRotated(env.cam_rot[VecMath.IDX_Z], 0.0, 0.0, 1.0);
		
		gl.glTranslated(-env.cam_pos[VecMath.IDX_X], -env.cam_pos[VecMath.IDX_Y], -env.cam_pos[VecMath.IDX_Z]);

		gl.glDisable(GL.GL_BLEND);
		
		int tcount = 0;
		int qcount = 0;
		
		double [] q_dark = quad_dark;
		double [] t_dark = tri_dark;
		
		DoubleBuffer q_cb = db_color_quad;
		DoubleBuffer t_cb = db_color_tri;
		
		boolean do_sprite_lightning = true;
		
		if(cheatCodes.activeCheat(cheatNoLighting))
		{
			q_dark = quad_full_bright;
			t_dark = tri_full_bright;
			q_cb = db_fb_quad;
			t_cb = db_fb_tri;
			do_sprite_lightning = false;
		}

		// check picked wall (if picked or just draw the walls in pick-colors)
		if(!map.isCyberspace() && pickPoint!=null || cheatCodes.activeCheat(cheatOnlyPick)) {
			processPickQuery(drawable);
		}

		// Draw normal surfaces (skip if pick-debugging...)
		if(!cheatCodes.activeCheat(cheatOnlyPick)) {
			if(map.isCyberspace()) {
				drawTilePolygonsCyber(drawable, q_dark, t_dark, q_cb, t_cb);
			} else {
				drawTilePolygons(drawable, q_dark, t_dark, q_cb, t_cb);
			}
		}
		
		gl.glColor3d(0.0, 1.0, 0.0);
		gl.glDisable(GL.GL_TEXTURE_2D);
		
		// Draw Object Markers
		
		ZOrderQueue<Integer> orderedSprites = new ZOrderQueue<Integer>(); 
		
		// fetch sprites
		for(int i=0; i<valid_objects; i++) {
			double x = object_vertex[i*6 + 0];
			double y = object_vertex[i*6 + 1];
			double z = object_vertex[i*6 + 2];

			// the coords might be aligned with walls and suggest different tiles...
			SSObject.MOTEntry mote = mapObjects.get(mORIndex[i]);
			int tile_x = mote.getXCoord() / 256;
			int tile_y = (map.getVertSize()-1) - (mote.getYCoord() / 256);

			int tileoffs = tile_y * map.getHorzSize() + tile_x;
			
			if( tileoffs < 0 || tileoffs >= visible_tiles.length)
				continue;

			int cam_dist_sq = Math.abs(cam_tile_x - tile_x) + Math.abs(cam_tile_y - tile_y);   
			
			if( cam_dist_sq > 2 && !visible_tiles[tile_y * map.getHorzSize() + tile_x] )
				continue;
			
			/*
			if(Math.abs(cam_tile_x - tile_x) > 8
					|| Math.abs(cam_tile_y - tile_y) > 8)
				continue;
			*/
			
			double cd = x * env.cam_view[VecMath.IDX_X] + y * env.cam_view[VecMath.IDX_Y] +z * env.cam_view[VecMath.IDX_Z] + env.cam_planed;
			
			if(cd<0.0)
				continue;
			
			double [] ovec = new double [3];
			ovec[VecMath.IDX_X] = x - env.cam_pos[VecMath.IDX_X];
			ovec[VecMath.IDX_Y] = y - env.cam_pos[VecMath.IDX_Y];
			ovec[VecMath.IDX_Z] = z - env.cam_pos[VecMath.IDX_Z];
			
			double view_dist = VecMath.component(ovec, env.cam_view);
			
			orderedSprites.offer(view_dist, Integer.valueOf(i));
		}

		if(cheatCodes.activeCheat(cheatNoHide)) {
			// Draw lines as base
			gl.glBegin(GL.GL_LINES);

			for (int i : orderedSprites) {
				double x1 = object_vertex[i * 6 + 0];
				double y1 = object_vertex[i * 6 + 1];
				double z1 = object_vertex[i * 6 + 2];

				double x2 = object_vertex[i * 6 + 3];
				double y2 = object_vertex[i * 6 + 4];
				double z2 = object_vertex[i * 6 + 5];
				if (object_class[i] == SSObject.ObjectClass.DoorsAndGratings) {
					// get drawn below
					continue;
				} else if (object_class[i] == SSObject.ObjectClass.Critters) {
					gl.glColor3d(1.0, 0.0, 0.0);
				} else if (object_class[i] == SSObject.ObjectClass.Patches) {
					gl.glColor3d(0.0, 0.0, 1.0);
				} else if (object_class[i] == SSObject.ObjectClass.SoftwareAndLogs) {
					gl.glColor3d(1.0, 1.0, 0.0);
				} else if (object_class[i] == SSObject.ObjectClass.Hardware) {
					gl.glColor3d(0.0, 1.0, 1.0);
				} else {
					gl.glColor3d(1.0, 1.0, 1.0);
				}


				gl.glVertex3d(x1, y1, z1);
				gl.glVertex3d(x2, y2, z2);
			}
			gl.glEnd();
		}
		
		double [] door_vertices = { 
				  -0.5,  0.5, 0.0
				,  0.5,  0.5, 0.0
				,  0.5, -0.5, 0.0
				, -0.5, -0.5, 0.0
		};
		
		double [] door_tc = {
				  0.0, 0.0
				, 1.0, 0.0
				, 1.0, 1.0
				, 0.0, 1.0
		};
		
		double door_width = 256.0 * mapToWorldSpace;
		
		gl.glActiveTexture(GL.GL_TEXTURE0);
		gl.glEnable(GL.GL_TEXTURE_2D);
		gl.glEnable(GL.GL_BLEND);
		gl.glColor3d(1.0, 1.0, 1.0);
		
		gl.glAlphaFunc(GL.GL_GREATER, 0.1f);
		gl.glEnable(GL2.GL_ALPHA_TEST);
		
		gl.glEnable(GL.GL_POLYGON_OFFSET_FILL);
		for(int i : orderedSprites)
		{
			if(object_class[i]==SSObject.ObjectClass.DoorsAndGratings)
			{
				int doorChunk = SSLogic.doorBitmapChunkBase + object_id[i] - SSObject.object_index[SSObject.ObjectClass.DoorsAndGratings.ordinal()][0];
				
				Texture [] textures = doorTextureMap.get(doorChunk);
				
				/* these coordinates are possible fixed to pin oriented sprites on
				 * tile corners (door fudge)
				 */
				double x1 = object_vertex[i*6 + 0];
				double y1 = object_vertex[i*6 + 1];
				double z1 = object_vertex[i*6 + 2];

				//z position fix from tsshp
				// TODO: is this already implemented ?
				//int iy1 = (int)(Math.floor(mote.getZCoord() + 0.5));
				//iy1 += 1 << (7 - map.getHeightShift());
				//iy1 &= ((0xFFFFFFFF) << (8 - map.getHeightShift()));
								
				if(textures!=null) {
					// animate the doors
					int dframe = doorFrame % (textures.length*2);
					if(dframe >= textures.length)
						dframe = (textures.length - (dframe - textures.length))-1;
					textures[dframe].bind(gl);
					
					double rotx = object_rotation[i * 3];
					double roty = object_rotation[i * 3 + 1];
					double rotz = object_rotation[i * 3 + 2];
				
					double vx, vy, vz;
				
					gl.glBegin(GL2.GL_QUADS);
					
					for(int di=0;di<4; di++) {
						gl.glTexCoord2d(door_tc[di*2], door_tc[di*2+1]);
						
						double [] dvec = VecMath.mkVec(door_vertices[di*3] * door_width, door_vertices[di*3+1] * door_width, door_vertices[di*3+2] * door_width, null);
						
						VecMath.rotateZ(dvec, -rotz, dvec);
						VecMath.rotateX(dvec, rotx, dvec);
						VecMath.rotateY(dvec, roty, dvec);
						
						vx = x1 + dvec[0];
						vy = y1 + dvec[1];
						vz = z1 + dvec[2];
						
						double darkness = 1.0 - clamp(darkness_at(vx, vz, vy));
						
						if(cheatCodes.activeCheat(cheatNoLighting))
							darkness = 1.0;
						
						gl.glColor3d(darkness, darkness, darkness);
						gl.glVertex3d(vx, vy, vz);
					}
					
					gl.glEnd();
				}
			}
			if(object_class[i]==SSObject.ObjectClass.SceneryAndFixtures)
			{
				MOTEntry mote = mapObjects.get(mORIndex[i]);
				if(mote.getObjectTypeId() == SSObject.OTID_WORDS || mote.getObjectTypeId() == SSObject.OTID_GRAFITTI || mote.getObjectTypeId() == SSObject.OTID_DECAL) {

					Texture tex = decalTextures.get(i);

					if(tex != null) {
						
						Integer logScale = decalScale.get(i);
						
						double fontScale = Fixture.FONT_SCALE;
						
						if(logScale != null) {
							fontScale /= (16 >>> logScale);
						}

						double x1 = object_vertex[i*6 + 0];
						double y1 = object_vertex[i*6 + 1];
						double z1 = object_vertex[i*6 + 2];

						tex.bind(gl);

						double rotx = object_rotation[i * 3];
						double roty = object_rotation[i * 3 + 1];
						double rotz = object_rotation[i * 3 + 2];

						double vx, vy, vz;

						gl.glBegin(GL2.GL_QUADS);

						for(int di=0;di<4; di++) {
							gl.glTexCoord2d(door_tc[di*2], door_tc[di*2+1]);

							double [] dvec = VecMath.mkVec(door_vertices[di*3] * tex.getImageWidth() * fontScale * mapToWorldSpace, door_vertices[di*3+1] * tex.getImageHeight() * fontScale * mapToWorldSpace, 0.0, null);

							VecMath.rotateZ(dvec, -rotz, dvec);
							VecMath.rotateX(dvec, rotx, dvec);
							VecMath.rotateY(dvec, roty, dvec);

							vx = x1 + dvec[0];
							vy = y1 + dvec[1];
							vz = z1 + dvec[2];

							double darkness = 1.0 - clamp(darkness_at(vx, vz, vy));

							if(cheatCodes.activeCheat(cheatNoLighting))
								darkness = 1.0;

							gl.glColor3d(darkness, darkness, darkness);
							gl.glVertex3d(vx, vy, vz);
						}

						gl.glEnd();
					}
				}
			}
		}
		gl.glDisable(GL.GL_POLYGON_OFFSET_FILL);

		
		for(int i : orderedSprites)
		{
			if(object_properties[i].renderType == SSObject.RenderType.Model3D) {
				SSModel model = models.get(Integer.valueOf(object_properties[i].model3d_index));
				int classIndex = object_class_index[i];
				int tc = 0;
				if(model.usesObjectTexture()) {
					tc = classIndexTextureChunkMap.get(classIndex);
				}
				
				if(model!=null) {
					double coords_x = object_vertex[i*6 + 0];					
					double coords_y = object_vertex[i*6 + 1];					
					double coords_z = object_vertex[i*6 + 2];

					gl.glPushMatrix();

					gl.glTranslated(coords_x, coords_y, coords_z);
					gl.glScaled(mapToWorldSpace, mapToWorldSpace, mapToWorldSpace);
					double rotX = (object_rotation[i*3 + 0] / Math.PI) * 180.0;
					double rotY = -180.0 + (object_rotation[i*3 + 1] / Math.PI) * 180.0;
					double rotZ = (object_rotation[i*3 + 2] / Math.PI) * 180.0;
					gl.glRotated(rotX, 1, 0, 0);
					gl.glRotated(rotY, 0, 1, 0);
					gl.glRotated(rotZ, 0, 0, 1);
					
					renderNode(gl, tc, model, model.getRoot(), coords_x, coords_z, coords_y);
					
					gl.glPopMatrix();
				}
			} else if(object_class[i] == ObjectClass.SceneryAndFixtures && object_id[i] == SSObject.OBJID_BRIDGE) {
				Fixture f = fixtureInfo.get(object_class_index[i]);
				if(f != null)
				{
					int width = f.getFlags() & 0x0F;
					int depth = (f.getFlags() & 0xF0) >>> 4;
					int height = (f.getFlags() & 0xFF00) >>> 8;
				
					if(width == 0)
						width = 4;
					
					if(depth == 0)
						depth = 4;
					
					if(height == 0)
						height = 2;
					
					double worldWidth = mapToWorldSpace * ((width * 256.0) / 4);
					double worldDepth = mapToWorldSpace * ((depth * 256.0) / 4);
					double worldHeight = mapToWorldSpace * ((height * 256.0) / 64);
					
					int topBottomTex = f.getTexture() & 0xFF;
					int sideTex = (f.getTexture() & 0xFF00) >>> 8;
		
					Texture topBottomTexture = null;
					Texture sideTexture = null;
					
					boolean useWallIndex = (topBottomTex & 0x80) != 0;
		
					// use wall tex ?
					if(useWallIndex) {
						topBottomTex = topBottomTex & 0x7F;
						if(topBottomTex < texids.length)
							topBottomTexture = textures[topBottomTex];
					} else {
						topBottomTex = SSModel.MODEL_OBJECT_TEXTURE_CHUNK_BASE + topBottomTex;
						topBottomTexture = modelTextures.get(topBottomTex);
					}
					if(useWallIndex) {
						sideTex = sideTex & 0x7F;
						if(sideTex < texids.length)
							sideTexture = textures[sideTex];
					} else {
						sideTex = SSModel.MODEL_OBJECT_TEXTURE_CHUNK_BASE + sideTex;
						sideTexture = modelTextures.get(sideTex);
					}
					
					if(topBottomTexture != null && sideTexture != null) {
						gl.glEnable(GL.GL_TEXTURE_2D);
						
						double [] crateDarkness = cheatCodes.activeCheat(cheatNoLighting) ? null : crateDarknessMap.get(i);
						
						if(crateDarkness == null ) {
							crateDarkness = tmpCrateDarkness;
							Arrays.fill(crateDarkness, cheatCodes.activeCheat(cheatNoLighting) ? 1.0 : object_darkness[i]);
						}
						
						double coords_x = object_vertex[i*6 + 0];					
						double coords_y = object_vertex[i*6 + 1];					
						double coords_z = object_vertex[i*6 + 2];

						gl.glPushMatrix();

						gl.glTranslated(coords_x, coords_y + (worldHeight / 2.0), coords_z);
						double rotX = (object_rotation[i*3 + 0] / Math.PI) * 180.0;
						double rotY = -180.0 + (object_rotation[i*3 + 1] / Math.PI) * 180.0;
						double rotZ = (object_rotation[i*3 + 2] / Math.PI) * 180.0;
						gl.glRotated(-rotZ, 0, 0, 1);
						gl.glRotated(rotX, 1, 0, 0);
						gl.glRotated(rotY, 0, 1, 0);
						
						gl.glScaled(worldWidth, worldHeight, worldDepth);
						renderBox(gl, topBottomTexture, sideTexture, crateDarkness);
						
						gl.glPopMatrix();
					}
				}
			} else if (object_id[i] == SSObject.OBJID_SMALL_CRATE || object_id[i] == SSObject.OBJID_LARGE_CRATE || object_id[i] == SSObject.OBJID_SECURE_CRATE) {
				Container con = containerInfo.get(object_class_index[i]);
				
				int defaultWidth, defaultHeight, defaultDepth;
				defaultWidth = defaultHeight = defaultDepth = Container.CONTAINER_SMALL_SIZE;
				
				switch(object_id[i]) {
				case SSObject.OBJID_LARGE_CRATE:
					defaultWidth = defaultHeight = defaultDepth = Container.CONTAINER_LARGE_SIZE;
					break;
				case SSObject.OBJID_SECURE_CRATE:
					defaultWidth = defaultHeight = defaultDepth = Container.CONTAINER_SECURE_SIZE;
					break;
				}
				
				if(con!=null) {
					double worldWidth = ((con.getWidth() == 0) ? defaultWidth : (10 * con.getWidth())) * mapToWorldSpace; 
					double worldHeight = ((con.getHeight() == 0) ? defaultHeight : (10 * con.getHeight())) * mapToWorldSpace; 
					double worldDepth = ((con.getDepth() == 0) ? defaultDepth : (10 * con.getDepth())) * mapToWorldSpace;
					
					int topBottomTex = con.getTopTexture() == 0 ? SSObject.Container.CONTAINER_DEFAULT_TOP_BOTTOM_TEXTURE : con.getTopTexture();
					int sideTex = con.getSideTexture() == 0 ? SSObject.Container.CONTAINER_DEFAULT_SIDE_TEXTURE : con.getSideTexture();
					
					Texture topBottomTexture = modelTextures.get(SSModel.MODEL_OBJECT_TEXTURE_CHUNK_BASE + topBottomTex);
					Texture sideTexture = modelTextures.get(SSModel.MODEL_OBJECT_TEXTURE_CHUNK_BASE + sideTex);
					
					if(topBottomTexture == null) {
						System.err.println("No such top-texture: " + topBottomTex + " " + (SSModel.MODEL_OBJECT_TEXTURE_CHUNK_BASE + topBottomTex) + " id #" + object_id[i] + " CI: " + object_class_index[i]);
						topBottomTexture = textures[0];
					}
					if(sideTexture == null) {
						System.err.println("No such side-texture: " + sideTex + " " + (SSModel.MODEL_OBJECT_TEXTURE_CHUNK_BASE + sideTex));
						sideTexture = textures[1];
					}
					
					if(topBottomTexture != null && sideTexture != null) {
						gl.glEnable(GL.GL_TEXTURE_2D);
						
						double [] crateDarkness = cheatCodes.activeCheat(cheatNoLighting) ? null : crateDarknessMap.get(i);
						
						if(crateDarkness == null ) {
							crateDarkness = tmpCrateDarkness;
							Arrays.fill(crateDarkness, cheatCodes.activeCheat(cheatNoLighting) ? 1.0 : object_darkness[i]);
						}

						double coords_x = object_vertex[i*6 + 0];					
						double coords_y = object_vertex[i*6 + 1];					
						double coords_z = object_vertex[i*6 + 2];

						gl.glPushMatrix();

						gl.glTranslated(coords_x, coords_y, coords_z);
						double rotX = (object_rotation[i*3 + 0] / Math.PI) * 180.0;
						double rotY = -180.0 + (object_rotation[i*3 + 1] / Math.PI) * 180.0;
						double rotZ = (object_rotation[i*3 + 2] / Math.PI) * 180.0;
						gl.glRotated(-rotZ, 0, 0, 1);
						gl.glRotated(rotX, 1, 0, 0);
						gl.glRotated(rotY, 0, 1, 0);
						
						gl.glScaled(worldWidth, worldDepth, worldHeight);
						renderBox(gl, topBottomTexture, sideTexture, crateDarkness);
						
						gl.glPopMatrix();

					} 
				}
			}
		}		
		// Billboarding for the Y-Axis (Up)
		// -> Facing the viewer, but only w.r.t Y
		
		// Get sine and cosine for view direction
		double angy = -(env.cam_rot[VecMath.IDX_Y] / 180.0) * Math.PI;
		//double angx = -(env.cam_rot[IDX_X] / 180.0) * Math.PI;
		
		// okay, now we do some sprites
		gl.glActiveTexture(GL.GL_TEXTURE0);
		gl.glEnable(GL.GL_TEXTURE_2D);
		gl.glEnable(GL.GL_BLEND);
		gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
		gl.glColor4d(1.0, 1.0, 1.0, 1.0);
		gl.glNormal3d(-env.cam_view[VecMath.IDX_X], -env.cam_view[VecMath.IDX_Y], -env.cam_view[VecMath.IDX_Z]);
		
		double [] coords = { 0,0,0, 0,0,0, 0,0,0, -3,0,0 };
		for(int i : orderedSprites)
		{
			CenteredTexture spriteCTex = prepareSpriteCoordinates(coords, i);
			if(spriteCTex==null)
				continue;
			
			Texture spriteTex = spriteCTex.texture;
			
			TextureCoords tc = spriteTex.getImageTexCoords();
			
			int [] corners = { TRI, TLI, BLI, BRI };  
			double [] tcarray = { tc.right(), tc.top(), tc.left(), tc.top(), tc.left(), tc.bottom(), tc.right(), tc.bottom() };
			
			if(do_sprite_lightning) {
				double spriteDark = object_darkness[i];

				gl.glColor3d(spriteDark, spriteDark, spriteDark);
			}
			
			spriteTex.bind(gl);
			
			gl.glBegin(GL2.GL_QUADS);
			
			for(int ci = 0; ci<corners.length; ci++) {
				int coordIndex = corners[ci];
				gl.glTexCoord2d(tcarray[ci*2], tcarray[ci*2+1]);
				gl.glVertex3d(coords[coordIndex*3+ VecMath.IDX_X], coords[coordIndex*3+ VecMath.IDX_Y], coords[coordIndex*3+ VecMath.IDX_Z]);
			}
			
			gl.glEnd();

			gl.glFlush();
		}
		
		// Now we draw the actual marker

		// get current camera position (world translation)
		double [] campos = new double [3];
		
		campos[VecMath.IDX_X] = -env.cam_pos[VecMath.IDX_X];
		campos[VecMath.IDX_Y] = 0.0;
		campos[VecMath.IDX_Z] = -env.cam_pos[VecMath.IDX_Z];
		
		// calculate the coordinates in open gl space (it has some name I forgot)
		// this is the offset to reach the camera position w.r.t. the viewer
		VecMath.rotateY(campos, -angy, campos);
		
		// restore 'normal' coordinate system
		// this is needed to make the TextRenderer produce a billboard-rendering
		gl.glTranslated(env.cam_pos[VecMath.IDX_X], 0.0, env.cam_pos[VecMath.IDX_Z]);
		gl.glRotated(-env.cam_rot[VecMath.IDX_Y], 0.0, 1.0, 0.0);
		// translate world back to current position (pre-rotated coordinates)
		gl.glTranslated(campos[VecMath.IDX_X], campos[VecMath.IDX_Y], campos[VecMath.IDX_Z]);
		
		//int spriteNum = 0;
		// now produce the billboards
		tr.begin3DRendering();
		//for(int i : orderedSprites)
		while(cheatCodes.activeCheat(cheatNoHide) && !orderedSprites.isEmpty())
		{
			ZOrderQueue<Integer>.ZInfo zi = orderedSprites.pollZ();
			int i = zi.getElement();
			
			double [] opos = new double [3];
			
			opos[VecMath.IDX_X] = object_vertex[i*6 + 3];
			opos[VecMath.IDX_Y] = object_vertex[i*6 + 4];
			opos[VecMath.IDX_Z] = object_vertex[i*6 + 5];

			double cam_dist_x = opos[VecMath.IDX_X] - env.cam_pos[VecMath.IDX_X];
			double cam_dist_y = opos[VecMath.IDX_Y] - env.cam_pos[VecMath.IDX_Y];
			double cam_dist_z = opos[VecMath.IDX_Z] - env.cam_pos[VecMath.IDX_Z];
			
			double cam_dist_sq = cam_dist_x * cam_dist_x + cam_dist_y * cam_dist_y + cam_dist_z * cam_dist_z;
			double max_dist_sq = 16 * tileToWorldSpace * tileToWorldSpace;
			
			if(cam_dist_sq > max_dist_sq)
				continue;
			
			// since we crippled the rotation matrix we have to 
			// do the calculation on CPU...
			VecMath.rotateY(opos, -angy, opos);
			
			if(object_class[i]==SSObject.ObjectClass.Critters)
			{
				tr.setColor(1.0f, 0.0f, 0.0f, 1.0f);
			}
			else
			if(object_class[i]==SSObject.ObjectClass.Patches)
			{
				tr.setColor(0.0f, 0.0f, 1.0f, 1.0f);
			}
			else
			if(object_class[i]==SSObject.ObjectClass.SoftwareAndLogs)
			{
				tr.setColor(1.0f, 1.0f, 0.0f, 1.0f);
			}
			else
			if(object_class[i]==SSObject.ObjectClass.Hardware)
			{
				tr.setColor(0.0f, 1.0f, 1.0f, 1.0f);
			}
			else
			{
				tr.setColor(1.0f, 1.0f, 1.0f, 1.0f);
			}
			
			int classIndex = object_class_index[i];
			Integer tc = classIndexTextureChunkMap.get(classIndex);
			
			SSObject.MOTEntry mote = mapObjects.get(mORIndex[i]);
			
			SSObject.CommonObjectProperty cop = mote.getCommonProperty();
			String specialString = "";
			
			if(mote.getOClass() == SSObject.ObjectClass.SceneryAndFixtures) {
				Fixture fix = fixtureInfo.get(mote.getObjectClassIndex());
				if(fix != null) {
					if(mote.getObjectId() == SSObject.OBJID_BRIDGE) {
						int sideTex = (fix.getTexture() & 0xFF00) >>> 8;
						int sideTexId = ((sideTex & 0x80)!=0) ? map.getUsedTextures()[sideTex & 0x7F] : SSModel.MODEL_OBJECT_TEXTURE_CHUNK_BASE + sideTex;
						int sideTexIdWall = map.getUsedTextures()[sideTex & 0x7F];

						specialString = ("W (4): " + (fix.getFlags() & 0x0F))
						+ (" H (4): " + ((fix.getFlags() & 0xF0)>>>4))
						+ (" D (2): " + ((fix.getFlags() & 0xFF00)>>>8))
						+ (" Tex-Top: " + map.getUsedTextures()[((fix.getTexture() & 0x7F))])
						+ (" Tex-Side: " + sideTex + " -> " + sideTexId + " | " + sideTexIdWall)
						+ (" FF: " + Integer.toHexString(fix.getFlags()) + " FT: " + tc); 
					} else {
						specialString = " " + fix.toString();
					}
					
					if(mote.getObjectTypeId() == SSObject.OTID_WORDS) {
						byte [] worddata = rm.getData(SSLogic.wordChunk, fix.getWord());
						if(worddata != null) {
							try {
								specialString += " word: " + new String(worddata, "ASCII").trim();
							} catch (UnsupportedEncodingException e) {
							}
						}
					}
				}
			}
			
			if(mote.getOClass() == SSObject.ObjectClass.Containers) {
				Container con = containerInfo.get(mote.getObjectClassIndex());
				if(con!=null) {
					specialString = "CI: " + mote.getObjectClassIndex() + " TBTex: " + con.getTopTexture() + " STex: " + con.getSideTexture();
				}
			}
			
			String objstring =
				object_class[i].toString()
				+ " " + String.format
				(
						Locale.US
//						, "%d (%d-%d %d) (%d %d %d) RT: %s (3D: %d) S:%d Sw:%.3f - Z:%d Zw:%.3f Flags: %04X%s%s"
						, "%d (%d-%d %d) (%d %d %d) RT: %s (3D: %d) Flags: %04X%s"
						, mote.getObjectId()
						//, mote.getXCoord()/256, mote.getXCoord()%256, (map.getHorzSize()-1) - (mote.getYCoord()/256), mote.getYCoord()%256
						, mote.getObjectClass(), mote.getObjectSubClass(), mote.getObjectType()
						, mote.getXAngle(), mote.getYAngle(), mote.getZAngle()
						, (cop!=null)?cop.renderType.toString():"?"
						, cop.model3d_index
//						, object_properties[i].scale
//						, object_properties[i].scale * 2.54
//						, mote.getZCoord()
//						, (mote.getZCoord() * map.getHeightScale() / 8.0) * mapToWorldSpace
						, cop.flags
						, specialString
				);
			float offsetx = object_string_offsets[object_class[i].ordinal()];
			
			// Jedi Mind Trick: The offset must not be rotated because
			// here the TextRenderer is working without rotation matrix.
			// Think about it...
			tr.draw3D(objstring, (float)opos[VecMath.IDX_X] + offsetx, (float)opos[VecMath.IDX_Y], (float)opos[VecMath.IDX_Z], text_scale_3d);
			gl.glFlush();
		}
		tr.end3DRendering();
		
		
		// we could now restore the rotation matrix but we are now
		// done producing (virtual) world objects...

		
		// Draw vis-map
		if(show_vismap)
		{
			gl.glDisable(GL.GL_DEPTH_TEST);
			gl.glLoadIdentity();
			glu.gluOrtho2D(0.0, 1.0, 1.0, 0.0);

			gl.glEnable(GL.GL_BLEND);
			gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
			gl.glColor4d(1.0, 1.0, 1.0, 1.0);

			gl.glEnable(GL.GL_TEXTURE_2D);
			vismaptex.bind(gl);

			gl.glColor3d(1.0, 1.0, 1.0);

			gl.glBegin(GL2.GL_QUADS);

			gl.glTexCoord2d(0.0, 0.0);
			gl.glVertex3d(0.2, 0.2, 1.0);

			gl.glTexCoord2d(1.0, 0.0);
			gl.glVertex3d(0.8, 0.2, 1.0);

			gl.glTexCoord2d(1.0, 1.0);
			gl.glVertex3d(0.8, 0.8, 1.0);

			gl.glTexCoord2d(0.0, 1.0);
			gl.glVertex3d(0.2, 0.8, 1.0);

			gl.glEnd();
			gl.glEnable(GL.GL_DEPTH_TEST);
			gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
			gl.glDisable(GL.GL_BLEND);
		}

		if(cheatCodes.activeCheat(cheatNoHide)) {
			int dir_index = ((int) ((env.cam_rot[VecMath.IDX_Y] + (360.0 + 22.5)) / 45.0)) % 8;

			tr.beginRendering(drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
			tr.setColor(Color.WHITE);

			tr.draw
					(
							String.format
									(
											Locale.US
											, "Pos: %.03fx%.03f h: %.3f r: %.03f %s %s, FPS: %.03f FOV: %.01f %s"
											, env.cam_pos[VecMath.IDX_X] * worldToTileSpace
											, env.cam_pos[VecMath.IDX_Z] * worldToTileSpace
											, env.cam_pos[VecMath.IDX_Y]
											, env.cam_rot[VecMath.IDX_Y]
											, directions[dir_index]
											, (cam_mt != null) ? cam_mt.getType().toString() : "<no tile>"
											, env.fps
											, env.fov_h
											, cheatCodes.activeCheat(cheatFly) ? " FlyMode" : ""
									)
							, 10
							, drawable.getSurfaceHeight() - 20
					);

			tr.draw
					(
							String.format
									(
											Locale.US
											, "TOffs %d Floor %d Ceiling %d Slope %d%s"
											, (cam_mt != null) ? cam_mt.getTextureOffset() : -1
											, (cam_mt != null) ? cam_mt.getFloor() : -1
											, (cam_mt != null) ? cam_mt.getCeiling() : -1
											, (cam_mt != null) ? cam_mt.getSlope() : -1
											, itexs
									)
							, 10
							, drawable.getSurfaceHeight() - 40
					);

			tr.draw
					(
							String.format
									(
											Locale.US
											, "Quads %d Tris %d Sum %d"
											, qcount
											, tcount
											, qcount + tcount
									)
							, 10
							, drawable.getSurfaceHeight() - 60
					);

			tr.draw
					(
							String.format
									(
											Locale.US
											, "View: X %.3f Z %.3f Y %.3f"
											, env.cam_view[VecMath.IDX_X]
											, env.cam_view[VecMath.IDX_Z]
											, env.cam_view[VecMath.IDX_Y]
									)
							, 10
							, drawable.getSurfaceHeight() - 80
					);

			tr.endRendering();
		}

		if(useVertexArrays)
		{
			gl.glEnableClientState(GL2.GL_COLOR_ARRAY);
		}
		
		env.frames++;
		
		glcanvas.repaint();
		
		if(cheatCodes.activeCheat(cheatNoFly))
		{
			cheatCodes.setCheat(cheatFly, false);
			cheatCodes.setCheat(cheatNoFly, false);
		}
		
		if(cheatCodes.activeCheat(cheatHide))
		{
			cheatCodes.setCheat(cheatHide, false);
			cheatCodes.setCheat(cheatNoHide, false);
		}

		if(cheatCodes.activeCheat(cheatNoNormals))
		{
			cheatCodes.setCheat(cheatNormals, false);
			cheatCodes.setCheat(cheatNoNormals, false);
		}
		
		if(cheatCodes.activeCheat(cheatLighting))
		{
			cheatCodes.setCheat(cheatNoLighting, false);
			cheatCodes.setCheat(cheatLighting, false);
		}
		
		if(cheatCodes.activeCheat(cheatNotOnlyPick))
		{
			cheatCodes.setCheat(cheatOnlyPick, false);
			cheatCodes.setCheat(cheatNotOnlyPick, false);
		}
	}
	
	// TODO fetch other aux-pals for cyber-items
	static final int [] cyber_auxpal = { 0x51, 0x48, 0x45, 0x42, 0x39, 0x36 };
	
	static final float model_scale_x = -1.0f;
	static final float model_scale_y = -1.0f;
	static final float model_scale_z =  1.0f;
	
	private void renderNode(GL2 gl, int modelTextureChunk, SSModel model, SSModel.Node node, double dox, double doy, double doz) {
		Texture surfaceTex;
		
		for(SSModel.Surface surface : node.getSurfaces()) {
			double cr = 1.0, cg = 1.0, cb = 1.0;
			boolean glowing_color = false;
			
			SSModel.M3Vector normal = surface.getNormal();
			gl.glNormal3f(normal.getX()*model_scale_x, normal.getY()*model_scale_y, normal.getZ()*model_scale_z);
			
			gl.glEnable(GL.GL_BLEND);
			gl.glDisable(GL.GL_TEXTURE_2D);
			
			if(surface.hasFlag(SSModel.M3F_TEXTURED)) {
				gl.glColor3d(1.0, 1.0, 1.0);
				int texnum = surface.getTexture();
				if(texnum==0) {
					texnum = modelTextureChunk;
				}
				
				surfaceTex = modelTextures.get(texnum);

				gl.glEnable(GL.GL_TEXTURE_2D);
				gl.glDisable(GL.GL_BLEND);

				if(surfaceTex!=null) {
					surfaceTex.bind(gl);
					
					if(use_multitex_glow) {
						gl.glActiveTexture(GL.GL_TEXTURE1);

						Texture glowTex = modelGlowTextures.get(surface.getTexture());

						if(glowTex != null) {

							gl.glColor4d(1.0, 1.0, 1.0, 1.0);
							gl.glEnable(GL.GL_TEXTURE_2D);
							gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_DECAL);
							glowTex.bind(gl);

						} else {
							gl.glDisable(GL.GL_TEXTURE_2D);
						}
						
						gl.glActiveTexture(GL.GL_TEXTURE0);
					}
					
				} else {
					gl.glDisable(GL.GL_TEXTURE_2D);
				}
			} else {
				int cindex;
				if(model.hasFlag(SSModel.M3F_AUX_SHADE)) {
					cindex = cyber_auxpal[(surface.getColor()+5)%6];
				} else {
					cindex = surface.getColor();
				}
				glowing_color = ((cindex > 2) && (cindex < 8 || cindex > 10) && (cindex < 32)) || cindex == 54;
				//glowing_color = !surface.hasFlag(SSModel.M3F_SHADED);
				cr = (((int)palette[cindex*3])&0xFF) / 256.0;
				cg = (((int)palette[cindex*3+1])&0xFF) / 256.0;
				cb = (((int)palette[cindex*3+2])&0xFF) / 256.0;
			}
			
			gl.glColor3d(cr, cg, cb);
			gl.glBegin(GL2.GL_POLYGON);
			
			for(Integer vIndex : surface.getVertexIndices()) {
				SSModel.M3TexCoord tc = surface.getTexCoords().get(vIndex);
				if(tc!=null) {
					if(use_multitex_glow) {
						gl.glMultiTexCoord2d(GL.GL_TEXTURE0, tc.getU() / 256.0, tc.getV() / 256.0);
						gl.glMultiTexCoord2d(GL.GL_TEXTURE1, tc.getU() / 256.0, tc.getV() / 256.0);
					} else {
						gl.glTexCoord2d(tc.getU() / 256.0, tc.getV() / 256.0);
					}
				}
				
				SSModel.M3Vector vec = model.getVertices().get(vIndex);
				
				if(vec!=null) {
					double vx = vec.getX() * model_scale_x;
					double vy = vec.getY() * model_scale_y;
					double vz = vec.getZ() * model_scale_z;
					if(!glowing_color && !cheatCodes.activeCheat(cheatNoLighting)) {
						double darkness = 1.0 - clamp(darkness_at(dox + (vx * mapToWorldSpace), doy + (vy * mapToWorldSpace), doz + (vz * mapToWorldSpace)));
						gl.glColor3d(cr*darkness, cg*darkness, cb*darkness);
					}
					
					gl.glVertex3d(vx, vy, vz);
				}
			}
			
			gl.glEnd();
			
			if(use_multitex_glow) {
				gl.glActiveTexture(GL.GL_TEXTURE1);
				gl.glDisable(GL.GL_TEXTURE_2D);
				gl.glActiveTexture(GL.GL_TEXTURE0);
			}
		}
		
		if(node.getLeft()!=null) {
			renderNode(gl, modelTextureChunk, model, node.getLeft(), dox, doy, doz);
		}
		
		if(node.getRight()!=null) {
			renderNode(gl, modelTextureChunk, model, node.getRight(), dox, doy, doz);
		}
	}
	
	@Override
	public void init(GLAutoDrawable drawable) {
		GL2 gl = drawable.getGL().getGL2();

		AWTMouseAdapter pickAdapter = new AWTMouseAdapter(pickListener, drawable);
		pickAdapter.addTo(glcanvas);

		gl.glClearColor(0, 0, 0, 0);
		gl.glEnable(GL.GL_DEPTH_TEST);
		gl.glEnable(GL.GL_TEXTURE_2D);
		gl.glShadeModel(GL2.GL_SMOOTH);
		// for doors + decals
		gl.glPolygonOffset(-1.0f, 0.0f);

		if (use_multitex_glow) {
			gl.glActiveTexture(GL.GL_TEXTURE1);
			gl.glEnable(GL.GL_TEXTURE_2D);
			gl.glActiveTexture(GL.GL_TEXTURE0);
		}

		gl.glHint(GL2.GL_PERSPECTIVE_CORRECTION_HINT, GL.GL_NICEST);

		//gl.glEnable(GL.GL_ALPHA_TEST);
		//gl.glAlphaFunc(GL.GL_GREATER, 0.1f);
		gl.glDepthFunc(GL.GL_LEQUAL);

		glu = new GLU();

		System.out.println("OpenGL Version: " + gl.glGetString(GL.GL_VERSION));

		if(gl.isExtensionAvailable("GL_ARB_multitexture"))
		{
			System.out.println("GL_ARB_multitexture is available");
			int [] maxtexunits = new int [1];
			gl.glGetIntegerv(GL2.GL_MAX_TEXTURE_UNITS, maxtexunits, 0);

			System.out.println(String.format(" -> System has %d texture units...", maxtexunits[0]));
		} else {
			System.out.println("GL_ARB_multitexture is NOT available");
		}

		tr = new TextRenderer(new Font("SansSerif", Font.BOLD, 14));
		tr.setUseVertexArrays(useVertexArrays);

		initMap(drawable);
	}

	private void initMap(GLAutoDrawable drawable) {
		GL2 gl = drawable.getGL().getGL2();

		old_tile_x = -1;
		old_tile_y = -1;
		old_cdir = -1;

		env.lastTime = -1;
		env.frame_time = -1;

		mapToWorldSpace = env.level_scale / 256.0;
		mapToTextureSpace = 1.0 / 256.0;
		worldToTextureSpace = 1.0 / env.level_scale;
		tileToWorldSpace = env.level_scale;
		worldToTileSpace = 1.0 / env.level_scale;
		texBase = map.getLevelHeight() * this.mapToWorldSpace;

		polygons = new TilePolys [map.getHorzSize()] [map.getVertSize()];

		upper_darkmap = new double [map.getHorzSize()+1] [map.getVertSize()+1];
		lower_darkmap = new double [map.getHorzSize()+1] [map.getVertSize()+1];

		portal_map = new byte [ map.getHorzSize() * map.getVertSize() ];
		visible_tiles = new boolean [map.getHorzSize() * map.getVertSize()];
		blocking_tiles = new boolean [map.getHorzSize() * map.getVertSize()];
		has_drawable = new boolean [map.getHorzSize() * map.getVertSize()];

		vis_info = new int [map.getHorzSize() * map.getVertSize()] [];

		mapObjects = map.getMOTEntries();

		fetchTextures(gl);
		
		buildDarkMaps();
		
		createPolygons();
		
		createObjectVertices();
		
		fetchSpriteTextures(gl);

		fetchDoorTextures(gl);
		
		fetchCritterTextures(gl);
		
		fixtureInfo = SSObject.getFixtureList(rm, map.getNumber());
		containerInfo = SSObject.getContainerList(rm, map.getNumber());

		fetchModels();
		
		fetchModelTextures(gl);
		
		fetchOtherFixtureTextures(gl);
		
		StringBuilder sb = new StringBuilder();
		
		quads_total = 0;
		tris_total = 0;
		
		for(int i=0; i < (map.isCyberspace() ? 1 : textures.length); i++)
		{
			quads_total += tex_quads[i];
			tris_total += tex_tris[i];

			max_quads_per_tex = Math.max(max_quads_per_tex, tex_quads[i]);
			max_tris_per_tex = Math.max(max_tris_per_tex, tex_tris[i]);

			sb.append(String.format("T%d(%d, %d) ", i, tex_quads[i], tex_tris[i]));
		}
		
		sb.append(String.format(" Total (%d, %d) Max (%d, %d)", quads_total, tris_total, max_quads_per_tex, max_tris_per_tex));
		
		System.out.println(sb.toString());

		sortPolygons();
		
		boolean loadVisInfo = configuration.getValueFor(Configuration.LOAD_VIS).get();
		
		if(loadVisInfo) {
			System.out.println("Trying to use cached visibility information...");
		}
		if(loadVisInfo && loadVisInfo())
		{
			System.out.println("Using cached visibility information...");
		}
		else
		{
			System.out.println("Calculating static visibility information...");
			calculate_vis_info();
			System.out.println("...done!");

			if(configuration.getValueFor(Configuration.SAVE_VIS).get()) {
				System.out.println("Saving vis-info...");
				saveVisInfo();
			}
		}
		
		if(useVertexArrays)
		{
			gl.glEnableClientState(GL2.GL_VERTEX_ARRAY);
			gl.glEnableClientState(GL2.GL_TEXTURE_COORD_ARRAY);
			gl.glEnableClientState(GL2.GL_COLOR_ARRAY);
			
			if(use_multitex_glow && !map.isCyberspace())
			{
				gl.glActiveTexture(GL.GL_TEXTURE1);
				gl.glClientActiveTexture(GL.GL_TEXTURE1);
				gl.glEnableClientState(GL2.GL_TEXTURE_COORD_ARRAY);
				gl.glClientActiveTexture(GL.GL_TEXTURE0);
				gl.glActiveTexture(GL.GL_TEXTURE0);
			}

			db_vertex_quad = Buffers.newDirectDoubleBuffer(quad_vertex ,0);
			db_texcoord_quad = Buffers.newDirectDoubleBuffer(quad_tc, 0);
			db_color_quad = Buffers.newDirectDoubleBuffer(quad_dark, 0);
			db_fb_quad = Buffers.newDirectDoubleBuffer(quad_full_bright, 0);
			
			db_vertex_tri = Buffers.newDirectDoubleBuffer(tri_vertex, 0);
			db_texcoord_tri = Buffers.newDirectDoubleBuffer(tri_tc, 0);
			db_color_tri = Buffers.newDirectDoubleBuffer(tri_dark, 0);
			db_fb_tri = Buffers.newDirectDoubleBuffer(tri_full_bright, 0);
		}
		
		ib_index_quad = Buffers.newDirectIntBuffer(map.isCyberspace() ? quads_total * 8 : max_quads_per_tex * 4);
		ib_index_tri = Buffers.newDirectIntBuffer(map.isCyberspace() ? tris_total * 6 :max_tris_per_tex * 3);

		object_string_offsets = new float [SSObject.ObjectClass.values().length];
		int oci = 0;
		for(SSObject.ObjectClass oc : SSObject.ObjectClass.values())
		{
			Rectangle2D bounds = tr.getBounds(oc.toString());
			object_string_offsets[oci++] = -((float) (bounds.getWidth() / 2.0)) * text_scale_3d;
		}

		vismapimg = new BufferedImage(map.getHorzSize(), map.getVertSize(), BufferedImage.TYPE_INT_ARGB);
		vismaptex = AWTTextureIO.newTexture(gl.getGLProfile(), vismapimg, false);
	}

	private void destroyMap(GLAutoDrawable drawable) {
		GL2 gl = drawable.getGL().getGL2();

		// deallocate map texture
		vismaptex.destroy(gl);

		for(Texture t : decalTextures.values()) {
			t.destroy(gl);
		}
		decalTextures.clear();

		for(Texture t : modelGlowTextures.values()) {
			t.destroy(gl);
		}
		modelGlowTextures.clear();

		for(Texture t : modelTextures.values()) {
			t.destroy(gl);
		}
		modelTextures.clear();

		// clear models
		models.clear();

		for(Texture[] tl : doorTextureMap.values()) {
			if(tl == null) continue;

			for(Texture t : tl) {
				t.destroy(gl);
			}
		}
		doorTextureMap.clear();

		for(CenteredTexture ct : objectSprites) {
			if(ct == null) continue;

			if(ct.texture != null) {
				ct.texture.destroy(gl);
			}
		}
		objectSprites = null;

		for(CenteredTexture ct : critterTextures.values()) {
			if(ct.texture != null) {
				ct.texture.destroy(gl);
			}
		}
		critterTextures.clear();

		// some anim textures could be references
		// to other textures
		// destroy will ignore double invocation
		for(Texture t : animGlowTextures.values()) {
			t.destroy(gl);
		}
		animGlowTextures.clear();

		for(Texture t : animTextures.values()) {
			t.destroy(gl);
		}
		animTextures.clear();

		for(Texture t : glow_tex) {
			if(t == null) continue;
			t.destroy(gl);
		}
		glow_tex = null;

		for(Texture t : textures) {
			if(t == null) continue;
			t.destroy(gl);
		}
		textures = null;
	}

	private void loadMapInternal(GLAutoDrawable drawable, SSMap map) {
		if(map==null) return;
		if(map == this.map) return;
		destroyMap(drawable);
		this.map = map;
		initMap(drawable);
	}

	public void loadMap(SSMap map) {
		this.nextMap = map;
	}
	
	private int getPackedSize(int width, int height)
	{
		// Java guarantees 32bit integers 
		return height * ((width + 31)/32);
	}
	
	private void calculate_vis_info()
	{
		boolean [] tile_vis_info = new boolean [map.getHorzSize() * map.getVertSize()];
		int packed_size = getPackedSize(map.getHorzSize(), map.getVertSize());
		
		for(int y = 0; y < map.getVertSize(); y++) {
			int yoffs = y * map.getHorzSize();
			for(int x = 0; x < map.getHorzSize(); x++) {
				if(!has_drawable[yoffs + x])
					continue;
				
				TileVisivility.line_visibility(has_drawable, map.getHorzSize(), map.getVertSize(), x, y, tile_vis_info);
				
				vis_info[yoffs + x] = new int [packed_size];
				
				int vis_index = 0;
				for(int vis_y = 0; vis_y < map.getVertSize(); vis_y++) {
					int vis_yoffs = vis_y * map.getHorzSize();
					int vis_x = 0;
					while(vis_x < map.getHorzSize()) {
						int info = 0;
						for(int bit = 0; bit < 32; bit++) {
							if(vis_x == map.getHorzSize())
								break;
							if(tile_vis_info[vis_yoffs + vis_x]) {
								info |= (1 << bit);
							}
							vis_x++;
						}
						vis_info[yoffs + x][vis_index] = info;
						vis_index++;
					}
				}
			}
		}
	}

	@Override
	public void reshape(GLAutoDrawable drawable, int x, int y, int width,
			int height) {
		double aspect = (double)width / (double)height;
		env.aspect = aspect;
		GL2 gl = drawable.getGL().getGL2();
		
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glLoadIdentity();
		glu.gluPerspective(env.fov_h, aspect, 0.1, 100.0);
		env.fov_update = false;
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glLoadIdentity();
	}
	
	public static class MapRunnable implements Runnable {
		private Map3D map;
		
		public Map3D getMap() {
			return map;
		}
		
		public MapRunnable(Map3D map) {
			this.map = map;
		}
		
		public void run() {
			map.setup();
		}
	}

	public static Map3D createMap3D(Configuration configuration, ResManager rm, SSMap map, byte [] palette, TextureProperties textureProperties)
	{
		MapRunnable mr = new MapRunnable(new Map3D(configuration, rm, map, palette, textureProperties));
		
		SwingUtilities.invokeLater(mr);
		
		return mr.getMap();
	}

	@Override
	public void windowActivated(WindowEvent e) {
	}

	@Override
	public void windowClosed(WindowEvent e) {
		System.out.println("Closing down...");
		mapExit(MapExitEvent.Exit, 0);
	}

	@Override
	public void windowClosing(WindowEvent e) {
	}

	@Override
	public void windowDeactivated(WindowEvent e) {
	}

	@Override
	public void windowDeiconified(WindowEvent e) {
	}

	@Override
	public void windowIconified(WindowEvent e) {
	}

	@Override
	public void windowOpened(WindowEvent e) {
	}
		
	private class TilePolys
	{
		private static final int MAX_TILE_QUADS = 2 + 4 * 2;
		private static final int MAX_TILE_TRIS = 4;
		private int [] quad_textures;
		private int num_quads;
		private double [] quad;
		private double [] quad_tc;
		private double [] quad_plane;
		private double [] quad_dark;
		private PolygonType [] q_type;
		private int [] tri_textures;
		private int num_tris;
		private double [] tri;
		private double [] tri_tc;
		private double [] tri_plane;
		private double [] tri_dark;
		private PolygonType [] t_type;
		
		public TilePolys()
		{
			quad_textures = new int [MAX_TILE_QUADS];
			
			quad = new double [MAX_TILE_QUADS * 4 * 3];
			quad_tc = new double [MAX_TILE_QUADS * 4 * 2];
			quad_plane = new double [MAX_TILE_QUADS * 4];
			quad_dark = new double [MAX_TILE_QUADS * 4 * 3];

			q_type = new PolygonType [MAX_TILE_QUADS];
			
			tri_textures = new int [MAX_TILE_TRIS];

			tri = new double [MAX_TILE_TRIS * 3 * 3];
			tri_tc = new double [MAX_TILE_TRIS * 3 * 2];
			tri_plane = new double [MAX_TILE_TRIS * 4];
			tri_dark = new double [MAX_TILE_TRIS * 3 * 3];

			t_type = new PolygonType [MAX_TILE_TRIS];
		}
		
		public void addQuad
		(
			  double tc_u1, double tc_v1 
			, double x1, double y1, double z1
			, double c1r, double c1g, double c1b
			, double tc_u2, double tc_v2 
			, double x2, double y2, double z2
			, double c2r, double c2g, double c2b
			, double tc_u3, double tc_v3 
			, double x3, double y3, double z3
			, double c3r, double c3g, double c3b
			, double tc_u4, double tc_v4 
			, double x4, double y4, double z4
			, double c4r, double c4g, double c4b
			, double nx, double ny, double nz, double d
			, int tex
			, PolygonType type
		)
		{
			if(num_quads==MAX_TILE_QUADS)
				return;
			
			quad[num_quads * 4 * 3 + 0] = x1;
			quad[num_quads * 4 * 3 + 1] = y1;
			quad[num_quads * 4 * 3 + 2] = z1;
			quad[num_quads * 4 * 3 + 3] = x2;
			quad[num_quads * 4 * 3 + 4] = y2;
			quad[num_quads * 4 * 3 + 5] = z2;
			quad[num_quads * 4 * 3 + 6] = x3;
			quad[num_quads * 4 * 3 + 7] = y3;
			quad[num_quads * 4 * 3 + 8] = z3;
			quad[num_quads * 4 * 3 + 9] = x4;
			quad[num_quads * 4 * 3 + 10] = y4;
			quad[num_quads * 4 * 3 + 11] = z4;
			
			quad_tc[num_quads * 4 * 2 + 0] = tc_u1; 
			quad_tc[num_quads * 4 * 2 + 1] = tc_v1; 
			quad_tc[num_quads * 4 * 2 + 2] = tc_u2; 
			quad_tc[num_quads * 4 * 2 + 3] = tc_v2; 
			quad_tc[num_quads * 4 * 2 + 4] = tc_u3; 
			quad_tc[num_quads * 4 * 2 + 5] = tc_v3; 
			quad_tc[num_quads * 4 * 2 + 6] = tc_u4; 
			quad_tc[num_quads * 4 * 2 + 7] = tc_v4; 
			
			quad_textures[num_quads] = tex;
			
			quad_plane[num_quads * 4 + 0] = nx;
			quad_plane[num_quads * 4 + 1] = ny;
			quad_plane[num_quads * 4 + 2] = nz;
			quad_plane[num_quads * 4 + 3] = d;

			quad_dark[num_quads * 4 * 3 + 0] = c1r;
			quad_dark[num_quads * 4 * 3 + 1] = c1g;
			quad_dark[num_quads * 4 * 3 + 2] = c1b;
			
			quad_dark[num_quads * 4 * 3 + 3] = c2r;
			quad_dark[num_quads * 4 * 3 + 4] = c2g;
			quad_dark[num_quads * 4 * 3 + 5] = c2b;
			
			quad_dark[num_quads * 4 * 3 + 6] = c3r;
			quad_dark[num_quads * 4 * 3 + 7] = c3g;
			quad_dark[num_quads * 4 * 3 + 8] = c3b;
			
			quad_dark[num_quads * 4 * 3 + 9] = c4r;
			quad_dark[num_quads * 4 * 3 + 10] = c4g;
			quad_dark[num_quads * 4 * 3 + 11] = c4b;
			
			q_type[num_quads] = type;
			
			num_quads++;
		}
		
		public void addTri
		(
			  double tc_u1, double tc_v1 
			, double x1, double y1, double z1
			, double c1r, double c1g, double c1b
			, double tc_u2, double tc_v2 
			, double x2, double y2, double z2
			, double c2r, double c2g, double c2b
			, double tc_u3, double tc_v3 
			, double x3, double y3, double z3
			, double c3r, double c3g, double c3b
			, double nx, double ny, double nz, double d
			, int tex
			, PolygonType type
		)
		{
			tri[num_tris * 3 * 3 + 0] = x1;
			tri[num_tris * 3 * 3 + 1] = y1;
			tri[num_tris * 3 * 3 + 2] = z1;
			tri[num_tris * 3 * 3 + 3] = x2;
			tri[num_tris * 3 * 3 + 4] = y2;
			tri[num_tris * 3 * 3 + 5] = z2;
			tri[num_tris * 3 * 3 + 6] = x3;
			tri[num_tris * 3 * 3 + 7] = y3;
			tri[num_tris * 3 * 3 + 8] = z3;
			
			tri_tc[num_tris * 3 * 2 + 0] = tc_u1; 
			tri_tc[num_tris * 3 * 2 + 1] = tc_v1; 
			tri_tc[num_tris * 3 * 2 + 2] = tc_u2; 
			tri_tc[num_tris * 3 * 2 + 3] = tc_v2; 
			tri_tc[num_tris * 3 * 2 + 4] = tc_u3; 
			tri_tc[num_tris * 3 * 2 + 5] = tc_v3; 
			
			tri_textures[num_tris] = tex;
			
			tri_plane[num_tris * 4 + 0] = nx;
			tri_plane[num_tris * 4 + 1] = ny;
			tri_plane[num_tris * 4 + 2] = nz;
			tri_plane[num_tris * 4 + 3] = d;

			tri_dark[num_tris * 3 * 3 + 0] = c1r;
			tri_dark[num_tris * 3 * 3 + 1] = c1g;
			tri_dark[num_tris * 3 * 3 + 2] = c1b;
			
			tri_dark[num_tris * 3 * 3 + 3] = c2r;
			tri_dark[num_tris * 3 * 3 + 4] = c2g;
			tri_dark[num_tris * 3 * 3 + 5] = c2b;
			
			tri_dark[num_tris * 3 * 3 + 6] = c3r;
			tri_dark[num_tris * 3 * 3 + 7] = c3g;
			tri_dark[num_tris * 3 * 3 + 8] = c3b;
			
			t_type[num_tris] = type;			
			
			num_tris++;
		}

	}
	
	private void buildDarkMaps()
	{
		for(int x=0; x<map.getHorzSize(); x++)
		{
			for(int y=0; y<map.getVertSize(); y++)
			{
				int my = (map.getVertSize()-1)-y;
				MapTile mt = map.getTile(x, my);
				
				int ld = mt.getLowerShade() - ((mt.getState()&0x0f000000)>>>24);
				int hd = mt.getUpperShade() - (((mt.getState()&0xf0000000)>>>28)&0x0f);
				
				int fh = mt.getFloor();
				int ch = mt.getCeiling();
				
				if(ld == hd || fh == ch)
				{
					upper_darkmap [x] [y] = hd / 15.0;
					lower_darkmap [x] [y] = ld / 15.0;
				}
				else
				{
					int sdelta = hd - ld;
					int dist = ch - fh;
					double dark_slope = (double)sdelta / (double)dist;
					lower_darkmap [x] [y] = (ld - dark_slope * fh) / 15.0;
					upper_darkmap [x] [y] = (hd + dark_slope * (map.getLevelHeight() - ch)) / 15.0;
				}
			}
		}
		
		// create safe border - there are no tiles using this area but there _could_ be...
		for(int x=0; x<map.getHorzSize()+1; x++)
		{
			lower_darkmap [x] [map.getVertSize()] = 0.0;
			upper_darkmap [x] [map.getVertSize()] = 1.0;
		}
		for(int y=0; y<map.getVertSize()+1; y++)
		{
			lower_darkmap [map.getHorzSize()] [y] = 0.0;
			upper_darkmap [map.getHorzSize()] [y] = 1.0;
		}
		
		if(darkness_debug)
		{
			try {
				FileWriter upper_image = new FileWriter("my_upper.pgm");
				upper_image.write("P2\n65 65\n255\n");
				StringBuilder sb = new StringBuilder();
				for(int y=0; y<=map.getVertSize(); y++)
				{
					sb.setLength(0);
					for(int x=0; x<=map.getHorzSize(); x++)
					{
						sb.append(Integer.toString((int)(clamp(upper_darkmap[x][y])*255.0)));
						sb.append(" ");
					}
					sb.append("\n");
					upper_image.write(sb.toString());
				}
				upper_image.close();

				FileWriter lower_image = new FileWriter("my_lower.pgm");
				lower_image.write("P2\n65 65\n255\n");
				for(int y=0; y<=map.getVertSize(); y++)
				{
					sb.setLength(0);
					for(int x=0; x<=map.getHorzSize(); x++)
					{
						sb.append(Integer.toString((int)(clamp(lower_darkmap[x][y])*255.0)));
						sb.append(" ");
					}
					sb.append("\n");
					lower_image.write(sb.toString());
				}
				lower_image.close();


			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public double darkness_at(double x, double y, double z)
	{
		int xl = (int)(x * worldToTileSpace);
		int xh = (int)((x + tileToWorldSpace) * worldToTileSpace);
		int yl = (int)(y * worldToTileSpace);
		int yh = (int)((y - tileToWorldSpace) * worldToTileSpace);
		
		double xweight = x - (int)x;
		double yweight = y - (int)y;
		
		double l_d_ll = lower_darkmap[xl][yl];
		double l_d_lh = lower_darkmap[xl][yh];
		double l_d_hl = lower_darkmap[xh][yl];
		double l_d_hh = lower_darkmap[xh][yh];

		double u_d_ll = upper_darkmap[xl][yl];
		double u_d_lh = upper_darkmap[xl][yh];
		double u_d_hl = upper_darkmap[xh][yl];
		double u_d_hh = upper_darkmap[xh][yh];
		
		double d_ll_slope = u_d_ll - l_d_ll;
		double d_lh_slope = u_d_lh - l_d_lh;
		double d_hl_slope = u_d_hl - l_d_hl;
		double d_hh_slope = u_d_hh - l_d_hh;
		
		double zscale = map.getLevelHeight() * mapToWorldSpace;
		
		double d_ll = l_d_ll + d_ll_slope * z / zscale;
		double d_lh = l_d_lh + d_lh_slope * z / zscale;
		double d_hl = l_d_hl + d_hl_slope * z / zscale;
		double d_hh = l_d_hh + d_hh_slope * z / zscale;

		double d_xl = d_ll * (1.0 - xweight) + d_hl * xweight;
		double d_xh = d_lh * (1.0 - xweight) + d_hh * xweight;
		double d_y = d_xl * yweight + d_xh * (1.0 - yweight);
		
		return d_y;
	}
	
	public void createObjectVertices()
	{
		object_vertex = new double [mapObjects.size() * 6];
		object_rotation = new double [mapObjects.size() * 3];
		object_class = new SSObject.ObjectClass [mapObjects.size()];
		object_class_index = new int [mapObjects.size()];
		object_id = new int [mapObjects.size()];
		object_properties = new SSObject.CommonObjectProperty [mapObjects.size()];
		object_darkness = new double [mapObjects.size()];
		
		mORIndex = new int [mapObjects.size()];
		
		int used_objects = 0;
		
		int mOIndex = -1;
		int oindex = 0;
		for(SSObject.MOTEntry mote : mapObjects)
		{
			mOIndex++;
			
			if(!mote.isInUse())
				continue;
			
			used_objects++;
			
			mORIndex[oindex] = mOIndex;
			
			int maptilex = mote.getXCoord() >>> 8;
			int my = mote.getYCoord() >>> 8;
			int maptiley = (map.getVertSize() - 1) - my;
			
			
			
			//double ontilex = ((((double)mote.getXCoord())/256.0) - maptilex);
			//double ontiley = 1.0 - ((((double)mote.getYCoord())/256.0) - my);
			int w_ontilex = mote.getXCoord() % 256;
			int w_ontiley = mote.getYCoord() % 256;
			
			// object position fudge (from tsshp)
			if(w_ontilex == 255)
				w_ontilex = 256;
			if(w_ontiley == 255)
				w_ontiley = 256;
			
			// door position fudge (from tsshp)
			if(mote.getOClass() == SSObject.ObjectClass.DoorsAndGratings) {
				if(w_ontilex == 127)
					w_ontilex = 128;
				if(w_ontiley == 127)
					w_ontiley = 128;
			}
			
			/*
			if(w_ontiley == 256 || w_ontiley == 256) {
				if(mote.getOClass() == SSObject.ObjectClass.DoorsAndGratings) {
					System.out.println("Door fudge executed!");
				} else {
					System.out.println("Door fudge executed for non-door! " + mote.getOClass());
				}
			}
			*/
			
			double ontilex = w_ontilex / 256.0;
			double ontiley = 1.0 - (w_ontiley / 256.0);

			double oposx = (maptilex + ontilex) * tileToWorldSpace;
			double oposy = (maptiley + ontiley) * tileToWorldSpace;

			MapTile mt = map.getTile(maptilex, my);
			if(mt==null)
			{
				System.err.println("No such tile: " + maptilex + " " + my + " (" + mote.getOClass() + ")");
				continue;
			}
			double ontilez = (mote.getZCoord() * map.getHeightScale() / 8.0) * mapToWorldSpace;
			
			//double fh = mt.getFloorAt(ontilex, ontiley) * mapToWorldSpace;
			double fh = ontilez;
			
			object_vertex[oindex * 6] = oposx;
			object_vertex[oindex * 6 + 1] = fh;
			object_vertex[oindex * 6 + 2] = oposy;
			object_vertex[oindex * 6 + 3] = oposx;
			object_vertex[oindex * 6 + 4] = fh + 1.0;
			object_vertex[oindex * 6 + 5] = oposy;
			
			object_class[oindex] = mote.getOClass();
			object_class_index[oindex] = mote.getObjectClassIndex();
			object_id[oindex] = mote.getObjectId();
			object_properties[oindex] = mote.getCommonProperty();
			
			object_darkness[oindex] = 1.0 - clamp(darkness_at(oposx, oposy, fh));
			
			object_rotation[oindex * 3] =  (mote.getXAngle() / 256.0) * Math.PI * 2.0;
			// and the coordinate mirror nightmare continues...
			object_rotation[oindex * 3 + 1] = -(mote.getYAngle() / 256.0) * Math.PI * 2.0;
			object_rotation[oindex * 3 + 2] = (mote.getZAngle() / 256.0) * Math.PI * 2.0;
			
			oindex++;
		}
		
		valid_objects = oindex;
		
		System.out.println("There are " + valid_objects + " objects in this map");
	}
	
	public void printPortalMap()
	{
		StringBuilder sbt = new StringBuilder();
		StringBuilder sbm = new StringBuilder();
		StringBuilder sbb = new StringBuilder();
		
		for(int y=0; y<map.getVertSize()-1; y++)
		{
			int offs = y * map.getHorzSize();
			for(int x=0; x<map.getHorzSize()-1; x++)
			{
				MapTile mt = map.getTile(x, (map.getVertSize()-1)-y);
				byte p = portal_map[offs + x];
				char mc = (mt.getType() == MapTile.Type.Solid)?' ':'X';
				
				sbt.append( ((p & P_NORTH) != 0)?" N ":"   ");
				sbm.append( ((p & P_WEST) != 0)?"W":" " );
				sbm.append(mc);
				sbm.append( ((p & P_EAST) != 0)?"E":" " );
				sbb.append( ((p & P_SOUTH) != 0)?" S ":"   ");
			}
			System.out.println(sbt.toString());
			System.out.println(sbm.toString());
			System.out.println(sbb.toString());
			
			sbt.setLength(0);
			sbm.setLength(0);
			sbb.setLength(0);
		}
	}
	
	private void markBlockingTiles(int xpos, int ypos, byte avoid_portal)
	{
		int toffs = ypos * map.getHorzSize() + xpos;
		byte tile_portals = portal_map[toffs];

		for(int i=0; i<portals.length; i++)
		{
			byte p = portals[i];
			
			if((p&avoid_portal)!=0)
				continue;
			
			int poffsx = portal_offsets[i*2];
			int poffsy = portal_offsets[i*2+1];
			
			int poffsmin =
				(poffsx==0)
				? xpos 
				: ypos * map.getHorzSize();
			// max + 1 really...
			int poffsmax = 
				(poffsx==0) 
				? ((map.getVertSize()-1) * map.getHorzSize()) + xpos 
				: ((ypos + 1) * map.getHorzSize())-1;
				
			if(poffsmin < 0)
				poffsmin = 0;
			
			if(poffsmax > visible_tiles.length)
				poffsmax = visible_tiles.length;
			
			int poffs = toffs + (portal_offsets[i*2]) + (portal_offsets[i*2+1]*map.getHorzSize());
			byte pportals;
			
			boolean blocks = (tile_portals & p) == 0;
			
			while(poffs >= poffsmin && poffs < poffsmax)
			{
				blocking_tiles[poffs] = blocks;
				
				pportals = portal_map[poffs];
				if(!blocks)
					blocks = (pportals & p) == 0;
				
				poffs += poffsx + (poffsy * map.getHorzSize());
			}
		}
	}
	
	private void markVisibleTiles(int xpos, int ypos, byte avoid_portal)
	{
		int toffs = ypos * map.getHorzSize() + xpos;
		
		int [] tile_vis_info = vis_info[toffs];
		
		// starting on a blocker... bad idea...
		if(tile_vis_info==null)
			return;

		Queue<Integer> queue = new LinkedList<Integer>();
		
		queue.offer(Integer.valueOf(toffs));
		
		while(!queue.isEmpty())
		{
			Integer ioffs = queue.remove();
			toffs = ioffs.intValue();
			byte tile_portals = portal_map[toffs];
			
			if(use_static_visibility) {
				// TODO: only works because map size is multiple of 32
				// needs general fix
				int info_index = toffs / 32;
				int info_bit = toffs % 32;

				if( (tile_vis_info[info_index] & (1<<info_bit)) == 0)
					continue;
			}
			
			if(visible_tiles[toffs])
				continue;
			
			if(use_vis_blocker)
			{
				if(blocking_tiles[toffs])
					continue;
			}
			
			visible_tiles[toffs] = true;
			
			for(int i=0; i<portals.length; i++)
			{
				byte p = portals[i];
				
				if( (tile_portals & p) == 0 )
					continue;
				
				if( (p & avoid_portal) != 0)
					continue;
				
				int poffs = toffs + (portal_offsets[i*2]) + (portal_offsets[i*2+1]*map.getHorzSize());
				
				if(poffs < 0 || poffs > visible_tiles.length)
					continue;
				
				if(visible_tiles[poffs])
					continue;
				
				queue.offer(Integer.valueOf(poffs));
			}
		}
	}
	
	private void computeVisibleTiles(int xpos, int ypos, byte avoid_portal)
	{
		Arrays.fill(visible_tiles, false);
		if(use_vis_blocker)
		{
			Arrays.fill(blocking_tiles, false);
			markBlockingTiles(xpos, ypos, avoid_portal);
		}
		markVisibleTiles(xpos, ypos, avoid_portal);
	}
	
	private void allVisible()
	{
		System.arraycopy(has_drawable, 0, visible_tiles, 0, has_drawable.length);
	}
	
	private void updateVismapTex(GL2 gl, int cx, int cy)
	{
		for(int y=0; y<map.getVertSize(); y++)
		{
			int yoffs = y * map.getHorzSize();
			for(int x=0; x<map.getHorzSize(); x++)
			{
				int color = visible_tiles[yoffs + x]?0x8800FFFF:( has_drawable[yoffs + x]?0x660000FF:0x00000000 );
				
				if(cx == x && cy == y)
					color = 0xEEFF0000;
				
				if(use_vis_blocker)
				{
					if(blocking_tiles[yoffs + x])
						color = 0x7700FF00;
				}
				
				vismapimg.setRGB(x, y, color);
			}
		}
		TextureData texdat = AWTTextureIO.newTextureData(gl.getGLProfile(), vismapimg, false);
		vismaptex.updateImage(gl, texdat);
	}
	
	private boolean loadVisInfo()
	{
		Path visBase = Path.of(configuration.getValueFor(Configuration.CONFIG_PATH).orElse("."));
		byte [] maphash = map.getTileHash();
		File f = visBase.resolve("visinfo_l" + map.getNumber()).toFile();
		
		byte [] cachehash = new byte [maphash.length];
		int packed_size = getPackedSize(map.getHorzSize(), map.getVertSize());
		
		if(f.exists() && f.canRead()) {
			try {
				DataInputStream dis = new DataInputStream(new FileInputStream(f));
				
				int r = dis.read(cachehash);
				
				if(r != maphash.length)
					return false;
				
				if(Utils.sameArrays(maphash, cachehash))
				{
					System.out.println("Cached vis-info seems valid...");
					for(int y=0; y<map.getVertSize(); y++)
					{
						//System.out.print(String.format("Line %02d: ", y));
						for(int x = 0; x < map.getHorzSize(); x++)
						{
							byte hasinfo = dis.readByte();
							
							if(hasinfo!=0)
							{
								//System.out.print(".");
								int [] tile_vis_info = new int [packed_size];
								
								for(int j=0; j < tile_vis_info.length; j++)
								{
									tile_vis_info[j] = dis.readInt();
								}
								
								vis_info[y * map.getHorzSize() + x] = tile_vis_info;
							}
							else
							{
								//System.out.print("-");
								vis_info[y * map.getHorzSize() + x] = null;
							}
						}
						//System.out.println();
					}
				}
				
				System.out.println("Cached vis-info loaded!");
				
			} catch (FileNotFoundException e) {
				System.out.println("No cached vis info found...");
				return false;
			} catch (IOException e) {
				System.out.println("IO Error reading cached vis info... ");
				return false;
			}
		}
		else
		{
			System.out.println("No cached vis-info found...");
			return false;
		}
		
		return true;
	}
	
	private boolean saveVisInfo()
	{
		Path visBase = Path.of(configuration.getValueFor(Configuration.CONFIG_PATH).orElse("."));
		byte [] maphash = map.getTileHash();
		File f = visBase.resolve("visinfo_l" + map.getNumber()).toFile();

		ByteArrayOutputStream baos = new ByteArrayOutputStream(64000);
		DataOutputStream dos = new DataOutputStream(baos);
		
		try {
			dos.write(maphash);
			
			for(int y = 0; y < map.getVertSize(); y++)
			{
//				System.out.print(String.format("Line %02d: ", y));
				for(int x = 0; x < map.getHorzSize(); x++)
				{
					int [] tile_vis_info = vis_info[y * map.getHorzSize() + x];
					
					if(tile_vis_info == null)
					{
//						System.out.print("-");
						dos.writeByte(0);
					}
					else
					{
//						System.out.print(".");
						dos.writeByte(1);
						for(int j=0; j<tile_vis_info.length; j++)
						{
							dos.writeInt(tile_vis_info[j]);
						}
					}
				}
//				System.out.println();
			}
			
			dos.flush();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		
		try {
			FileOutputStream fos = new FileOutputStream(f);
			fos.write(baos.toByteArray());
			fos.close();
			System.out.println("Vis-info saved!");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	private boolean mapExit(MapExitListener.MapExitEvent mee, int param) {
		boolean exitOK = listeners.isEmpty() ? true : false;
		for(MapExitListener meel : listeners) {
			if(meel.onMapExit(mee, param))
				exitOK = true;
		}
		
		return exitOK;
	}
	
	private void initPickColors() {
		pickColorIndex = 0;
	}
	
	private int getNextPickColor() {
		int color;
		if(pickColorIndex >= pickColors.size()) {
			while(pickColors.contains(color = rnd.nextInt(0x1000000) | 0x030303));
			pickColors.add(color);
		} else {
			color = pickColors.get(pickColorIndex);
		}
		pickColorIndex++;
		return color;
	}

	@Override
	public void dispose(GLAutoDrawable drawable) {
		System.out.println("Disposing...");
		JOGLKeyboard.removeKeyReleaseListener(cheatCodes);
		JOGLKeyboard.removeKeyReleaseListener(toggles);
		JOGLKeyboard.dispose();
		destroyMap(drawable);
	}
}
