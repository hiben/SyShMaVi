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

import java.util.Arrays;

public class TileVisivility {
	
	public static boolean [] line_visibility(boolean [] area, int width, int height, int sx, int sy, boolean [] visible_area)
	{
		if(visible_area==null)
			visible_area = new boolean [area.length];
		
		Arrays.fill(visible_area, false);
		
		int ti = sy * width + sx;
		
		if(ti < 0 || ti >= area.length)
			return visible_area;
		
		visible_area[sy * width + sx] = true;

		// scanning from source in four quadrants.
		for(int y_dir = -1; y_dir < 2; y_dir += 2) {
			for(int t_y = sy; y_dir == -1 ? t_y >= 0 : t_y < height; t_y += y_dir) {
				for(int x_dir = -1; x_dir < 2; x_dir += 2) {
					for(int t_x = sx; x_dir == -1 ? t_x >= 0 : t_x < width; t_x += x_dir) {
						if(t_x == sx && t_y == sy)
							continue;

						int t_i = t_y * width + t_x;

						if(!area[t_i])
							continue;

						if(visible_area[t_i])
							continue;
						
						// modify area 
						//area[t_i] = visible_area[t_i] = line_vis(area, width, sx, sy, t_x, t_y);
						visible_area[t_i] = line_vis(area, width, sx, sy, t_x, t_y);
					}
				}
			}
		}
		
		return visible_area;
	}
	
	public static float pl_dist_sq(float x1, float y1, float x2, float y2, float px, float py) {
		float lsq = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1);

		float u = ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1) ) / lsq;
		
		float t_x = x1 + u * (x2 - x1);
		float t_y = y1 + u * (y2 - y1);
		
		float dsq = (px - t_x) * (px - t_x) + (py - t_y) * (py - t_y);
		
		return dsq;
	}

	public static boolean line_vis(boolean [] area, int width, int sx, int sy, int tx, int ty)
	{
		int ti;
		int dx = tx - sx;
		int dy = ty - sy;
		
		int start_x, start_y, end_x, end_y, small_y;
		
		ti = sy * width + sx;
		
		if(!area[ti]) {
			return false;
		}
		
		if(dx == 0 && dy == 0) {
			return true;
		}
		
		int h_len = Math.abs(dx) + 1;
		int v_len = Math.abs(dy) + 1;
		
		// handle horizontal case
		if(dy == 0) {
			start_x = sx < tx ? sx : tx;
			end_x = start_x + h_len;
			for(int x=start_x; x<end_x; x++) {
				ti = sy * width + x;
				
				if(!area[ti]) {
					return false;
				}
			}
			
			return true;
		}
		
		// handle vertical case		
		if(dx == 0) {
			start_y = sy < ty ? sy : ty;
			end_y  = start_y + v_len;
			
			for(int y=start_y; y < end_y; y++)
			{
				ti = y * width + sx;
				
				if(!area[ti]) {
					return false;
				}
			}
			
			return true;
		}
		
		// at this point, h_len and v_len > 1

		start_x = sx < tx ? sx : tx;
		start_y = sx < tx ? sy : ty;
		end_x = start_x + h_len;
		end_y = start_y + v_len;
		
		small_y = sy < ty ? sy : ty;
		
		int tile_x, tile_y;
		
		boolean [] last_slice = new boolean [v_len];
		boolean [] current_slice = new boolean [v_len];
		boolean [] tmp_slice;
		
		boolean blocked = false;
		
		// Slope-Version
		for(int x=0; x < h_len; x++) {
			Arrays.fill(current_slice, false);
			
			tile_x = start_x + x;
			
			if(dx > 0) {
				tile_y = sy + (x * dy) / (h_len - 1);
			} else {
				tile_y = ty - (x * dy) / (h_len - 1);
			}
			
			ti = tile_y * width + tile_x;
			
			boolean is_free = area[ti];
			float dist_sq = pl_dist_sq(sx, sy, tx, ty, tile_x, tile_y);
			
			int y = tile_y - small_y;
			
			current_slice[y] = (is_free && dist_sq < 1.5);
			
			int run_tile_y = tile_y - 1;
			
			while( (y = run_tile_y - small_y) >= 0 && pl_dist_sq(sx, sy, tx, ty, tile_x, run_tile_y) < 1.5) {
				ti = run_tile_y * width + tile_x;
				current_slice[y] = area[ti];
				run_tile_y--;
			}

			run_tile_y = tile_y + 1;
			
			while((y = run_tile_y - small_y) < v_len && pl_dist_sq(sx, sy, tx, ty, tile_x, run_tile_y) < 1.5) {
				ti = run_tile_y * width + tile_x;
				current_slice[y] = area[ti];
				run_tile_y++;
			}
			
			// on start/finish only keep area with source/target
			if(x == 0 || x == h_len-1) {
				int check_y, e_y;
				
				if(x==0) {
					check_y = (dx < 0 ? ty : sy) - small_y;
				} else {
					check_y = (dx < 0 ? sy : ty) - small_y;
				}
				
				e_y = check_y - 1;
				
				while(e_y >= 0 && current_slice[e_y]) {
					e_y--;
				}
				
				while(e_y >= 0) {
					current_slice[e_y--] = false;
				}

				e_y = check_y + 1;
				
				while(e_y < v_len && current_slice[e_y]) {
					e_y++;
				}
				
				while(e_y < v_len) {
					current_slice[e_y++] = false;
				}
			}
			
			if(x > 0) {
				boolean has_connection = false;
				
				int slice_y = 0;
				boolean had_connection = false;
				
				while(slice_y < v_len)
				{
					// if current slice has potential connection...
					if(current_slice[slice_y]) {
						// ...and last slide also has...
						if(last_slice[slice_y]) {
							// ...remember this
							had_connection = true;
							// ...also mark globally
							has_connection = true;
						}
					// at some point, connected area in current slice ends...
					} else {
						// ...and if its not connected...
						if(!had_connection) {
							int back_y = slice_y - 1;
							// delete the last area (current becomes last)
							while(back_y >= 0 && current_slice[back_y]) {
								current_slice[back_y--] = false;
							}
						// and else, we just keep it
						} else {
							had_connection = false;
						}
					}
					slice_y++;
				}
				
				if(current_slice[v_len-1] && !had_connection) {
					int back_y = v_len-1;
					while(back_y >= 0 && current_slice[back_y]) {
						current_slice[back_y--] = false;
					}
				}
				
				// even if connected, target could not be contained
				if(dx > 0 && x == h_len-1) {
					if(!current_slice[ty - small_y])
						has_connection = false;
				}
				
				
				if(!has_connection) {
					blocked = true;
					break;
				}
			}
			
			
			tmp_slice = last_slice;
			last_slice = current_slice;
			current_slice = tmp_slice;
		}
		
		return !blocked;
	}
}
