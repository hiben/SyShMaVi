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

import java.awt.event.KeyEvent;

public class Environment {
	public long lastTime;
	public double rotationZ, rotationY;
	
	public double [] cam_pos = new double [3];
	public double [] cam_rot = new double [3];
	public double [] cam_view = new double [3];
	public double cam_planed; 
	
	public int key_forward = KeyEvent.VK_W;
	public int key_backward = KeyEvent.VK_S;
	public int key_left = KeyEvent.VK_A;
	public int key_right = KeyEvent.VK_D;
	
	public int key_lookup = KeyEvent.VK_R;
	public int key_lookcenter = KeyEvent.VK_F;
	public int key_lookdown = KeyEvent.VK_V;

	public int key_up = KeyEvent.VK_PAGE_UP;
	public int key_down = KeyEvent.VK_PAGE_DOWN;

	public int key_turn_left = KeyEvent.VK_LEFT;
	public int key_turn_right = KeyEvent.VK_RIGHT;

	public int key_runmod = KeyEvent.VK_SHIFT;
	
	public int key_next_map = KeyEvent.VK_ADD;
	public int key_previous_map = KeyEvent.VK_SUBTRACT;
	
	public int key_show_map = KeyEvent.VK_TAB;

	public int key_console = KeyEvent.VK_DEAD_CIRCUMFLEX;

	public double speed = 1.5;
	public double turn_speed = 60.0;
	public double turn_speed_mouse = 7200.0;
	public double run_mod = 3.0;
	
	public double level_scale = 2.0;
	// eye height has been reconstructed from
	// screenshots. it seems the camera
	// is located 1.5m above the floor
	// (assuming 2m tile length)
	// ...my eyes are at 1.61m...
	public double eye_height = 1.5;
	
	public double aspect;
	// it seems 60 degress FOV is
	// common in games. real eyes
	// have about 90 degress but it looks
	// somewhat strange in games...
	public double fov_h = 60.0;
	public boolean fov_update = true;
	public long frame_time;
	public int frames;
	
	public double fps;
}
