package org.gps.air.receiver.Impl;

import org.phlo.AirReceiver.AudioClock;
import org.phlo.AirReceiver.AudioStreamInformationProvider;

import javax.sound.sampled.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Created by leogps on 6/9/14.
 */
public class TardyYetReliableAudioOutputQueue implements AudioClock {

    private static Logger s_logger = Logger.getLogger(TardyYetReliableAudioOutputQueue.class.getName());

    private static final double BufferSizeSeconds = 0.05;

    /**
     * The seconds time corresponding to line time zero
     */
    private final double m_secondsTimeOffset;

    /**
     * True if the line's audio format is signed but
     * the requested format was unsigned
     */
    private final boolean m_convertUnsignedToSigned;

    /**
     * Bytes per frame, i.e. number of bytes
     * per sample times the number of channels
     */
    private final int m_bytesPerFrame;

    /**
     * JavaSounds audio output line
     */
    private final SourceDataLine m_line;

    /**
     * Signals that the queue is being closed.
     * Never transitions from true to false!
     */
    private volatile boolean m_closing = false;

    /**
     * The last frame written to the line.
     * Used to generate filler data
     */
    private final byte[] m_lineLastFrame;

    /**
     * Number of frames appended to the line
     */
    private AtomicLong m_lineFramesWritten = new AtomicLong(0);

    /**
     * Largest frame time seen so far
     */
    private long m_latestSeenFrameTime = 0;

    /**
     * The frame time corresponding to line time zero
     */
    private long m_frameTimeOffset = 0;

    /**
     * Average packet size in frames.
     * We use this as the number of silence frames
     * to write on a queue underrun
     */
    private final int m_packetSizeFrames;

    /**
     * Requested line gain
     */
    private float m_requestedGain = 0.0f;

    /**
     * AsyncEnqueuer thread
     */
    private final AsyncEnqueuer asyncEnqueuer = new AsyncEnqueuer();
    private final Thread m_queueThread = new Thread(asyncEnqueuer);

    /**
     *  The line's audio format
     */
    private final AudioFormat m_format;

    /**
     * Sample rate
     */
    private final double m_sampleRate;

    public TardyYetReliableAudioOutputQueue(final AudioStreamInformationProvider streamInfoProvider) throws LineUnavailableException {

        final AudioFormat audioFormat = streamInfoProvider.getAudioFormat();

        /* OSX does not support unsigned PCM lines. We thust always request
		 * a signed line, and convert from unsigned to signed if necessary
		 */
        if (AudioFormat.Encoding.PCM_SIGNED.equals(audioFormat.getEncoding())) {
            m_format = audioFormat;
            m_convertUnsignedToSigned = false;
        }
        else if (AudioFormat.Encoding.PCM_UNSIGNED.equals(audioFormat.getEncoding())) {
            m_format = new AudioFormat(
                    audioFormat.getSampleRate(),
                    audioFormat.getSampleSizeInBits(),
                    audioFormat.getChannels(),
                    true,
                    audioFormat.isBigEndian()
            );
            m_convertUnsignedToSigned = true;
        }
        else {
            throw new LineUnavailableException("Audio encoding " + audioFormat.getEncoding() + " is not supported");
        }

		/* Audio format-dependent stuff */
        m_packetSizeFrames = streamInfoProvider.getFramesPerPacket();
        m_bytesPerFrame = m_format.getChannels() * m_format.getSampleSizeInBits() / 8;
        m_sampleRate = m_format.getSampleRate();
        m_lineLastFrame = new byte[m_bytesPerFrame];
        for(int b=0; b < m_lineLastFrame.length; ++b)
            m_lineLastFrame[b] = (b % 2 == 0) ? (byte)-128 : (byte)0;

		/* Compute desired line buffer size and obtain a line */
        final int desiredBufferSize = (int)Math.pow(2, Math.ceil(Math.log(BufferSizeSeconds * m_sampleRate * m_bytesPerFrame) / Math.log(2.0)));
        final DataLine.Info lineInfo = new DataLine.Info(
                SourceDataLine.class,
                m_format,
                desiredBufferSize
        );
        m_line = (SourceDataLine) AudioSystem.getLine(lineInfo);
        m_line.open(m_format, desiredBufferSize);
        s_logger.info("Audio output line created and openend. Requested buffer of " + desiredBufferSize / m_bytesPerFrame  + " frames, got " + m_line.getBufferSize() / m_bytesPerFrame + " frames");

		/* Start enqueuer thread and wait for the line to start.
		 * The wait guarantees that the AudioClock functions return
		 * sensible values right after construction
		 */
        m_queueThread.setDaemon(true);
        m_queueThread.setName("Audio Enqueuer");
        m_queueThread.setPriority(Thread.MAX_PRIORITY);
        m_queueThread.start();

        /* Initialize the seconds time offset now that the line is running. */
        m_secondsTimeOffset = 2208988800.0 +  System.currentTimeMillis() * 1e-3;
    }

    public synchronized boolean enqueue(final long frameTime, final byte[] frames) throws InterruptedException {
        asyncEnqueuer.addToQueue(frameTime, frames);
        return true;
    }

    /**
     * Returns the line's MASTER_GAIN control's value.
     */
    private float getLineGain() {
        if (m_line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
			/* Bound gain value by min and max declared by the control */
            final FloatControl gainControl = (FloatControl)m_line.getControl(FloatControl.Type.MASTER_GAIN);
            return gainControl.getValue();
        }
        else {
            s_logger.severe("Audio output line doesn not support volume control");
            return 0.0f;
        }
    }

    private synchronized void applyGain() {
        setLineGain(m_requestedGain);
    }

    /**
     * Sets the desired output gain.
     *
     * @param gain desired gain
     */
    public synchronized void setGain(final float gain) {
        m_requestedGain = gain;
    }

    /**
     * Stops audio output
     */
    public void close() {
        m_closing = true;
        m_queueThread.interrupt();
    }

    /**
     * Returns the desired output gain.
     *
     */
    public synchronized float getGain() {
        return m_requestedGain;
    }

    /**
     * Removes all currently queued sample data
     */
    public void flush() {
        asyncEnqueuer.internalQueue.clear();
        //s_logger.warning("Overall drop count in the last session: " + dropCount);
//        s_logger.warning("Overall drop count in the last session: " + droppedStreamObjectsList.size());
//        droppedStreamObjectsList.clear();
        System.gc();
    }

    /**
     * Sets the line's MASTER_GAIN control to the provided value,
     * or complains to the log of the line does not support a MASTER_GAIN control
     *
     * @param gain gain to set
     */
    private void setLineGain(final float gain) {
        if (m_line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
			/* Bound gain value by min and max declared by the control */
            final FloatControl gainControl = (FloatControl)m_line.getControl(FloatControl.Type.MASTER_GAIN);
            if (gain < gainControl.getMinimum())
                gainControl.setValue(gainControl.getMinimum());
            else if (gain > gainControl.getMaximum())
                gainControl.setValue(gainControl.getMaximum());
            else
                gainControl.setValue(gain);
        }
        else
            s_logger.severe("Audio output line doesn not support volume control");
    }

    @Override
    public synchronized void setFrameTime(final long frameTime, final double secondsTime) {
        final double ageSeconds = getNowSecondsTime() - secondsTime;
        final long lineTime = Math.round((secondsTime - m_secondsTimeOffset) * m_sampleRate);

        final long frameTimeOffsetPrevious = m_frameTimeOffset;
        m_frameTimeOffset = frameTime - lineTime;

        s_logger.fine("Frame time adjusted by " + (m_frameTimeOffset - frameTimeOffsetPrevious) + " based on timing information " + ageSeconds + " seconds old and " + (m_latestSeenFrameTime - frameTime) + " frames before latest seen frame time");
    }

    @Override
    public double getNowSecondsTime() {
        return m_secondsTimeOffset + getNowLineTime() / m_sampleRate;
    }

    @Override
    public long getNowFrameTime() {
        return m_frameTimeOffset + getNowLineTime();
    }

    @Override
    public double getNextSecondsTime() {
        return m_secondsTimeOffset + getNextLineTime() / m_sampleRate;
    }

    private synchronized long getNextLineTime() {
        return m_lineFramesWritten.get();
    }

    @Override
    public long getNextFrameTime() {
        return m_frameTimeOffset + getNextLineTime();
    }

    @Override
    public double convertFrameToSecondsTime(final long frameTime) {
        return m_secondsTimeOffset + (frameTime - m_frameTimeOffset) / m_sampleRate;
    }

    private long getNowLineTime() {
        return m_line.getLongFramePosition();
    }

    private synchronized long convertFrameToLineTime(final long entryFrameTime) {
        return entryFrameTime - m_frameTimeOffset;
    }

    private class AsyncEnqueuer implements Runnable {

        private final int FRAME_BUFFER = 300;

        private final int DROP_FRAMES_WHEN_OVER_SECONDS = 20; // If 20secs too late, we'll clear queue.

        private CountDownLatch countDownLatch = new CountDownLatch(FRAME_BUFFER);

        private final LinkedSortedQueue<Long, byte[]> internalQueue = new LinkedSortedQueue<Long, byte[]>();

        @Override
        public void run() {
            s_logger.info("Thread started bitch");

            /* Start the line */
            m_line.start();

            while (!m_closing) {

                try {

                    countDownLatch.await(); // Wait for buffer to be full.
                    resetBufferWait(); // Reset buffer wait.


                    while(!internalQueue.isEmpty()) {
                        if (getLineGain() != m_requestedGain) {
                            applyGain();
                        }

                        Entry<Long, byte[]> entry = internalQueue.firstEntryRemove();
                        final long entryLineTime = convertFrameToLineTime(entry.getKey());

                        /* Get sample data and do sanity checks */
                        /* Convert samples if necessary */
                        final byte[] samplesConverted = entry.getValue();
                        final int samplesConvertedLen = samplesConverted.length;
                        if (m_convertUnsignedToSigned) {
                            //final byte[] samplesConverted = Arrays.copyOfRange(nextPlaybackSamples, 0, nextPlaybackSamples.length);
                            /* The line expects signed PCM samples, so we must
                             * convert the unsigned PCM samples to signed.
                             * Note that this only affects the high bytes!
                             */
                            for (int i = 0; i < samplesConvertedLen; i += 2) {
                                samplesConverted[i] = (byte) ((samplesConverted[i] & 0xff) - 0x80);
                            }
                        }

                        final long gapFrames = entryLineTime - getNextLineTime();

                        /* Write samples to line */
                        final int bytesWritten = m_line.write(samplesConverted, 0, samplesConvertedLen);
                        if (bytesWritten != samplesConverted.length) {
                            s_logger.warning("Audio output line accepted only " + bytesWritten + " bytes of sample data while trying to write " + samplesConvertedLen + " bytes");
                        }

                        /* Update state */

                        m_lineFramesWritten.addAndGet(bytesWritten / m_bytesPerFrame);
                        synchronized (m_lineLastFrame) {
                            for (int b = 0; b < m_bytesPerFrame; ++b)
                                m_lineLastFrame[b] = samplesConverted[samplesConvertedLen - (m_bytesPerFrame - b)];

                            s_logger.finest("Audio output line end is now at " + getNextLineTime() + " after writing " + samplesConvertedLen / m_bytesPerFrame + " frames");
                        }

                        final double timingErrorSeconds = gapFrames / m_sampleRate;
                        if(timingErrorSeconds > DROP_FRAMES_WHEN_OVER_SECONDS) {
                            //s_logger.warning("Removed: " + internalQueue.firstKey());
                            internalQueue.clear();
                        }

                    }

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }

        private void resetBufferWait() {
            countDownLatch = new CountDownLatch(FRAME_BUFFER);
        }

        public void addToQueue(long frameTime, byte[] frames) throws InterruptedException {
            internalQueue.put(frameTime, frames);
            countDownLatch.countDown(); // Countdown buffer.
        }
    }


}
