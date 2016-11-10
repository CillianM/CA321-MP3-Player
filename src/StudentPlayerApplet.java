import javax.sound.sampled.*;
import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import java.io.*;

class Player extends Panel implements Runnable
{
    //Applet Variables
    private static final long serialVersionUID = 1L;
    private TextField textfield;
    private TextArea textarea;
    private String filename;

    //Thread Variables
    private Producer producer;
    private Consumer consumer;
    private BoundedBuffer buffer;

    Player(String filename)
    {

        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        textfield = new TextField();
        textarea = new TextArea();
        textarea.setFont(font);
        textfield.setFont(font);
        setLayout(new BorderLayout());
        add(BorderLayout.SOUTH, textfield);
        add(BorderLayout.CENTER, textarea);

        /*
        * Java 8u11 bug requires text fields to be set as something before it's emptied
        * this requires us to use textfield.setText(" ") before textfield.setText("");
        * */

        textfield.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {

                if(e.getActionCommand().equals("x"))
                {
                    textfield.setText(" ");
                    textfield.setText("");
                    producer.stopProducer();
                    consumer.stopConsumer();
                    buffer.stopBuffer();
                    textarea.append("Command received: Halt playback \n");
                }
                if(e.getActionCommand().equals("p"))
                {
                    if(!buffer.paused)
                    {
                        textfield.setText(" ");
                        textfield.setText("");

                        textarea.append("Command received: Pausing playback \n");
                        consumer.pauseConsumer();
                    }

                }
                else if(e.getActionCommand().equals("r"))
                {
                    if(buffer.paused)
                    {
                        textfield.setText(" ");
                        textfield.setText("");

                        textarea.append("Command received: Resuming playback \n");
                        consumer.paused = false;
                        buffer.resumeBuffer();
                    }

                }
                else if(e.getActionCommand().equals("q"))
                {
					if(consumer.supported)
					{
		                textfield.setText(" ");
		                textfield.setText("");

		                //Increment current volume level by 1
		                consumer.newVolume =  consumer.volume.getValue() + 1F;

		                //Ensure we don't go over the max volume
		                if( consumer.newVolume <  consumer.volumeMax)
		                {
		                    consumer.volume.setValue( consumer.newVolume);
		                    textarea.append("Command received: Raised Volume \n");
		                }
		                else
		                    textarea.append("Command received: Max Volume! \n");
					}
                }
                else if(e.getActionCommand().equals("a"))
                {
					if(consumer.supported)
					{
		                textfield.setText(" ");
		                textfield.setText("");

		                //Decrement current volume by one
		                consumer.newVolume =  consumer.volume.getValue() - 1F;

		                //Ensure we don't go under the min volume
		                if(consumer.newVolume > consumer.volumeMin)
		                {
		                    consumer.volume.setValue(consumer.newVolume);
		                    textarea.append("Command received: Lowered Volume \n");
		                }
		                else
		                    textarea.append("Command received: Min Volume! \n");
					}
                }
                else if(e.getActionCommand().equals("m"))
                {
					if(consumer.supported)
					{
						    textfield.setText(" ");
						    textfield.setText("");

						    if(!consumer.muted)
						    {
						        //Save whatever the current volume level is
						        consumer.currentVolume = consumer.volume.getValue();

						        //Set volume to the lowest setting ==> "Muted"
						        consumer.volume.setValue(consumer.volumeMin);
						        textarea.append("Command received: Muted Audio \n");
						        consumer.muted = true;
						    }
						    else
						    {
						        textarea.append("Command received:  Already Muted! \n");
						    }
					}
                }
                else if(e.getActionCommand().equals("u"))
                {
					if(consumer.supported)
					{
		                textfield.setText(" ");
		                textfield.setText("");

		                if(consumer.muted)
		                {
		                    //Set the volume back to the same volume it was when it was muted
		                    consumer.volume.setValue(consumer.currentVolume);
		                    textarea.append("Command received: Unmuted Audio \n");
		                    consumer.muted = false;
		                }
		                else
		                {
		                    textarea.append("Command received: Audio Not Muted! \n");
		                }
					}
                }
            }
        });

        //Start player thread
        this.filename = filename;
        new Thread(this).start();
    }

    public void run()
    {

        try
        {
            File file = new File(filename);
            AudioInputStream s = AudioSystem.getAudioInputStream(file);
            AudioFormat format;
            DataLine.Info info;
            format = s.getFormat();
            textarea.append("Audio format: " + format.toString() + "\n");
            long audioFileLength = file.length();
            int frameSize = format.getFrameSize();
            float frameRate = format.getFrameRate();
            float durationInSeconds = Math.round(audioFileLength / (frameSize * frameRate));
            textarea.append("Audio file Duration: " + durationInSeconds + "\n");

            info = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(info))
            {
                throw new UnsupportedAudioFileException();
            }
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);

            int oneSecond = (int) (format.getChannels() * format.getSampleRate() *
                    format.getSampleSizeInBits() / 8);
            buffer = new BoundedBuffer(oneSecond);

            //Once Audio format, readers and writers setup start the threads
            producer = new Producer(textarea,s,buffer,new byte[oneSecond]);
            consumer = new Consumer(textarea,line,buffer);
            Thread t1 = new Thread(producer);
            Thread t2 = new Thread(consumer);
            t1.start();
            t2.start();

            t1.join();
            t2.join();
            textarea.append("Main says: playback complete \n");
        }
        catch (UnsupportedAudioFileException e )
        {
            System.out.println("Player initialisation failed");
            e.printStackTrace();
            System.exit(1);
        }
        catch (IOException e)
        {
            System.out.println("Player initialisation failed, IOException");
            e.printStackTrace();
            System.exit(1);
        }
        catch (InterruptedException e)
        {
            System.out.println("Thread Interrupted Exception, InterruptedException");
            e.printStackTrace();
            System.exit(1);
        }
        catch (LineUnavailableException e)
        {
            System.out.println("Player initialisation failed, LineUnavailableException");
            e.printStackTrace();
            System.exit(1);
        }
    }

}

class Producer implements Runnable
{
    //local boolean to shut down thread
    private boolean done = false;
    private int numBytesRead;
    private AudioInputStream s;
    private BoundedBuffer buffer;
    private byte [] audioBuffer;
    private TextArea textArea;

    Producer(TextArea textArea, AudioInputStream s,BoundedBuffer buffer, byte [] audioBuffer)
    {
        this.s = s;
        this.textArea = textArea;
        this.buffer = buffer;
        this.audioBuffer = audioBuffer;
    }

    synchronized public void run()
    {
        //Check if it's done reading the file or ended prematurely
        while(!done && numBytesRead != -1)
        {
            try
            {
                //Read data to audio buffer and have the length of it send to numBytesRead
                numBytesRead = s.read(audioBuffer);
                buffer.insertChunk(audioBuffer);
            }
            catch (Exception e)
            {
                System.out.println("Exception occurred");
                e.printStackTrace();
                System.exit(1);
            }

            buffer.isDone();
        }
        textArea.append("Producer says: goodbye \n");
    }

    void stopProducer()
    {
        done = true;
    }

}

class Consumer implements Runnable
{
    private SourceDataLine line;
    private BoundedBuffer buffer;

    //boolean to shut down thread and to pause consumer
    //=> No need to shut down producer as it can fill up the buffer while it's waiting
    private boolean done = false;
    boolean paused = false;
    FloatControl volume;
    private TextArea textArea;
    float volumeMin;
    float volumeMax;
    float newVolume;
    float currentVolume;
    boolean muted = false;
    boolean supported = false;

    Consumer(TextArea textArea,SourceDataLine line,BoundedBuffer buffer)
    {
        this.line = line;
        this.buffer = buffer;
        this.textArea = textArea;
        //set up volume control
		if(line.isControlSupported(FloatControl.Type.MASTER_GAIN))
		{
			supported = true;
			volume = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
			volumeMax = volume.getMaximum();
			volumeMin = volume.getMinimum();
		}
		else
		{
			textArea.append("Audio Controls Not Supported On This Version of java \n");
		}
    }

    synchronized public void run()
    {
        //initialize the line
        line.start();
        byte [] audioData;

        while(!done)
        {
            try
            {
                //remove chunk (1 second) of data from the buffer
                audioData = buffer.removeChunk();
                //if it's a null it's because we've reached the end of the file or ended prematurely
                if(audioData == null)
                    break;

                //pause the consumer on users request
                while(paused)
                {
                    try
                    {
                        wait();
                    }
                    catch (InterruptedException e)
                    {
                        System.out.println("Thread Interrupted Exception");
                        e.printStackTrace();
                        System.exit(1);
                    }
                }

                //write chunk of data to device with length of it (1 second)
                line.write(audioData, 0, audioData.length);
            }
            catch (Exception e)
            {
                System.out.println("Exception occurred");
                e.printStackTrace();
                System.exit(1);
            }
        }

        //Close down line and exit thread
        textArea.append("Consumer says: goodbye \n");
        line.drain();
        line.stop();
        line.close();
    }

    //stop the consumer prematurely
    void stopConsumer()
    {
        done = true;
    }

    //pause the consumer and the buffer
    void pauseConsumer()
    {
        paused = true;
        buffer.pauseBuffer();
    }

}

class BoundedBuffer
{
    private int nextIn = 0;
    private int nextOut = 0;
    private int occupied = 0;
    private int oneSecond;
    private byte [] oneSecondBlock;
    private boolean dataAvailable;
    private byte [] bufferArray;

    boolean paused;

    BoundedBuffer(int oneSecond)
    {
        this.oneSecond = oneSecond;
        //initialize the buffer to 10 x 1 second = 10 seconds
        bufferArray = new byte[10 * oneSecond];
        oneSecondBlock = new byte[oneSecond];
        dataAvailable = true;
    }

    synchronized void insertChunk(byte [] data)
    {
        try
        {
            //if there is no spaces then wait
            while (occupied == 10) wait();

            //rather than byte by byte copy a chunk of data into the buffer array from data
            System.arraycopy(data,0,bufferArray,((nextIn) % (oneSecond * 10)),oneSecondBlock.length);

            //increment the pointed by 1 second worth of bytes
            nextIn+= data.length % (oneSecond * 10);
            //show there is a space taken
            occupied++;
            notifyAll();
        }
        catch (InterruptedException e)
        {
            System.out.println("Thread Interrupted Exception");
            e.printStackTrace();
            System.exit(1);
        }
    }

    synchronized byte[] removeChunk()
    {
        //get a new array of bytes to send out
        byte[] outs = new byte[oneSecond];
        try
        {
            //if there is nothing to take and if we've ended then return a null
            if (occupied == 0 && !dataAvailable) {
                return null;
            }

            //if there is nothing to take or we've paused then we wait
            while (occupied == 0 || paused)
            {
                wait();
            }

            //rather than byte by byte we just copy one chunk
            System.arraycopy( bufferArray, (nextOut  % (oneSecond * 10)), outs, 0, oneSecondBlock.length );

            //ensuring we never go over the full ten seconds and increment a second each time
            nextOut += oneSecondBlock.length % (oneSecond * 10);
            occupied--;
            notifyAll();
            //return the now filled array
            return outs;
        }

        catch (InterruptedException e)
        {
            System.out.println("Thread Interrupted Exception");
            e.printStackTrace();
            System.exit(1);
        }
        return outs;
    }

    //pause the buffer from consuming data from the buffer
    synchronized void pauseBuffer()
    {
        paused = true;
        notifyAll();
    }

    //allow it to continue consuming data from the buffer
    synchronized void resumeBuffer()
    {

        paused = false;
        notifyAll();
    }

    //stop the buffer prematurely
    synchronized void stopBuffer()
    {
        occupied = 0;
        notifyAll();
    }

    //when we've reached the end of the file we can signal the removeblock to stop
    synchronized  void isDone()
    {
        dataAvailable = false;
    }
}

public class StudentPlayerApplet extends Applet
{
    private static final long serialVersionUID = 1L;
    public void init()
    {
        setLayout(new BorderLayout());
        add(BorderLayout.CENTER, new Player(getParameter("file")));
    }
}

