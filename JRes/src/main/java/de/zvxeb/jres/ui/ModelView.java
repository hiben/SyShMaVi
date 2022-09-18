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

import java.awt.BorderLayout;

import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.JFrame;

import com.jogamp.opengl.*;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.glu.GLU;
import de.zvxeb.jres.ResBitmap;
import de.zvxeb.jres.ResManager;
import de.zvxeb.jres.SSModel;
import de.zvxeb.jkeyboard.KeyPressListener;
import de.zvxeb.jkeyboard.jogl.JOGLKeyboard;

import com.jogamp.opengl.util.FPSAnimator;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;

public class ModelView extends JFrame implements GLEventListener, KeyPressListener {
	// 2306 large screen
	// 2307 computer screen
	// 2308 computer node
	// 2309 table
	// 2310 table
	// 2312 shelf
	// 2314 chair
	// 2315 table
	// 2320 cart
	// 2321 healing station
	// 2329 camera
	// 2352/2353 cybertoggle
	// 2350 power station
	// 2349 cyberjack
	// 2347 cyberinfo
	// 2344 cyber access
	// 2342 cyber health
	// 2331 computer terminal (numbers)
	private static final long serialVersionUID = 1L;
	
	// TODO fetch other aux-pals for cyber-items
	static final int [] cyber_auxpal = { 0x51, 0x48, 0x45, 0x42, 0x39, 0x36 };
	
	private ResManager rm;
	
	private byte [] palette;
	
	private GLCanvas glcanvas;
	private GLU glu;
	
	private static final int IDX_X = 0;
	private static final int IDX_Y = 1;
	private static final int IDX_Z = 2;
	
	private double [] cam_pos = new double [3];
	private double [] cam_rot = new double [3];
	
	private double scale = 1.0 / 10.0;

	private double scale_x = -1.0 * scale;
	private double scale_y = -1.0 * scale;
	private double scale_z = 1.0 * scale;
	
	private SSModel model;
	private int objectClassIndex;
	
	private int onlyNode = -1;
	private boolean requestInfo = false;
	
	private int onlySurface = -1;
	
	private Texture [] textures;
	private int modelTextureChunk;
	
	private Map<Integer, Texture> texMap = new TreeMap<Integer, Texture>();
	
	int numVertices;
	
	long lastTime = -1L;
	
	//private int key_forward = KeyEvent.VK_W;
	private int key_forward = KeyEvent.VK_W;
	private int key_backward = KeyEvent.VK_S;
	private int key_left = KeyEvent.VK_A;
	private int key_right = KeyEvent.VK_D;
	
	private int key_down = KeyEvent.VK_F;
	private int key_up = KeyEvent.VK_R;

	//private int key_turn_left = KeyEvent.VK_LEFT;
	private int key_turn_left = KeyEvent.VK_LEFT;
	private int key_turn_right = KeyEvent.VK_RIGHT;

	private int key_yaw_up = KeyEvent.VK_DOWN;
	private int key_yaw_down = KeyEvent.VK_UP;

	private int key_runmod = KeyEvent.VK_SHIFT;
	
	private double speed = 1.5;
	private double turn_speed = 60.0;
	private double run_mod = 3.0;
	
	private FPSAnimator fpsAni;
	private int maxFPS = 60;
	
	public void dispose() {
		super.dispose();
		glcanvas.removeGLEventListener(this);
		JOGLKeyboard.dispose();
	}
	
	private void fetchTextures(GL gl) {
		textures = new Texture [model.getUsedTextures().size() + (model.usesObjectTexture() ? 1 : 0)];
		
		int tindex = 0;
		for(Integer texid : model.getUsedTextures()) {
			System.out.println("Loading texture #" + texid);
			byte [] texdata = rm.getData(texid, 0);
			ResBitmap rb = new ResBitmap(texdata, (short)texid.intValue(), 0);
			BufferedImage bi = rb.getARGBImage(palette);
			textures[tindex] = AWTTextureIO.newTexture(gl.getGLProfile(), bi, true);
			textures[tindex].setTexParameteri(gl, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
			textures[tindex].setTexParameteri(gl, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
			textures[tindex].setTexParameteri(gl, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
			textures[tindex].setTexParameteri(gl, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
			
			
			texMap.put(Integer.valueOf(texid), textures[tindex]);
			
			tindex++;
		}
		
		if(model.usesObjectTexture()) {
			modelTextureChunk = SSModel.MODEL_OBJECT_TEXTURE_CHUNK_BASE + objectClassIndex;
			System.out.println("Loading model-texture #" + modelTextureChunk);
			byte [] texdata = rm.getData(modelTextureChunk, 0);
			if(texdata != null) {
				ResBitmap rb = new ResBitmap(texdata, (short)modelTextureChunk, 0);
				BufferedImage bi = rb.getARGBImage(palette);
				textures[tindex] = AWTTextureIO.newTexture(gl.getGLProfile(), bi, true);
				textures[tindex].setTexParameteri(gl, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
				textures[tindex].setTexParameteri(gl, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
				textures[tindex].setTexParameteri(gl, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
				textures[tindex].setTexParameteri(gl, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
				
				
				texMap.put(Integer.valueOf(modelTextureChunk), textures[tindex]);
			} else {
				System.err.println("Unable to load model texture #"+ modelTextureChunk);
			}
		}
		
		System.out.println("Prepared " + tindex + " textures");
	}
	
	public void init(GLAutoDrawable drawable) {
		GL gl = drawable.getGL();
		
		gl.glClearColor(0, 0, 0, 0);
		gl.glEnable(GL.GL_DEPTH_TEST);
		gl.glDepthFunc(GL.GL_LEQUAL);
		
		glu = new GLU();
		
		fetchTextures(gl);
	}
	
	public void reshape(GLAutoDrawable drawable, int x, int y, int w, int h) {
		double aspect = (double)w / (double)h;
		GL2 gl = drawable.getGL().getGL2();
		
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glLoadIdentity();
		glu.gluPerspective(60.0, aspect, 0.1, 100.0);
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glLoadIdentity();
	}

	public void displayChanged(GLAutoDrawable drawable, boolean arg1, boolean arg2) {
	}
	
	
	public void renderNode(GL2 gl, SSModel m, SSModel.Node n, String tag) {
		Texture tex = null;
		
		int nodeLevel = -1;
		
		if(tag!=null) {
			nodeLevel = 0;
			for(int ci = 0; ci < tag.length(); ci++) {
				if(tag.charAt(ci)=='.')
					nodeLevel++;
			}
		}
		
		if(onlyNode == -1 || nodeLevel == onlyNode) {
			
		if(requestInfo) {
			System.out.println("There are " + n.getSurfaces().size() + " surfaces in this node " + tag);
		}
		
		int surfaceIndex = 0;
		for(SSModel.Surface s : n.getSurfaces()) {
			if(onlySurface == -1 || surfaceIndex == onlySurface) {
				
			if(requestInfo) {
				System.out.println("Drawing surface from " + tag + " sindex " + surfaceIndex + " " + s);
			}
			
			SSModel.M3Vector normal = s.getNormal();
			
			gl.glNormal3d(normal.getX() * scale_x, normal.getY() * scale_y, normal.getZ() * scale_z);
			
			gl.glEnable(GL.GL_BLEND);
			gl.glDisable(GL.GL_TEXTURE_2D);
			
			if(s.hasFlag(SSModel.M3F_TEXTURED)) {
				gl.glColor3d(1.0, 1.0, 1.0);
				int texnum = s.getTexture();
				if(texnum == 0)
					texnum = modelTextureChunk;
				tex = texMap.get(Integer.valueOf(texnum));
				
				if(tex!=null) {
					gl.glEnable(GL.GL_TEXTURE_2D);
					gl.glDisable(GL.GL_BLEND);
					tex.bind(gl);
				}
			} else {
				int cindex;
				if(model.hasFlag(SSModel.M3F_AUX_SHADE)) {
					cindex = cyber_auxpal[(s.getColor()+5)%6];
				} else {
					cindex = s.getColor();
				}
				gl.glColor3d( (((int)palette[cindex*3])&0xFF) / 256.0, (((int)palette[cindex*3+1])&0xFF) / 256.0, (((int)palette[cindex*3+2])&0xFF) / 256.0);
			}
			
			gl.glBegin(GL2.GL_POLYGON);
			
			for(Integer vIndex : s.getVertexIndices()) {
				SSModel.M3TexCoord tc = s.getTexCoords().get(vIndex);
				if(tc!=null) {
					gl.glTexCoord2d(tc.getU() / 256.0, tc.getV() / 256.0);
				}
				
				SSModel.M3Vector vec = m.getVertices().get(vIndex);
				
				if(vec!=null) {
					gl.glVertex3d(vec.getX() * scale_x, vec.getY() * scale_y, vec.getZ() * scale_z);
					if(requestInfo) {
						System.out.print(String.format("%.3f %.3f %.3f, ", vec.getX(), vec.getY(), vec.getZ()));
					}
				}
				
			}
			if(requestInfo) {
				System.out.println();
			}
			
			gl.glEnd();
		}
			surfaceIndex++;
		}
		
		}
		
		if(n.getLeft()!=null) {
			renderNode(gl, m, n.getLeft(), tag+".L");
		}
		if(n.getRight()!=null) {
			renderNode(gl, m, n.getRight(), tag+".R");
		}
		
		if(onlyNode == nodeLevel+1 && requestInfo) {
			requestInfo = false;
		}
	}
	
	public void display(GLAutoDrawable drawable) {
		long curTime = System.currentTimeMillis();
		
		if(lastTime==-1)
			lastTime=curTime;
		
		long tdelta = curTime - lastTime;
		
		handle_movement(tdelta);
		
		lastTime = curTime;

		while(cam_rot[IDX_Y]>=360.0)
			cam_rot[IDX_Y] -= 360.0;

		while(cam_rot[IDX_Y]<=-360.0)
			cam_rot[IDX_Y] += 360.0;
		
		GL2 gl = drawable.getGL().getGL2();
		gl.glLoadIdentity();

		gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
		
		gl.glRotated(cam_rot[IDX_X], 1.0, 0.0, 0.0);
		gl.glRotated(cam_rot[IDX_Y], 0.0, 1.0, 0.0);
		gl.glRotated(cam_rot[IDX_Z], 0.0, 0.0, 1.0);
		gl.glTranslated(cam_pos[IDX_X], cam_pos[IDX_Y], cam_pos[IDX_Z]);
		
		renderNode(gl, model, model.getRoot(), "Root");
		
		if(onlyNode == 0 && requestInfo) {
			requestInfo = false;
		}
	}
	
	public ModelView(ResManager rm, int classIndex, SSModel m, byte [] palette) {
		super("ModelView");
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		
		setLayout(new BorderLayout());
		
		this.objectClassIndex = classIndex;
		this.rm = rm;
		this.palette = palette;
		this.model = m;
		
		numVertices = model.getVertices().size();
		
		System.out.println("Setting up GL...");
		
		
		lastTime = -1L;
		
		GLCapabilities glcaps = new GLCapabilities(GLProfile.get(GLProfile.GL2));
		glcanvas = new GLCanvas(glcaps);
		
		add(glcanvas, BorderLayout.CENTER);
		
		glcanvas.addGLEventListener(this);

		JOGLKeyboard.initialize(glcanvas);
		JOGLKeyboard.addKeyPressListener(this);

		fpsAni = new FPSAnimator(glcanvas, maxFPS);
		
		setSize(640, 480);
		setVisible(true);
		glcanvas.requestFocus();
		
		fpsAni.start();
	}
	
	private final int M_F = 1;
	private final int M_B = 2;
	private final int M_L = 4;
	private final int M_R = 8;

	private final int BLOCK_FB = M_F | M_B;
	private final int BLOCK_LR = M_L | M_R;
	
	public void handle_movement(long delta)
	{
		if(!glcanvas.isFocusOwner()) {
			return;
		}
		
		int f = JOGLKeyboard.isPressed(key_forward)?M_F:0;
		int b = JOGLKeyboard.isPressed(key_backward)?M_B:0;
		int l = JOGLKeyboard.isPressed(key_left)?M_L:0;
		int r = JOGLKeyboard.isPressed(key_right)?M_R:0;

		int mov_case = f | b | l | r;
		
		// filter contradictions
		if( (mov_case & BLOCK_FB) == BLOCK_FB )
			mov_case &= ~BLOCK_FB;

		if( (mov_case & BLOCK_LR) == BLOCK_LR )
			mov_case &= ~BLOCK_LR;
		
		int t_l = JOGLKeyboard.isPressed(key_turn_left)?M_L:0;
		int t_r = JOGLKeyboard.isPressed(key_turn_right)?M_R:0;
		
		int turn_case = t_l | t_r;
		
		if( (turn_case & BLOCK_LR) == BLOCK_LR )
			turn_case &= ~BLOCK_LR;

		boolean running = JOGLKeyboard.isPressed(key_runmod);
		double mov_scale = (double)delta / 1000.0;
		double mov_mult = mov_scale * (running?run_mod:1.0);
		
		if(turn_case!=0)
		{
			double turn_add = mov_mult * turn_speed;
			
			if((turn_case & M_L)!=0)
			{
				cam_rot[IDX_Y] -= turn_add;
			}
			
			if((turn_case & M_R)!=0)
			{
				cam_rot[IDX_Y] += turn_add;
			}
		}
		
		if(JOGLKeyboard.isPressed(key_yaw_down)) {
			cam_rot[IDX_X] += mov_mult * turn_speed;
		}
		if(JOGLKeyboard.isPressed(key_yaw_up)) {
			cam_rot[IDX_X] -= mov_mult * turn_speed;
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

			mov_angle -= cam_rot[IDX_Y];

			double movement = mov_mult * speed;
			
			double r_ma = (mov_angle / 180.0) * Math.PI;

			double delta_x = Math.sin(r_ma) * movement;
			double delta_z = Math.cos(r_ma) * movement;

			cam_pos[IDX_X] += delta_x;
			cam_pos[IDX_Z] += delta_z;
		}
		
		if(JOGLKeyboard.isPressed(key_down))
			cam_pos[IDX_Y] += mov_mult * speed;
		
		if(JOGLKeyboard.isPressed(key_up))
			cam_pos[IDX_Y] -= mov_mult * speed;
	}

	@Override
	public void keyPressed(int keyid) {
		if(keyid==KeyEvent.VK_F1) {
			onlyNode++;
		} else if(keyid==KeyEvent.VK_F2) {
			onlyNode--;
		} else if(keyid==KeyEvent.VK_F3) {
			requestInfo = true;
		} else if(keyid==KeyEvent.VK_F4) {
			onlySurface--;
		} else if(keyid==KeyEvent.VK_F5) {
			onlySurface++;
		} else {
			return;
		}
		
		if(onlyNode<-1)
			onlyNode = -1;
		
		if(onlyNode>=model.getNumberOfNodes())
			onlyNode = model.getNumberOfNodes()-1;
		
		if(onlySurface<-1)
			onlySurface = -1;

		System.out.println("NodeLevel = " + onlyNode + ", Surface = " + onlySurface);
	}

	@Override
	public void dispose(GLAutoDrawable drawable) {
		// TODO Auto-generated method stub
		
	}
}
