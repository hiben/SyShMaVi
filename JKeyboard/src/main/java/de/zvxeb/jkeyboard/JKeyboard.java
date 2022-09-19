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
package de.zvxeb.jkeyboard;

import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * A simple pollable keyboard-state representation.
 * @author Hendrik Iben
 */
public class JKeyboard {
	/**
	 * This array reflects the observed key states.<br>
	 * It can be indexed with any defined keycode to obtain
	 * the state of that particular key.
	 */
	static public boolean [] keystate = null;
	static private KBEventListener kbel = new KBEventListener();
	static private boolean isRegistered = false;
	
	static private List<KeyPressListener> pressListeners = new LinkedList<KeyPressListener>();
	static private List<KeyReleaseListener> releaseListeners = new LinkedList<KeyReleaseListener>();

	static private List<KeyPressListener> tmpPressListeners = new LinkedList<KeyPressListener>();
	static private List<KeyReleaseListener> tmpReleaseListeners = new LinkedList<KeyReleaseListener>();
	
	static private Map<Object, KeyPressListener> perObjectPressListener = new HashMap<Object, KeyPressListener>();
	static private Map<Object, KeyReleaseListener> perObjectReleaseListener = new HashMap<Object, KeyReleaseListener>();
	
	static private Object syncDummy = new Object();
	
	/**
	 * This method is called to attach to the AWTEvent-system.<br>
	 * If keys are pressed when this method is called, their state
	 * will not be represented correctly until they are released.
	 */
	public static void initialize()
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
				Toolkit tk = Toolkit.getDefaultToolkit();
				tk.addAWTEventListener(kbel, AWTEvent.KEY_EVENT_MASK);
				isRegistered = true;
			}
		}
	}
	
	public static void addKeyPressListener(KeyPressListener kpl)
	{
		JKeyboard.addKeyPressListener(kpl, (Object)null);
	}
	
	public static void removeKeyPressListener(KeyPressListener kpl)
	{
		JKeyboard.removeKeyPressListener(kpl, (Object)null);
	}

	public static void addKeyReleaseListener(KeyReleaseListener krl)
	{
		JKeyboard.addKeyReleaseListener(krl, (Object)null);
	}
	
	public static void removeKeyReleaseListener(KeyReleaseListener krl)
	{
		JKeyboard.removeKeyReleaseListener(krl, (Object)null);
	}

	public static void addKeyListener(KeyListener kl)
	{
		JKeyboard.addKeyListener(kl, (Object)null);
	}
	
	public static void removeKeyListener(KeyListener kl)
	{
		JKeyboard.removeKeyListener(kl, (Object)null);
	}
	
	public static void addKeyPressListener(KeyPressListener kpl, Object src)
	{
		synchronized(syncDummy) {
			if(src==null) {
				pressListeners.add(kpl);
			} else {
				perObjectPressListener.put(src, kpl);
			}
		}
	}


	public static void removeKeyPressListener(KeyPressListener kpl, Object src)
	{
		synchronized(syncDummy) {
			if(src==null) {
				pressListeners.remove(kpl);
			} else {
				perObjectPressListener.remove(src);
			}
		}	
	}

	public static void addKeyReleaseListener(KeyReleaseListener krl, Object src)
	{
		synchronized(syncDummy) {
			if(src==null) {
				releaseListeners.add(krl);
			} else {
				perObjectReleaseListener.put(src, krl);
			}
		}
	}

	public static void removeKeyReleaseListener(KeyReleaseListener krl, Object src)
	{
		synchronized(syncDummy) {
			if(src==null) {
				releaseListeners.remove(krl);
			} else {
				perObjectReleaseListener.remove(src);
			}
		}
	}

	public static void addKeyListener(KeyListener kl, Object src)
	{
		synchronized(syncDummy) {
			if(src==null) {
				pressListeners.add(kl);
				releaseListeners.add(kl);
			} else {
				perObjectPressListener.put(src, kl);
				perObjectReleaseListener.put(src, kl);
			}
		}
	}

	public static void removeKeyListener(KeyListener kl, Object src)
	{
		synchronized(syncDummy) {
			if(src==null) {
				pressListeners.remove(kl);
				releaseListeners.remove(kl);
			} else {
				perObjectPressListener.remove(src);
				perObjectReleaseListener.remove(src);
			}
		}
	}
	
	/**
	 * This method is called to detach from the AWTEvent-system.<br>
	 * The last observed keystate persists in the representation.
	 */
	public static void shutdown()
	{
		Toolkit tk = Toolkit.getDefaultToolkit();
		tk.removeAWTEventListener(kbel);
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
	
	private static class KBEventListener implements AWTEventListener
	{
		public void eventDispatched(AWTEvent event) {
			if(event instanceof KeyEvent)
			{
				Object src = event.getSource();
				KeyPressListener perObjectPress = (src==null)?null:perObjectPressListener.get(src);
				KeyReleaseListener perObjectRelease = (src==null)?null:perObjectReleaseListener.get(src);
				
				int eid = event.getID();
				int keycode = ((KeyEvent)event).getKeyCode();
				switch(eid)
				{
				case KeyEvent.KEY_PRESSED:
					keystate[keycode] = true;
					if(perObjectPress!=null) {
						perObjectPress.keyPressed(keycode, ((KeyEvent) event).getKeyChar());
					}
					else {
						tmpPressListeners.addAll(pressListeners);
						for(KeyPressListener kpl : tmpPressListeners)
							kpl.keyPressed(keycode, ((KeyEvent) event).getKeyChar());
						tmpPressListeners.clear();
					}
					break;
				case KeyEvent.KEY_RELEASED:
					keystate[keycode] = false;
					if(perObjectRelease!=null) {
						perObjectRelease.keyReleased(keycode, ((KeyEvent) event).getKeyChar());
					} else {
						tmpReleaseListeners.addAll(releaseListeners);
						for(KeyReleaseListener krl : tmpReleaseListeners)
							krl.keyReleased(keycode, ((KeyEvent) event).getKeyChar());
						tmpReleaseListeners.clear();
					}
					break;
				}
			}
		}
		
	}
}
