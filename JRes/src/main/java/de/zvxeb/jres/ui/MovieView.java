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
 * Created on 26.09.2008
 */
package de.zvxeb.jres.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.swing.AbstractAction;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import de.zvxeb.jres.ResMovie;
import de.zvxeb.jres.util.PNGImageFilter;
import de.zvxeb.jres.util.Util;

public class MovieView extends JFrame implements ChangeListener {
	private static final long serialVersionUID = 1L;
	
	public static final int maxVidCacheForSubTitle = 10;
	
	private ResMovie movie;
	
	private VideoDisplay vidDisp;
	
	private JPanel controlPanel;
	
	private JScrollPane subtitleScroll;
	private JTextArea subtitleArea;
	
	private JPanel buttonPanel;
	private JButton buttonStart, buttonStop, buttonSaveFrame, buttonSaveAllFrames;
	
	private JSlider sliderPosition;
	private boolean currentlySetting = false;
	
	private String [] resampleItems = { "Resample", "Java", "None" };
	
	private JComboBox comboboxResample;
	
	private JLabel infoLabel;
	
	private PlaybackThread pt;
	private FrameCache fc;
	private AudioThread at;
	
	private JFileChooser saveFrameChooser = null;
	
	private class VideoDisplay extends Component {
		private static final long serialVersionUID = 1L;

		private Dimension videoSize = new Dimension(100, 100);
		
		private BufferStrategy strategy = null;
		
		private BufferedImage frame = null;
		private Graphics2D frameGraphics = null;
		
		private boolean buffered = true;
		
		public void paint(Graphics g) {
			Graphics2D g2d = (Graphics2D)g;
			if(frame==null)
				return;
			
			g2d.drawImage(frame, null, null);
		}

		public void update(BufferedImage i) {
			if(buffered && strategy==null && isVisible()) {
				createBufferStrategy(2);
				strategy = getBufferStrategy();
				if(strategy==null)
					throw new RuntimeException("No Strategy!");
			} else {
				if(!buffered && strategy!=null) {
					strategy.dispose();
					strategy = null;
				}
			}
			
			if(buffered && isVisible()) {
				Graphics2D g2d = (Graphics2D)strategy.getDrawGraphics();
				g2d.drawImage(i, null, null);
				g2d.dispose();
				strategy.show();
			} else {
				frameGraphics.drawImage(i, null, null);
				repaint();
			}
		}
		
		public void init(Dimension videoSize, int type, boolean buffered) {
			this.videoSize = videoSize;
			if(type==-1)
				type = BufferedImage.TYPE_INT_RGB;
			frame = new BufferedImage(videoSize.width, videoSize.height, BufferedImage.TYPE_INT_ARGB);
			frameGraphics = frame.createGraphics();
			invalidate();
			this.buffered = buffered;
			setIgnoreRepaint(buffered);
		}
		
		public Dimension getSize() {
			return videoSize;
		}
		
		public Dimension getPreferredSize() {
			return videoSize;
		}
		
		public int getWidth() {
			return videoSize.width;
		}
		
		public int getHeight() {
			return videoSize.height;
		}
	}
	
	public MovieView(String title, ResMovie rm)
	{
		super(title);
		setLayout(new BorderLayout());
		
		movie = rm;
		
		vidDisp = new VideoDisplay();
		
		getContentPane().add(vidDisp, BorderLayout.CENTER);
		
		controlPanel = new JPanel();
		
		controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.PAGE_AXIS));
		
		subtitleArea = new JTextArea(2*3+1, 40);
		
		subtitleScroll = new JScrollPane(subtitleArea);
		
		buttonPanel = new JPanel();
		
		buttonStart = new JButton();
		buttonStop = new JButton();
		buttonSaveFrame = new JButton();
		buttonSaveAllFrames = new JButton();
		
		buttonStart.setAction
		(
				new AbstractAction("Play")
				{
					private static final long serialVersionUID = 1L;
					
					public void actionPerformed(ActionEvent ae)
					{
						if(pt!=null)
							pt.stopPlayback();
						if(at!=null)
							at.stopAudio();
						
						movie.resetMovie();
						
						pt = new PlaybackThread();
						fc = new FrameCache();
						at = new AudioThread();
						fc.start();
						pt.start();
						at.start();
					}
				}
		);
		
		buttonStop.setAction
		(
				new AbstractAction("Stop")
				{
					private static final long serialVersionUID = 1L;
					
					public void actionPerformed(ActionEvent ae)
					{
						if(pt!=null)
							pt.stopPlayback();
						if(fc!=null)
							fc.stopCaching();
						if(at!=null)
							at.stopAudio();
					}
				}
		);
		
		buttonSaveFrame.setAction
		(
			new AbstractAction("SaveFrame")
			{
				private static final long serialVersionUID = 1L;
				
				public void actionPerformed(ActionEvent ae)
				{
					if(saveFrameChooser==null) {
						saveFrameChooser = new JFileChooser(new File("."));
					}
					saveFrameChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
					saveFrameChooser.setFileFilter(new PNGImageFilter());
					
					if(vidDisp.frame==null) {
						JOptionPane.showMessageDialog(MovieView.this, "There is currently no frame to save!", "No frame to save!", JOptionPane.ERROR_MESSAGE);
						return;
					}
					
					if(saveFrameChooser.showSaveDialog(MovieView.this)==JFileChooser.APPROVE_OPTION)
					{
						File selectedFile = saveFrameChooser.getSelectedFile();
						if(Util.getFileExt(selectedFile.getName())==null) {
							File parent = selectedFile.getParentFile();
							selectedFile = new File(parent, selectedFile.getName() + ".png");
						}
						if(selectedFile.exists()) {
							if(JOptionPane.showConfirmDialog(MovieView.this, "File \"" + selectedFile.getName() + "\" exists. Overwrite ?", "File exist...", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE)!=JOptionPane.YES_OPTION)
								return;
						}
						try {
							if(!ImageIO.write(vidDisp.frame, "png", new FileOutputStream(selectedFile))) {
								JOptionPane.showMessageDialog(MovieView.this, "Java could not create a PNG image. Good luck!", "Missing PNG-support!", JOptionPane.ERROR_MESSAGE);
							}
						} catch (IOException e) {
							e.printStackTrace();
							JOptionPane.showMessageDialog(MovieView.this, "There was an error while writing the image! Check console output!", "Error while writing", JOptionPane.ERROR_MESSAGE);
						}
					}
				}				
			}
		);
		
		buttonSaveAllFrames.setAction(new AbstractAction("Save All Frames") {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				if(saveFrameChooser==null) {
					saveFrameChooser = new JFileChooser(new File("."));
				}
				saveFrameChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				saveFrameChooser.setFileFilter(null);
				
				if(saveFrameChooser.showOpenDialog(MovieView.this) == JFileChooser.APPROVE_OPTION) {
					movie.resetMovie();
					int frames = movie.getMoviewFrames();
					int digits = (int)Math.ceil(Math.log10(frames));
					String format = "frame%0" + digits + "d.png";
					ResMovie.MovieData md = new ResMovie.MovieData();
					ResMovie.Frame f;
					int framenum = 0;
					try {
						String basename = saveFrameChooser.getSelectedFile().getCanonicalPath();
						while( (f = movie.getNextFrame(md)) != null) {
							if(f instanceof ResMovie.VideoFrame) {
								ResMovie.VideoFrame vf = (ResMovie.VideoFrame)f;
								BufferedImage bi = vf.getVideoData();
								String file = basename + File.separator + String.format(format, framenum++);
								if(!ImageIO.write(bi, "PNG", new File(file))) {
									System.err.println("Could not write frame " + (framenum-1));
								}
							}
						}
					} catch (IOException e1) {
						JOptionPane.showMessageDialog(MovieView.this, "Error while processing frames: " + e1.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
					}
				}

			}
			
		});
		
		comboboxResample = new JComboBox(resampleItems);
		comboboxResample.setToolTipText("Resample: compute 22050Hz sample; Java: Use 22222Hz Audio Line (buggy); None: playback 22222Hz sample at 22050Hz");
		
		infoLabel = new JLabel();
		
		buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
		
		buttonPanel.add(buttonStart);
		buttonPanel.add(buttonStop);
		buttonPanel.add(buttonSaveFrame);
		buttonPanel.add(buttonSaveAllFrames);
		buttonPanel.add(new JLabel("Audio-Fudge: "));
		buttonPanel.add(comboboxResample);
		buttonPanel.add(infoLabel);
		
		controlPanel.add(subtitleScroll);
		controlPanel.add(buttonPanel);
		
		sliderPosition = new JSlider(0, rm.getMovieTime());
		controlPanel.add(sliderPosition);
		
		sliderPosition.addChangeListener(this);
		
		getContentPane().add(controlPanel, BorderLayout.SOUTH);
		
		if(movie.getWidth()==0 || movie.getHeight()==0)
		{
			BufferedImage bi = new BufferedImage(128, 128, BufferedImage.TYPE_BYTE_GRAY);
			bi.createGraphics().drawString("No Image", 20, 64);
			vidDisp.init(new Dimension(128, 128), BufferedImage.TYPE_BYTE_GRAY, false);
			vidDisp.update(bi);
		}
		else
		{
			vidDisp.init(new Dimension(movie.getWidth(), movie.getHeight()), BufferedImage.TYPE_INT_RGB, false);
		}
		
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				if(pt!=null)
					pt.stopPlayback();
				if(at!=null)
					at.stopAudio();
			}
		});
	}
	
	public MovieView(ResMovie rm)
	{
		this("MovieView", rm);
	}
	
	private class FrameCache extends Thread 
	{
		private int goal = 40;
		private boolean active = true;
		
		public void stopCaching() {
			active = false;
		}
		
		private LinkedList<ResMovie.MovieData> dataPool = new LinkedList<ResMovie.MovieData>();
		
		private Map<ResMovie.Frame, ResMovie.MovieData> poolLink = new HashMap<ResMovie.Frame, ResMovie.MovieData>();

		LinkedList<ResMovie.Frame> frameQueue = new LinkedList<ResMovie.Frame>();
		
		public void run() {
			ResMovie.Frame f = null;
			
			// create elements for storage
			for(int i=0; i<goal; i++) {
				dataPool.add(new ResMovie.MovieData());
			}
			ResMovie.MovieData md;
			
			synchronized(frameQueue) {
				synchronized(dataPool) {
					md = dataPool.getFirst();
					md.setUnupdated();
				}
				while(active && frameQueue.size()<goal && (f=movie.getNextFrame(md))!=null && dataPool.size()>0) {
					synchronized(dataPool) {
						dataPool.removeFirst();
						poolLink.put(f, md);
						frameQueue.offer(f);
						if(frameQueue.size()<goal) {
							md = dataPool.getFirst();
							md.setUnupdated();
						}
					}
				}
			}

			while(active && f!=null) {
				if(frameQueue.size()<goal && dataPool.size()>0) {
					synchronized(dataPool) {
						md = dataPool.removeFirst();
					}
					md.setUnupdated();
					f = movie.getNextFrame(md);
					if(f!=null) {
						synchronized(dataPool) {
							poolLink.put(f, md);
						}
						synchronized(frameQueue) {
							frameQueue.offer(f);
							frameQueue.notify();
						}
					} else {
						synchronized(dataPool) {
							dataPool.addLast(md);
						}
					}
				} else {
					try {
						sleep(1);
					} catch (InterruptedException e) {
					}
				}
			}
		}
		
		public ResMovie.Frame getFrame() {
			synchronized(frameQueue) {
				if(frameQueue.size()>0) {
					return frameQueue.remove();
				}
				while(isAlive()) {
					try {
						frameQueue.wait(1);
						if(frameQueue.size()>0) {
							return frameQueue.getFirst();
						}
					} catch (InterruptedException e) {
						System.err.println("FrameQueue interrupted!");
						return null;
					}
				}
			}

			/*
			while(isAlive()) {
				yield();
				if(frameQueue.size()>0) {
					synchronized(frameQueue) {
						return frameQueue.remove();
					}
				}
			}
			*/

			return null;
		}
		
		public void releaseFrame(ResMovie.Frame f) {
			ResMovie.MovieData md = poolLink.get(f);
			if(md!=null) {
				synchronized(dataPool) {
					poolLink.remove(f);
					md.updateSubTitleFrame(null); // free for GC
					dataPool.addLast(md);
				}
			}
		}
		
		public int getBufferSize() {
			return frameQueue.size();
		}
	}
	
	private class AudioThread extends Thread
	{
		private Queue<byte []> audioDataQueue = new LinkedList<byte []>();
		private boolean doPlay, finishAudio;
		
		public void stopAudio() {
			finishAudio = false;
			doPlay = false;
		}

		public void finishAndStopAudio() {
			finishAudio = true; // set true first for thread
			doPlay = false;
		}
		
		public void addAudioData(byte [] data) {
			synchronized(audioDataQueue) {
				audioDataQueue.offer(data);
			}
		}

		public void clearAudioData() {
			synchronized(audioDataQueue) {
				audioDataQueue.clear();
			}
		}
		
		private byte [] resample22222(byte [] data, byte [] resampled) {
			int new_size = ((data.length * 22050) / 22222);
			
			if(resampled == null || resampled.length!=new_size)
				resampled = new byte [new_size];
			
			for(int i=0; i<new_size; i++) {
				float si = (float)(i * (data.length-1))/(float)(new_size-1);
				int ilow = (int)Math.floor(si);
				int ihigh = (int)Math.ceil(si);
				float w = si - (float)Math.floor(si);
				resampled[i] = (byte)((((1.0f - w) * (((int)data[ilow])&0xFF) ) + w * (((int)data[ihigh])&0xFF)) / 2.0f);
			}
			
			return resampled;			
		}
		
		public void run() {
			float sampleRate = 22050f;
			boolean doResample = false;
			
			if(resampleItems[1].equals(comboboxResample.getSelectedItem()))
			{
				sampleRate = 22222f;
			}
			
			if(resampleItems[0].equals(comboboxResample.getSelectedItem()))
			{
				doResample = true;
			}
			
			AudioFormat af = new AudioFormat(sampleRate, 8, 1, false, false);
			SourceDataLine dsl;
	
			try {
				dsl = AudioSystem.getSourceDataLine(af);

				dsl.open(af);
				dsl.start();
			} catch (LineUnavailableException e) {
				e.printStackTrace();
				return;
			}
			
			doPlay = true;
			int buffersize = dsl.getBufferSize();
			int available, datainbuffer;
			
			byte [] resample_buffer = null;
			byte [] current_audio = null;
			int current_index = 0;
			int data_left = 0;
			int toWrite;
			
			while((doPlay || finishAudio) && isAlive()) {
				available = dsl.available();
				datainbuffer = buffersize - available;
				if(current_audio!=null) {
					toWrite = Math.min(data_left, available);
					dsl.write(current_audio, current_index, toWrite);
					data_left -= toWrite;
					current_index += toWrite;
					if(data_left<1) {
						current_audio = null;
					}
				} else {
					if(audioDataQueue.size()>0) {
						if(datainbuffer < 8192) {
							synchronized(audioDataQueue) {
								if(doResample) {
									resample_buffer = resample22222(audioDataQueue.remove(), current_audio);
									current_audio = resample_buffer;
								}
								else
								{
									current_audio = audioDataQueue.remove();
								}
									
							}
							current_index = 0;
							data_left = current_audio.length;
							continue;
						}
					} else {
						if(finishAudio) {
							doPlay = false;
							finishAudio = false;
						}
					}
				}
				try {
					sleep(10);
				} catch (InterruptedException e) {
				}

			}

			dsl.flush();
			dsl.drain();

			dsl.stop();
			dsl.close();
			
			System.out.println("ResampleBuffer: " + ((resample_buffer!=null)?(""+resample_buffer.length):"<null>"));
		}
		
	}
	
	public static String frameType(ResMovie.Frame f) {
		if(f instanceof ResMovie.VideoFrame)
			return "VideoFrame";
		if(f instanceof ResMovie.AudioFrame)
			return "AudioFrame";
		if(f instanceof ResMovie.SubTitleFrame)
			return "SubTitleFrame";
		
		return "Frame";
	}

	public class PlaybackThread extends Thread
	{
		private boolean doPlay, isPlaying;
		private boolean killAudio = true;
		
		public PlaybackThread()
		{
			doPlay = true;
			isPlaying = false;
		}
		
		public void stopPlayback()
		{
			killAudio = true;
			doPlay = false;
		}

		public void stopPlaybackKeepAudio()
		{
			killAudio = false;
			doPlay = false;
		}
		
		public boolean isPlaying()
		{
			return isPlaying;
		}
		
		
		public void run()
		{
			isPlaying = true;
			
			ResMovie.Frame currentFrame = fc.getFrame();
			ResMovie.Frame prevFrame = currentFrame;
			long frameTime = currentFrame.getTimestamp();
			long startFrameTime = frameTime;
			long frameStart = ((frameTime - startFrameTime) * 1000) / 65536;
			int videoFrame = 1;
			
			long startSystemTime = System.currentTimeMillis();

			while(doPlay && currentFrame!=null)
			{
				long currentTime = System.currentTimeMillis() - startSystemTime;
				long delta = frameStart - currentTime;
				long prevFrameStart = frameStart;

				while(prevFrameStart == frameStart) {
					if(!(currentFrame instanceof ResMovie.AudioFrame)) {
						while(delta > 10) {
							currentTime = System.currentTimeMillis() - startSystemTime;
							delta = frameStart - currentTime;
						}
					}
					if(delta < -9 && currentFrame instanceof ResMovie.VideoFrame) {
						System.out.println("Underrun " + delta);
						videoFrame++;
					} else {

						if(currentFrame instanceof ResMovie.AudioFrame) {
							byte [] audio_data = ((ResMovie.AudioFrame)currentFrame).getAudioData();
							byte [] copy = new byte [audio_data.length];
							System.arraycopy(audio_data, 0, copy, 0, audio_data.length);
							at.addAudioData(copy);
						}
						if(currentFrame instanceof ResMovie.VideoFrame) {
							videoFrame++;
							vidDisp.update(((ResMovie.VideoFrame)currentFrame).getVideoData());
							currentlySetting = true;
							
							if(!sliderPosition.getValueIsAdjusting())
								sliderPosition.setValue((int)currentFrame.getTimestamp());
							
							currentlySetting = false;
						}
						if(currentFrame instanceof ResMovie.SubTitleFrame) {
							subtitleArea.append("\n" + ((ResMovie.SubTitleFrame)currentFrame).getText());
							subtitleArea.setCaretPosition(subtitleArea.getDocument().getLength());
						}
					}
					
					fc.releaseFrame(currentFrame);

					prevFrame = currentFrame;
					prevFrameStart = frameStart;

					currentFrame = fc.getFrame();

					if(currentFrame != null) {
						if(currentFrame.getTimestamp() != prevFrame.getTimestamp()) {
							frameTime = currentFrame.getTimestamp();
							frameStart = ((frameTime - startFrameTime) * 1000) / 65536;
							currentTime = System.currentTimeMillis() - startSystemTime;
							delta = frameStart - currentTime;
							
							if(delta < 0) {
								System.out.println("smaller delta... " + delta + " type " + frameType(currentFrame));;
							}
							
						} else {
							delta = 0;
						}
					} else {
						break; // same frame time loop
					}
					
					infoLabel.setText("Frame #" + videoFrame + " Buffer: " + fc.getBufferSize() + " Mem: " + Runtime.getRuntime().freeMemory());
				}

				yield();
			}
			
			if(killAudio)
				at.finishAndStopAudio();
			isPlaying = false;
		}
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		if(sliderPosition.getValueIsAdjusting() || currentlySetting)
			return;
		
		if(pt!=null)
			pt.stopPlaybackKeepAudio();
		if(fc!=null)
			fc.stopCaching();
		if(at!=null) {
			at.clearAudioData();
		}
		else {
			at = new AudioThread();
			at.start();
		}
		
		movie.gotoTime(sliderPosition.getValue());
		
		pt = new PlaybackThread();
		fc = new FrameCache();
		fc.start();
		pt.start();
	}
}
