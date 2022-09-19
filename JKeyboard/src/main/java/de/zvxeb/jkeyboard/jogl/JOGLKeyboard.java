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
 * Created on 07.11.2008
 */
package de.zvxeb.jkeyboard.jogl;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.List;

import com.jogamp.opengl.GLDrawable;

import de.zvxeb.jkeyboard.KeyListener;
import de.zvxeb.jkeyboard.KeyPressListener;
import de.zvxeb.jkeyboard.KeyReleaseListener;

/**
 * A simple pollable keyboard-state representation for use
 * with JOGL drawables
 * @author Hendrik Iben
 */
public class JOGLKeyboard {
	/**
	 * This array reflects the observed key states.<br>
	 * It can be indexed with any defined keycode to obtain
	 * the state of that particular key.
	 */
	static public boolean [] keystate;
	static private KBEventListener kbel = new KBEventListener();
	static private boolean isRegistered = false;
	
	static private List<KeyPressListener> pressListeners = new LinkedList<KeyPressListener>();
	static private List<KeyReleaseListener> releaseListeners = new LinkedList<KeyReleaseListener>();

	static private List<KeyPressListener> tmpPressListeners = new LinkedList<KeyPressListener>();
	static private List<KeyReleaseListener> tmpReleaseListeners = new LinkedList<KeyReleaseListener>();
	
	static private Object syncDummy = new Object();
	
	static private GLDrawable drawable;
	
	/**
	 * This method is called to attach to the AWTEvent-system.<br>
	 * If keys are pressed when this method is called, their state
	 * will not be represented correctly until they are released.
	 */
	public static void initialize(GLDrawable drawable)
	{
		synchronized(syncDummy) {
			// Find out about defined keystates (~200 keys, max value ~65000)
			Class<?> keyClass = KeyEvent.class;
			Field [] keyfields = keyClass.getFields();
			int maxKey = Integer.MIN_VALUE;
			for(Field f : keyfields) {
				if(((f.getModifiers() & Modifier.STATIC) == Modifier.STATIC) && f.getName().startsWith("VK_") && f.getType() == int.class) {
					try {
						int value = f.getInt(null);
						if(maxKey < value)
							maxKey = value;
					} catch (IllegalArgumentException e) {
						e.printStackTrace();
					} catch (IllegalAccessException e) {
						e.printStackTrace();
					}
				}
			}
			keystate = new boolean [maxKey+1];
			
			for(int i=0; i<keystate.length; i++)
				keystate[i] = false;

			if(!isRegistered) {
				JOGLKeyboard.drawable = drawable;
				
				if(drawable instanceof Component) {
					Component c = (Component)drawable;
					c.addKeyListener(kbel);
				} else {
					System.err.println("Not attaching to window...");
				}
				
				isRegistered = true;
			}
		}
	}
	
	public static void dispose() {
		synchronized(syncDummy) {
			if(isRegistered) {
				if(drawable instanceof Component) {
					Component c = (Component)drawable;
					c.removeKeyListener(kbel);
				}
				isRegistered = false;
			}
		}
	}

		
	public static void addKeyPressListener(KeyPressListener kpl)
	{
		synchronized(syncDummy) {
				pressListeners.add(kpl);
		}
	}


	public static void removeKeyPressListener(KeyPressListener kpl)
	{
		synchronized(syncDummy) {
				pressListeners.remove(kpl);
		}	
	}

	public static void addKeyReleaseListener(KeyReleaseListener krl)
	{
		synchronized(syncDummy) {
				releaseListeners.add(krl);
		}
	}

	public static void removeKeyReleaseListener(KeyReleaseListener krl)
	{
		synchronized(syncDummy) {
				releaseListeners.remove(krl);
		}
	}

	public static void addKeyListener(KeyListener kl)
	{
		synchronized(syncDummy) {
				pressListeners.add(kl);
				releaseListeners.add(kl);
		}
	}

	public static void removeKeyListener(KeyListener kl)
	{
		synchronized(syncDummy) {
				pressListeners.remove(kl);
				releaseListeners.remove(kl);
		}
	}
	
	/**
	 * This method is called to detach from the AWTEvent-system.<br>
	 * The last observed keystate persists in the representation.
	 */
	public static void shutdown()
	{
		if(drawable instanceof Component) {
			Component c = (Component)drawable;
			c.removeKeyListener(kbel);
		}
	}
	
	/**
	 * Determine if a key is currently pressed.
	 * @param keycode
	 * @return <em>true</em> if the last observed event for this key was a press,
	 * <em>false</em> if it was a release (or no event occured yet) 
	 */
	public static boolean isPressed(int keycode)
	{
		return keystate[keycode];
	}
	
	private static class KBEventListener implements java.awt.event.KeyListener
	{

		@Override
		public void keyTyped(KeyEvent e) {

		}

		@Override
		public void keyPressed(KeyEvent event) {
			int keycode = event.getKeyCode();

			keystate[keycode] = true;
			tmpPressListeners.addAll(pressListeners);
			for(KeyPressListener kpl : tmpPressListeners)
				kpl.keyPressed(keycode, event.getKeyChar());
			tmpPressListeners.clear();
		}

		@Override
		public void keyReleased(KeyEvent event) {
			int keycode = event.getKeyCode();

			keystate[keycode] = false;
			tmpReleaseListeners.addAll(releaseListeners);
			for(KeyReleaseListener krl : tmpReleaseListeners)
				krl.keyReleased(keycode, event.getKeyChar());
			tmpReleaseListeners.clear();
		}
	}
}
